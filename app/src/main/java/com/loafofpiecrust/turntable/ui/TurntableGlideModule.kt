package com.loafofpiecrust.turntable.ui

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.loafofpiecrust.turntable.service.Library

@GlideModule
class TurntableGlideModule: AppGlideModule() {
    override fun isManifestParsingEnabled() = false
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(Library.ARTWORK_OPTIONS)
    }
}