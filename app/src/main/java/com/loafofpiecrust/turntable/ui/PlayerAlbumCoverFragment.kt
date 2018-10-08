package com.loafofpiecrust.turntable.ui

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
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.util.recyclerViewPager
import com.loafofpiecrust.turntable.screenSize
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView

class PlayerAlbumCoverFragment : BaseFragment() {

    //    private val subs = ArrayList<Disposable>()
//    var slidingPanel: SlidingUpPanelLayout? = null

    override fun ViewManager.createView(): View = recyclerViewPager {
        layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        adapter = Adapter()
        triggerOffset = 0.2f
        isSinglePageFling = true
        isClickable = true
        clipToPadding = false
        clipToOutline = false

        var fromInteraction = true
        addOnPageChangedListener { prev, curr ->
            if (fromInteraction) {
                MusicService.enact(PlayerAction.QueuePosition(curr))
            }
        }

        var prev = 0
        MusicService.instance.switchMap {
            it?.player?.queue
        }.consumeEachAsync { q ->
            (adapter as Adapter).updateData(q.list, q.position)
            fromInteraction = false
            if (Math.abs(q.position - prev) > 8) { // smooth scroll would take too long
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

            val job = Job()
            subs.put(holder, job)?.cancelSafely()
            async(UI + job) {
                song.loadCover(Glide.with(holder.view)).consumeEach {
                    it?.into(holder.image) ?: run {
                        holder.image.imageResource = R.drawable.ic_default_album
                    }
                }
            }
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
            launch(Dispatchers.Default) {
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
                    val padBy = dimen(R.dimen.fullscreen_card_margin)
                    val screenWidth = context.screenSize.width - (padBy * 2)
                    padding = padBy
                    bottomPadding = padBy / 2
                    topPadding = padBy / 2 + dimen(R.dimen.statusbar_height)
                    clipToPadding = false
                    clipToOutline = false
//                        minimumHeight = screenWidth


                    cardView {
                        cardElevation = dimen(R.dimen.medium_elevation).toFloat()
                        frameLayout {
                            imageView {
                                id = R.id.image
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                adjustViewBounds = true
                            }.lparams {
                                width = matchParent
//                                    height = matchParent
                                height = screenWidth
                            }
                        }
                    }.lparams(width = matchParent, height = screenWidth)
                })
            }
        }

    }
}