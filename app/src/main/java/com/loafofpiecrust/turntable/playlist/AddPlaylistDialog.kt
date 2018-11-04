package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.support.transition.Slide
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendation
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.standardStyle
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import com.loafofpiecrust.turntable.util.*
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
import java.util.*
import kotlin.reflect.KClass

class AddPlaylistDialog : BaseDialogFragment(), ColorPickerDialogListener {
    companion object {
        fun withItems(items: List<Recommendation>) = AddPlaylistDialog().apply {
            startingTracks = TrackList(items)
        }
    }

    @Parcelize
    private data class TrackList(val tracks: List<Recommendation> = listOf()): Parcelable
    private var startingTracks by arg { TrackList() }

    private var playlistName: String = ""
    private var playlistType: KClass<*> = CollaborativePlaylist::class
    private val playlistColor = ConflatedBroadcastChannel<Int>()
    private var mixTapeType = MixTape.Type.C60

    override fun onStart() {
        super.onStart()
        if (dialog != null) {
//            setStyle(DialogFragment.STYLE_NO_FRAME, 0)
            dialog.window?.setLayout(matchParent, matchParent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Slide().apply {
            slideEdge = Gravity.BOTTOM
        }.let {
            enterTransition = it
            exitTransition = it
        }
    }

    private fun createAndFinish() {
        val color = playlistColor.valueOrNull
        val pl = when (playlistType) {
            // TODO: Use the actual user uuid string.
            MixTape::class -> MutableMixtape(
                PlaylistId(playlistName),
                Sync.selfUser,
                mixTapeType,
                color
            ).apply {
                addAll(0, startingTracks.tracks.mapNotNull { it as? Song })
            }
            CollaborativePlaylist::class -> CollaborativePlaylist(
                PlaylistId(playlistName),
                Sync.selfUser,
                color
            ).apply {
                startingTracks.tracks.forEach {
                    (it as? Song)?.let { add(it) }
                }
            }
            AlbumCollection::class -> AlbumCollection(
                PlaylistId(playlistName),
                Sync.selfUser,
                color
            ).apply {
                startingTracks.tracks.forEach {
                    (it as? AlbumId)?.let { add(it) }
                }
            }
            else -> kotlin.error("Unreachable")
        }
        context!!.library.addPlaylist(pl)
        dismiss()
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
                setNavigationOnClickListener { dismiss() }
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
                            setColorPickerDialogListener(this@AddPlaylistDialog)
                        }.show(requireActivity().fragmentManager, "color-picker-dialog")
                }
            }

            lateinit var mixTapeSpinner: Spinner
            spinner {
                val choices = listOf(
                    CollaborativePlaylist::class,
                    MixTape::class,
                    AlbumCollection::class
                )
                val names = choices.map { it.localizedName(context) }
                adapter = choiceAdapter(context, names)

                onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>) {
                    }

                    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                        val choice = choices[position]
                        playlistType = choice
                        mixTapeSpinner.visibility = if (choice == MixTape::class) {
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
                adapter = choiceAdapter(context, choices)
                onItemSelectedListener {
                    onItemSelected { adapter, view, pos, id ->
                        mixTapeType = choices[pos]
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
}

fun <T> choiceAdapter(
    context: Context,
    items: List<T>
) = ArrayAdapter<T>(context, R.layout.spinner_item, items).apply {
    setDropDownViewResource(R.layout.spinner_item)
}