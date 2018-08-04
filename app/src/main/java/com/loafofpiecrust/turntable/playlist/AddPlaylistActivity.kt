package com.loafofpiecrust.turntable.playlist

import activitystarter.Arg
import android.content.Context
import android.os.Parcelable
import android.text.InputType
import android.view.View
import android.view.ViewManager
import android.widget.ArrayAdapter
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.BaseActivity
import com.loafofpiecrust.turntable.ui.turntableToolbar
import com.mcxiaoke.koi.ext.onTextChange
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.*
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onItemSelectedListener
import java.util.*

//@MakeActivityStarter
class AddPlaylistActivity : BaseActivity(), ColorPickerDialogListener {

    @Parcelize
    data class TrackList(val tracks: List<Music> = listOf()): Parcelable

    @Arg(optional=true)
    var startingTracks = TrackList()

    private var playlistName: String = ""
    private var playlistType: String = "Playlist"
    private var playlistColor: Int = UserPrefs.accentColor.value

    override fun makeView(ui: ViewManager): View = ui.verticalLayout {
        fitsSystemWindows = true

        turntableToolbar(this@AddPlaylistActivity) {
            title = "New Collection"
        }.lparams(width=matchParent, height=dimen(R.dimen.toolbar_height))

        verticalLayout {
            // Things to configure:
            // id, color, type
            textInputLayout {
                editText {
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
                    }
                }

            }

            linearLayout {
                button("Cancel").onClick {
                    finish()
                }
                button("Add Playlist").onClick {
                    val pl = when (playlistType) {
                    // TODO: Use the actual user id string.
                        "Mixtape" -> MixTape(
                            SyncService.selfUser.username,
                            MixTape.Type.C60,
                            playlistName,
                            playlistColor,
                            UUID.randomUUID()
                        ).apply {
                            startingTracks.tracks.forEach {
                                given(it as? Song) { add(0, it) }
                            }
                        }
                        "Playlist" -> CollaborativePlaylist(
                            SyncService.selfUser.username,
                            playlistName,
                            playlistColor,
                            UUID.randomUUID()
                        ).apply {
                            startingTracks.tracks.forEach {
                                given(it as? Song) { add(it) }
                            }
                        }
                        "Album Collection" -> AlbumCollection(
                            SyncService.selfUser.username,
                            playlistName,
                            playlistColor,
                            UUID.randomUUID()
                        ).apply {
                            startingTracks.tracks.forEach {
                                given(it as? Album) { add(it) }
                            }
                        }
                        else -> kotlin.error("Unreachable")
                    }
                    ctx.library.addPlaylist(pl)
                    finish()
                }
            }
        }.lparams(width=matchParent) {
            margin = dip(16)
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        playlistColor = color
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