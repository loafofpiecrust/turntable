package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.View
import android.view.ViewManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.PartialAlbum
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.song.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseActivity
import com.mcxiaoke.koi.ext.onTextChange
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onItemSelectedListener
import java.util.*

class AddPlaylistActivity : BaseActivity(), ColorPickerDialogListener {

    @Parcelize
    data class TrackList(val tracks: List<SaveableMusic> = listOf()): Parcelable

    @Arg(optional=true) var startingTracks = TrackList()

    private var playlistName: String = ""
    private var playlistType: String = "Playlist"
    private var playlistColor: Int = UserPrefs.accentColor.value
    private var mixTapeType = MixTape.Type.C60

    private lateinit var toolbar: Toolbar

    override fun ViewManager.createView() = linearLayout {
        orientation = LinearLayout.VERTICAL

        toolbar = toolbar {
            standardStyle()
            topPadding = dimen(R.dimen.statusbar_height)
            title = "New Collection"
        }.lparams(width=matchParent)

        verticalLayout {
            // Things to configure:
            // id, color, type
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
                    inputMethodManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
                    ColorPickerDialog.newBuilder()
                        .setColor(playlistColor)
                        .setAllowCustom(true)
                        .setAllowPresets(true)
                        .setShowColorShades(true)
                        .show(this@AddPlaylistActivity)
                }
            }

            lateinit var mixTapeSpinner: Spinner
            spinner {
                val choices = listOf(
                    "Playlist",
                    "Mixtape",
                    "Album Collection"
                )
                adapter = ChoiceAdapter(ctx, choices)

                onItemSelectedListener {
                    onItemSelected { adapter, view, pos, id ->
                        val choice = choices[pos]
                        playlistType = choice
                        mixTapeSpinner.visibility = if (choice == "Mixtape") {
                            // Show different mixtape types.
                            View.VISIBLE
                        } else View.GONE
                    }
                }
            }

            // Mixtape types
            mixTapeSpinner = spinner {
                visibility = View.GONE
                val choices = MixTape.Type.values().toList()
                adapter = ChoiceAdapter(ctx, choices)
                onItemSelectedListener {
                    onItemSelected { adapter, view, pos, id ->
                        mixTapeType = choices[pos]
                    }
                }
            }

            linearLayout {
                button("Cancel").onClick {
                    finish()
                }
                button("Add Playlist") {
                    id = R.id.positive_button
                    onClick {
                        val pl = when (playlistType) {
                            // TODO: Use the actual user id string.
                            "Mixtape" -> MixTape(
                                SyncService.selfUser,
                                mixTapeType,
                                playlistName,
                                playlistColor,
                                UUID.randomUUID()
                            ).apply {
                                startingTracks.tracks.forEach {
                                    (it as? Song)?.let { add(0, it) }
                                }
                            }
                            "Playlist" -> CollaborativePlaylist(
                                SyncService.selfUser,
                                playlistName,
                                playlistColor,
                                UUID.randomUUID()
                            ).apply {
                                startingTracks.tracks.forEach {
                                    (it as? Song)?.let { add(it) }
                                }
                            }
                            "Album Collection" -> AlbumCollection(
                                SyncService.selfUser,
                                playlistName,
                                playlistColor,
                                UUID.randomUUID()
                            ).apply {
                                startingTracks.tracks.forEach {
                                    (it as? PartialAlbum)?.let { add(it) }
                                }
                            }
                            else -> kotlin.error("Unreachable")
                        }
                        context.library.addPlaylist(pl)
                        finish()
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
        playlistColor = color
        toolbar.backgroundColor = color
    }
}

class ChoiceAdapter<T>(ctx: Context, items: List<T>): ArrayAdapter<T>(ctx, R.layout.support_simple_spinner_dropdown_item, items) {
//    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
//        val item = getItem(position)
//        return with(AnkoContext.create(context)) {
//            textView(item.toString()) {}
//        }
//    }
}