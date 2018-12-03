package com.loafofpiecrust.turntable.player

//import com.loafofpiecrust.turntable.service.MusicService2
import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.util.recyclerViewPager
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint

class PlayerAlbumCoverFragment : BaseFragment() {

    //    private val subs = ArrayList<Disposable>()
//    var slidingPanel: SlidingUpPanelLayout? = null

    override fun ViewManager.createView(): View = recyclerViewPager {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = Adapter()
        triggerOffset = 0.2f
        isSinglePageFling = true
        isClickable = true

        var fromInteraction = true
        addOnPageChangedListener { prev, curr ->
            if (fromInteraction) {
                MusicService.offer(PlayerAction.QueuePosition(curr))
            }
        }

        var prev = 0
        MusicService.instance.switchMap {
            it?.player?.queue
        }.consumeEachAsync { q ->
            (adapter as Adapter).updateData(q.list, q.position)
            fromInteraction = false
            if (Math.abs(q.position - prev) > 5) { // smooth scroll would take too long
                scrollToPosition(q.position)
            } else {
                smoothScrollToPosition(q.position)
            }
            fromInteraction = true
            prev = q.position
        }
    }

    class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {
        var list: List<Song>? = null
//        var queue: MusicService.Queue? = null
        var position: Int = 0
        private val subs = HashMap<ViewHolder, Job>()
        override fun getItemCount(): Int = list?.size ?: 0

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val q = list ?: return
            val song = q[position]

            subs.put(holder, App.async(Dispatchers.Main) {
                song.loadCover(Glide.with(holder.view)).consumeEach {
                    it?.into(holder.image) ?: run {
                        holder.image.imageResource = R.drawable.ic_default_album
                    }
                }
            })?.cancel()
        }


        fun updateData(newSongs: List<Song>, position: Int) {
            val q = list
//            queue = MusicService.Queue(newSongs, position)
//            notifyDataSetChanged()
            if (q == null || q.isEmpty()) {
                // No need to diff
                list = newSongs
                this.position = position
                notifyItemRangeInserted(0, newSongs.size)
                return
            }

            class Differ(val old: List<Song>, val new: List<Song>): DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = new.size

                override fun areItemsTheSame(oldIdx: Int, newIdx: Int) // compare existence
                    = old[oldIdx] === new[newIdx]

                override fun areContentsTheSame(oldIdx: Int, newIdx: Int) // compare metadata
                    = old[oldIdx] == new[newIdx]
            }

            // Don't halt any UI
            // Run diff in background thread
            GlobalScope.launch(Dispatchers.Default) {
                val diff = DiffUtil.calculateDiff(Differ(q, newSongs))
                launch(Dispatchers.Main) {
                    // go back to UI thread to update the view
                    list = newSongs
                    this@Adapter.position = position
                    diff.dispatchUpdatesTo(this@Adapter)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
            = ViewHolder.create(parent.context)

        class ViewHolder private constructor(val view: View): RecyclerView.ViewHolder(view) {
            val image = view.findViewById<ImageView>(R.id.image)!!
            companion object {
                fun create(context: Context): ViewHolder = ViewHolder(AnkoContext.create(context).frameLayout {
                    padding = dimen(R.dimen.fullscreen_card_margin)
                    clipToPadding = false

                    cardView {
                        cardElevation = dimen(R.dimen.medium_elevation).toFloat()
                        constraintLayout {
                            val img = imageView {
                                id = R.id.image
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }

                            applyConstraintSet {
                                img {
                                    dimensionRation = "H,1:1"
                                    width = matchParent
                                    height = matchConstraint
                                }
                            }
                        }
                    }
                })
            }
        }

    }
}