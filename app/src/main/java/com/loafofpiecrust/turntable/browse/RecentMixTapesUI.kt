package com.loafofpiecrust.turntable.browse

import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.playlist.PlaylistDetailsUI
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.ui.universal.UIComponent
import com.loafofpiecrust.turntable.ui.universal.ViewContext
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.days
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.views.RecyclerAdapter
import com.loafofpiecrust.turntable.views.RecyclerItem
import com.loafofpiecrust.turntable.views.RecyclerListItem
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.recyclerview.v7.recyclerView

@Parcelize
class RecentMixTapesUI: UIComponent(), Parcelable {
    override fun ViewContext.render() = recyclerView {
        val mixtapes = produceSingle {
            MixTape.queryMostRecent(10.days)
        }
        
        layoutManager = LinearLayoutManager(context)
        adapter = object : RecyclerAdapter<MixTape, RecyclerItem>(coroutineContext, mixtapes) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
                RecyclerListItem(parent, 3, true)

            override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
                val mt = data[position]
                holder.mainLine.text = mt.id.name
                holder.subLine.text = mt.type.name
                mt.color?.let { holder.card.backgroundColor = it }

                holder.card.setOnClickListener { v ->
                    Library.cachePlaylist(mt)
                    v.context.replaceMainContent(
                        PlaylistDetailsUI(mt.id).createFragment()
                    )
                }
            }
        }
    }
}

class RecentMixTapesFragment: BaseFragment() {
    override fun ViewManager.createView(): View = with(this) {
        val mixtapes = produceSingle {
            MixTape.queryMostRecent(10.days)
        }

        frameLayout {
            recyclerView {
                layoutManager = LinearLayoutManager(context)
                adapter = object : RecyclerAdapter<MixTape, RecyclerItem>(job, mixtapes) {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItem =
                        RecyclerListItem(parent, 3, true)

                    override fun onBindViewHolder(holder: RecyclerItem, position: Int) {
                        val mt = data[position]
                        holder.mainLine.text = mt.id.name
                        holder.subLine.text = mt.type.name
                        mt.color?.let { holder.card.backgroundColor = it }

                        holder.card.setOnClickListener { v ->
                            Library.cachePlaylist(mt)
                            v.context.replaceMainContent(
                                PlaylistDetailsUI(mt.id).createFragment()
                            )
                        }
                    }
                }
            }
        }
    }

}