package com.loafofpiecrust.turntable.playlist

import android.app.DialogFragment
import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.View
import android.view.ViewManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.album.PartialAlbum
import com.loafofpiecrust.turntable.model.playlist.AlbumCollection
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.song.*
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.util.arg
import com.mcxiaoke.koi.ext.onTextChange
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.toolbar
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onItemSelectedListener
import java.util.*
import kotlin.reflect.KClass

class AddPlaylistDialog : BaseDialogFragment(), ColorPickerDialogListener {
    companion object {
        fun withItems(items: List<SavableMusic>) = AddPlaylistDialog().apply {
            startingTracks = TrackList(items)
        }
    }

    @Parcelize
    private data class TrackList(val tracks: List<SavableMusic> = listOf()): Parcelable
    private var startingTracks by arg { TrackList() }

    private var playlistName: String = ""
    private var playlistType: KClass<*> = CollaborativePlaylist::class
    private var playlistColor: Int = UserPrefs.accentColor.value
    private var mixTapeType = MixTape.Type.C60

    private lateinit var toolbar: Toolbar

    override fun onStart() {
        super.onStart()
        dialog.window.setLayout(matchParent, matchParent)
        setStyle(DialogFragment.STYLE_NO_FRAME, 0)
    }

    override fun ViewManager.createView() = linearLayout {
        orientation = LinearLayout.VERTICAL

        toolbar = toolbar {
            standardStyle()
            setNavigationOnClickListener { dismiss() }
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
                    activity?.currentFocus?.let {
                        context.inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                    }
                    ColorPickerDialog.newBuilder()
                        .setColor(playlistColor)
                        .setAllowCustom(true)
                        .setAllowPresets(true)
                        .setShowColorShades(true)
                        .create().apply {
                            setColorPickerDialogListener(this@AddPlaylistDialog)
                        }.show(requireActivity().fragmentManager, "color-picker-dialog")
                }
            }

            lateinit var mixTapeSpinner: Spinner
            spinner {
                val choices = listOf(
                    "Playlist" to CollaborativePlaylist::class,
                    "Mixtape" to MixTape::class,
                    "Album Collection" to AlbumCollection::class
                )
                adapter = ChoiceAdapter(context, choices.map { it.first })

                onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>) {
                    }

                    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                        val (_, choice) = choices[position]
                        playlistType = choice
                        mixTapeSpinner.visibility = if (choice === MixTape::class) {
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
                adapter = ChoiceAdapter(context, choices)
                onItemSelectedListener {
                    onItemSelected { adapter, view, pos, id ->
                        mixTapeType = choices[pos]
                    }
                }
            }

            linearLayout {
                button("Cancel").onClick {
                    dismiss()
                }
                button("Add Playlist") {
                    id = R.id.positive_button
                    onClick {
                        val pl = when (playlistType) {
                            // TODO: Use the actual user id string.
                            MixTape::class -> MixTape(
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
                            CollaborativePlaylist::class -> CollaborativePlaylist(
                                SyncService.selfUser,
                                playlistName,
                                playlistColor,
                                UUID.randomUUID()
                            ).apply {
                                startingTracks.tracks.forEach {
                                    (it as? Song)?.let { add(it) }
                                }
                            }
                            AlbumCollection::class -> AlbumCollection(
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
                        dismiss()
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

class ChoiceAdapter<T>(
    ctx: Context,
    items: List<T>
): ArrayAdapter<T>(ctx, android.R.layout.simple_spinner_item, items) {
    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
}