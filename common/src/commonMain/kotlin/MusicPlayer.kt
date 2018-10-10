package com.loafofpiecrust.turntable.common

expect class PlatformMusicPlayer {
    fun play()
}

expect fun platformTest()
fun commonTest() {
    println("common")
}