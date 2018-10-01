package com.loafofpiecrust.turntable.youtube

sealed class YtFormat(
    /**
     * An identifier used by youtube for different formats.
     */
    open val itag: Int,
    /**
     * The file extension and conainer format like "mp4"
     */
    open val ext: String,
    open val isDash: Boolean = false,
    open val isHls: Boolean = false
) {
    enum class VCodec {
        H263, H264, MPEG4, VP8, VP9, NONE
    }

    enum class ACodec {
        MP3, AAC, VORBIS, OPUS, NONE
    }

    data class Audio(
        override val itag: Int,
        override val ext: String,
        val codec: ACodec,
        /// Audio bitrate in kbit/s
        val bitrate: Int,
        override val isDash: Boolean = true,
        override val isHls: Boolean = false
    ): YtFormat(itag, ext, isDash, isHls)

    data class Video(
        override val itag: Int,
        override val ext: String,
        val codec: VCodec,
        val height: Int,
        override val isDash: Boolean = true,
        override val isHls: Boolean = false,
        val fps: Int = 30
    ): YtFormat(itag, ext, isDash, isHls)

    data class AV(
        override val itag: Int,
        override val ext: String,
        val videoCodec: VCodec,
        /// Pixel height of the video stream
        val height: Int,
        val audioCodec: ACodec,
        val audioBitrate: Int,
        override val isDash: Boolean = false,
        override val isHls: Boolean = false
    ): YtFormat(itag, ext, isDash, isHls)
}