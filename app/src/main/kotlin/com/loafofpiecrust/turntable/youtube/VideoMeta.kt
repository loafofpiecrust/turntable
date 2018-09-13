//package com.loafofpiecrust.turntable.youtube
//
//
//data class VideoMeta(
//    val videoId: String,
//    val name: String?,
//    val author: String?,
//    val channelId: String?,
//    val description: String?,
//    val duration: Long?,
//    val viewCount: Long?,
//    val isLiveStream: Boolean = false
//) {
//    companion object {
//        private val IMAGE_BASE_URL = "http://i.ytimg.com/vi/"
//    }
//
//    // 120 x 90
//    val thumbUrl get() = IMAGE_BASE_URL + videoId + "/default.jpg"
//
//    // 320 x 180
//    val mqImageUrl get() = IMAGE_BASE_URL + videoId + "/mqdefault.jpg"
//
//    // 480 x 360
//    val hqImageUrl get() = IMAGE_BASE_URL + videoId + "/hqdefault.jpg"
//
//    // 640 x 480
//    val sdImageUrl get() = IMAGE_BASE_URL + videoId + "/sddefault.jpg"
//
//    // Max resolution
//    val maxResImageUrl get() = IMAGE_BASE_URL + videoId + "/maxresdefault.jpg"
//}