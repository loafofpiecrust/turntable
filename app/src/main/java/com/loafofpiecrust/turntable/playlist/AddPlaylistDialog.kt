package com.loafofpiecrust.turntable.playlist

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.support.transition.Slide
import android.text.InputType
import android.view.Gravity
import android.view.ViewManager
import android.widget.ArrayAdapter
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
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
import kotlin.reflect.KClass

class AddPlaylistDialog : BaseDialogFragment(), ColorPickerDialogListener {
    companion object {
        fun withItems(items: List<Recommendable>) = AddPlaylistDialog().apply {
            startingTracks = TrackList(items)
        }
    }

    @Parcelize
    private data class TrackList(val tracks: List<Recommendable> = listOf()): Parcelable
    private var startingTracks by arg { TrackList() }

    private var playlistName: String = ""
    private var playlistType: KClass<*> = CollaborativePlaylist::class
    private val playlistColor = ConflatedBroadcastChannel<Int>()

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
        val id = PlaylistId(playlistName, Sync.selfUser)
        val pl = when (playlistType) {
            AlbumCollection::class -> AlbumCollection(id, color).apply {
                startingTracks.tracks.forEach {
                    (it as? AlbumId)?.let { add(it) }
                }
            }
            GeneralPlaylist::class -> GeneralPlaylist(id).apply {
                startingTracks.tracks.forEach {
                    (it as? Song)?.let { add(it) }
                }
            }
            else -> kotlin.error("Unreachable")
        }
        Library.addPlaylist(pl)
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


            spinner {
                val choices = listOf(
                    AlbumCollection::class,
                    GeneralPlaylist::class
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