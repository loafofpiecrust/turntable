package com.loafofpiecrust.turntable.ui

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.loafofpiecrust.turntable.R

@GlideModule
class TurntableGlideModule: AppGlideModule() {
    override fun isManifestParsingEnabled() = false
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(
            RequestOptions()
                .error(R.drawable.ic_default_album)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .fitCenter()
        )
    }
}