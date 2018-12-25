package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.support.v4.app.DialogFragment
import android.text.InputType
import android.transition.Slide
import android.view.ViewManager
import android.widget.ArrayAdapter
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.PlaylistId
import com.loafofpiecrust.turntable.model.playlist.SongPlaylist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.serialize.arg
import com.loafofpiecrust.turntable.serialize.getValue
import com.loafofpiecrust.turntable.serialize.setValue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.ui.popMainContent
import com.loafofpiecrust.turntable.util.localizedName
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.mcxiaoke.koi.ext.onTextChange
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.navigationIconResource
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.appBarLayout
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onItemSelectedListener
import org.jetbrains.anko.support.v4.toast
import kotlin.reflect.KClass

class NewPlaylistDialog : BaseDialogFragment(), ColorPickerDialogListener {
    companion object {
        fun withItems(items: List<Recommendable>) = NewPlaylistDialog().apply {
            startingTracks = TrackList(items)
        }
    }

    @Parcelize
    private data class TrackList(val tracks: List<Recommendable> = listOf()): Parcelable
    private var startingTracks by arg { TrackList() }

    private var playlistName: String = ""
    private var playlistType: KClass<*> = SongPlaylist::class
    private val playlistColor = ConflatedBroadcastChannel<Int>()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(matchParent, matchParent)
    }

    override fun ViewManager.createView() = verticalLayout {
        backgroundColor = context.colorAttr(android.R.attr.windowBackground)

        appBarLayout {
            topPadding = dimen(R.dimen.statusbar_height)
            backgroundColor = UserPrefs.primaryColor.value
            playlistColor.consumeEachAsync {
                backgroundColor = it
            }

            toolbar {
                standardStyle()
                navigationIconResource = R.drawable.ic_close
                setNavigationOnClickListener { context.popMainContent() }
                title = "New Collection"

                menuItem(R.string.fui_button_text_save, R.drawable.ic_save, showIcon = true).onClick {
                    createAndFinish()
                }
            }.lparams(width=matchParent)
        }


        verticalLayout {
            // Things to configure:
            // uuid, color, type
            textInputLayout {
                editText {
                    id = R.id.title
                    hint = "Name"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    onTextChange { text, _, _, _ ->
                        playlistName = text.toString()
                    }
                }
            }

            button("Choose Color") {
                onClick {
                    // Hide the keyboard if it's visible, we're picking a color here!
                    activity?.currentFocus?.let {
                        context.inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                    }
                    ColorPickerDialog.newBuilder()
                        .setColor(playlistColor.valueOrNull ?: Color.BLACK)
                        .setAllowCustom(true)
                        .setAllowPresets(true)
                        .setShowColorShades(true)
                        .create().apply {
                            setColorPickerDialogListener(this@NewPlaylistDialog)
                        }.show(requireActivity().fragmentManager, "color-picker-dialog")
                }
            }


            if (BuildConfig.DEBUG) {
                spinner {
                    val choices = listOf(
                        SongPlaylist::class
                    )
                    val names = choices.map { it.localizedName(context) }
                    adapter = choiceAdapter(context, names)

                    onItemSelectedListener {
                        onItemSelected { _, _, position, id ->
                            val choice = choices[position]
                            playlistType = choice
                        }
                    }
                }
            }
        }.lparams(width=matchParent) {
            margin = dimen(R.dimen.text_content_margin)
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        playlistColor.offer(color)
    }

    private fun createAndFinish() {
        if (playlistName.isBlank()) {
            toast(R.string.playlist_name_required)
            return
        }

        val color = playlistColor.valueOrNull
        val id = PlaylistId(playlistName, Sync.selfUser)
        val pl = when (playlistType) {
            AlbumCollection::class -> AlbumCollection(id, color).also { pl ->
                for (item in startingTracks.tracks) {
                    (item as? AlbumId)?.let { pl.add(it) }
                }
            }
            SongPlaylist::class -> SongPlaylist(id).also { pl ->
                pl.color = color
                for (item in startingTracks.tracks) {
                    (item as? Song)?.let { pl.add(it) }
                }
            }
            else -> error("Unknown playlist type")
        }
        Library.addPlaylist(pl)
        dismiss()
    }
}

fun <T> choiceAdapter(
    context: Context,
    items: List<T>
) = ArrayAdapter<T>(context, R.layout.spinner_item, items).apply {
    setDropDownViewResource(R.layout.spinner_item)
}