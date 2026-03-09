package com.zariapps.quran.sudais.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Transcodes an audio file (MP3) → AAC-LC mono 32 kbps M4A.
 *
 * Target: 32 kbps mono CBR
 *   - 32 kbps mono AAC is fully intelligible for a single voice recitation
 *   - ~3× smaller than a 91 kbps MP3 (mp3quran.net source)
 *   - Results in ~350 MB total vs ~1010 MB without transcoding
 *
 * Safety: caller should verify output < input before replacing original.
 *
 * OOM protection: bounded PCM queue (MAX_PENDING_PCM) with back-pressure so
 *   long surahs like Al-Baqarah (2.5 h) don't exhaust memory.
 */
object AudioTranscoder {

    private const val TARGET_BITRATE  = 32_000   // 32 kbps
    private const val OUTPUT_CHANNELS = 1         // always mono — recitation is a single voice
    private const val MAX_PENDING_PCM = 12        // ~48 KB max buffered at any time

    fun transcodeToAac(inputFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        return try {
            extractor.setDataSource(inputFile.absolutePath)

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

            val inputMime     = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate    = inputFormat.getIntegerSafe(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val inputChannels = inputFormat.getIntegerSafe(MediaFormat.KEY_CHANNEL_COUNT, 1)
            val needsDownmix  = inputChannels > OUTPUT_CHANNELS

            // Decoder: MP3 → raw PCM
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Encoder: PCM → AAC-LC mono 32 kbps CBR
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, OUTPUT_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
                // CBR forces the encoder to honour the requested bitrate instead of
                // using variable quality — crucial for predictable file sizes
                setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val pendingPcm  = ArrayDeque<Pair<ByteArray, Long>>(MAX_PENDING_PCM)
            var inputDone           = false
            var decoderDone         = false
            var eosToEncoder        = false
            var encoderDone         = false
            var muxerTrackIndex     = -1
            var muxerStarted        = false

            while (!encoderDone) {

                // 1. Feed compressed bytes → decoder (back-pressure when queue is full)
                if (!inputDone && pendingPcm.size < MAX_PENDING_PCM) {
                    val idx = decoder.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf  = decoder.getInputBuffer(idx)!!
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

                // 2. Drain PCM from decoder → (downmix if stereo) → queue
                if (!decoderDone && pendingPcm.size < MAX_PENDING_PCM) {
                    val idx = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (idx >= 0) {
                        val buf = decoder.getOutputBuffer(idx)!!
                        if (bufferInfo.size > 0) {
                            val raw = ByteArray(bufferInfo.size)
                            buf.position(bufferInfo.offset)
                            buf.get(raw)
                            val pcm = if (needsDownmix) downmixStereoToMono(raw) else raw
                            pendingPcm.addLast(pcm to bufferInfo.presentationTimeUs)
                        }
                        decoder.releaseOutputBuffer(idx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoderDone = true
                        }
                    }
                }

                // 3. Feed PCM queue → encoder
                if (!eosToEncoder) {
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
                            eosToEncoder = true
                        }
                    }
                }

                // 4. Drain encoder → muxer
                val idx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf      = encoder.getOutputBuffer(idx)!!
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

    /** Downmixes interleaved stereo 16-bit PCM → mono 16-bit PCM by averaging channels. */
    private fun downmixStereoToMono(stereo: ByteArray): ByteArray {
        val mono = ByteArray(stereo.size / 2)
        var si = 0; var mi = 0
        while (si + 3 < stereo.size) {
            val left  = (stereo[si].toInt() and 0xFF) or (stereo[si + 1].toInt() shl 8)
            val right = (stereo[si + 2].toInt() and 0xFF) or (stereo[si + 3].toInt() shl 8)
            val mixed = ((left.toShort().toInt() + right.toShort().toInt()) shr 1).toShort()
            mono[mi]     = (mixed.toInt() and 0xFF).toByte()
            mono[mi + 1] = (mixed.toInt() ushr 8 and 0xFF).toByte()
            si += 4; mi += 2
        }
        return mono
    }

    private fun MediaFormat.getIntegerSafe(key: String, default: Int): Int =
        try { getInteger(key) } catch (_: Exception) { default }
}
