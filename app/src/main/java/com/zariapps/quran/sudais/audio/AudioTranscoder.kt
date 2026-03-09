package com.zariapps.quran.sudais.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Transcodes an audio file (e.g. MP3) to AAC in an M4A container.
 *
 * Why AAC over MP3 at the same bitrate:
 *   - AAC is ~30% more efficient than MP3
 *   - 64 kbps AAC ≈ 128 kbps MP3 in perceived quality for speech/recitation
 *   - Native Android support — no third-party library needed
 */
object AudioTranscoder {

    private const val TARGET_BITRATE = 64_000 // 64 kbps

    /**
     * Transcodes [inputFile] → [outputFile] (.m4a).
     * Runs synchronously — call from a background coroutine (Dispatchers.IO).
     * Returns true on success; on failure the output file is deleted.
     */
    fun transcodeToAac(inputFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        return try {
            extractor.setDataSource(inputFile.absolutePath)

            // Locate the audio track
            var inputFormat: MediaFormat? = null
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    inputFormat = fmt
                    trackIndex = i
                    break
                }
            }
            if (inputFormat == null || trackIndex == -1) return false
            extractor.selectTrack(trackIndex)

            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inputFormat.getIntegerSafe(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channelCount = inputFormat.getIntegerSafe(MediaFormat.KEY_CHANNEL_COUNT, 1)

            // Decoder: source format → raw PCM
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Encoder: raw PCM → AAC-LC
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Muxer: wraps AAC into .m4a container
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // State
            val bufferInfo = MediaCodec.BufferInfo()
            val pendingPcm = ArrayDeque<Pair<ByteArray, Long>>() // (pcmBytes, presentationTimeUs)
            var inputDone = false
            var decoderDone = false
            var eosSignaledToEncoder = false
            var encoderDone = false
            var muxerTrackIndex = -1
            var muxerStarted = false

            while (!encoderDone) {
                // ── Step 1: Feed compressed data into decoder ───────────────────────────
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // ── Step 2: Drain PCM from decoder into queue ───────────────────────────
                if (!decoderDone) {
                    val idx = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (idx >= 0) {
                        val buf = decoder.getOutputBuffer(idx)!!
                        if (bufferInfo.size > 0) {
                            val data = ByteArray(bufferInfo.size)
                            buf.position(bufferInfo.offset)
                            buf.get(data)
                            pendingPcm.addLast(data to bufferInfo.presentationTimeUs)
                        }
                        decoder.releaseOutputBuffer(idx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoderDone = true
                        }
                    }
                }

                // ── Step 3: Feed PCM queue into encoder ─────────────────────────────────
                if (!eosSignaledToEncoder) {
                    if (pendingPcm.isNotEmpty()) {
                        val idx = encoder.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val (data, pts) = pendingPcm.removeFirst()
                            val buf = encoder.getInputBuffer(idx)!!
                            buf.clear()
                            buf.put(data)
                            encoder.queueInputBuffer(idx, 0, data.size, pts, 0)
                        }
                    } else if (decoderDone) {
                        val idx = encoder.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            encoder.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSignaledToEncoder = true
                        }
                    }
                }

                // ── Step 4: Drain encoder output into muxer ─────────────────────────────
                val idx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = encoder.getOutputBuffer(idx)!!
                        val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(muxerTrackIndex, buf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(idx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        } finally {
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntegerSafe(key: String, default: Int): Int =
        try { getInteger(key) } catch (_: Exception) { default }
}
