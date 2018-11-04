package com.loafofpiecrust.turntable.model.song

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize


@Parcelize
data class LocalSongId(
    val id: Long,
    val albumId: Long,
    val artistId: Long
): Song.PlatformId, Parcelable

@Parcelize
data class RemoteSongId(
    val id: String?,
    val albumId: String?,
    val artistId: String?
): Song.PlatformId, Parcelable