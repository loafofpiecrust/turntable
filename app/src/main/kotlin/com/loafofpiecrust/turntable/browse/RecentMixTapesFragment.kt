package com.loafofpiecrust.turntable.browse

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.playlist.PlaylistDetailsFragmentStarter
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.produceTask
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import java.util.concurrent.TimeUnit


class RecentMixTapesFragment: BaseFragment() {
    override fun ViewManager.createView(): View = with(this) {
        val mixtapes = produceTask {
            MixTape.queryMostRecent(TimeUnit.DAYS.toMillis(10))
        }

        frameLayout {
            recyclerView {
                layoutManager = LinearLayoutManager(context)
                adapter = object : RecyclerAdapter<MixTape, RecyclerItem>() {
                    init {
                        subscribeData(mixtapes)
                    }

                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
                        RecyclerListItem(parent, 3, true)

                    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
                        val mt = data[position]
                        holder.mainLine.text = mt.name
                        holder.subLine.text = mt.type.name
                        given(mt.color) { holder.card.backgroundColor = it }

                        holder.card.setOnClickListener { v ->
                            v.context.library.cachePlaylist(mt)
                            val frag = PlaylistDetailsFragmentStarter.newInstance(mt.id, mt.name)
                            v.context.replaceMainContent(frag, true)
                        }
                    }
                }
            }
        }
    }

}