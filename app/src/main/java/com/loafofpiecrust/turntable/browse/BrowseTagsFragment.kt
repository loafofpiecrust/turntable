package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.github.salomonbrys.kotson.*
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.ui.RecyclerListItem
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.gson
import com.loafofpiecrust.turntable.util.produceSingle
import kotlinx.coroutines.runBlocking
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.verticalLayout

class BrowseTagsFragment: BaseFragment() {
    companion object {
        const val LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0"
    }

    data class Tag(
        val name: String,
        val usageCount: Int,
        val reach: Int
    )

    private val popularTags: List<Tag> = runBlocking {
        val res = Http.get(LASTFM_API_URL, params=mapOf(
            "api_key" to BuildConfig.LASTFM_API_KEY, "format" to "json",
            "method" to "chart.getTopTags"
        )).gson["toptags"]
        val tagResults = res["tag"].array
        tagResults.map { it.obj }.map {
            Tag(
                it["id"].string,
                usageCount = it["count"].int,
                reach = it["reach"].int
            )
        }
    }

    override fun ViewManager.createView(): View = verticalLayout {
        recyclerView {
            layoutManager = LinearLayoutManager(context)
            adapter = TagAdapter(popularTags)
        }
    }


    inner class TagAdapter(
        initialData: List<Tag>
    ): RecyclerAdapter<Tag, RecyclerListItem>(job, produceSingle(initialData)) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            RecyclerListItem(parent, 2)

        override fun onBindViewHolder(holder: RecyclerListItem, position: Int) {
            val tag = data[position]
            holder.mainLine.text = tag.name
        }

    }
}
