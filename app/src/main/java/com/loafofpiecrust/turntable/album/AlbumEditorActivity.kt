package com.loafofpiecrust.turntable.album

//import org.jaudiotagger.audio.AudioFileIO
//import org.jaudiotagger.tag.FieldKey
//import org.jaudiotagger.tag.TagOptionSingleton

import activitystarter.Arg
import android.app.ProgressDialog
import android.net.Uri
import android.support.constraint.ConstraintLayout.LayoutParams.PARENT_ID
import android.support.v4.provider.DocumentFile
import android.view.ViewManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.parMap
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.ui.BaseActivity
import com.loafofpiecrust.turntable.util.generateChildrenIds
import com.loafofpiecrust.turntable.util.size
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jetbrains.anko.*
import org.jetbrains.anko.constraint.layout.ConstraintSetBuilder.Side.*
import org.jetbrains.anko.constraint.layout.applyConstraintSet
import org.jetbrains.anko.constraint.layout.constraintLayout
import org.jetbrains.anko.constraint.layout.matchConstraint
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk27.coroutines.onClick
import java.io.File

class AlbumEditorActivity : BaseActivity() {
    @Arg lateinit var albumId: AlbumId

    private lateinit var titleEdit: EditText
    private lateinit var artistEdit: EditText
    private lateinit var yearEdit: EditText

    override fun ViewManager.createView() = constraintLayout {
        val album = runBlocking { LocalApi.find(albumId) } ?: run {
            toast("Album folder not found locally.")
            finish()
            return@constraintLayout
        }

        val artwork = imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
            launch {
                album.loadCover(Glide.with(context)).consumeEach {
                    it?.into(this@imageView) ?: run {
                        imageResource = R.drawable.ic_default_album
                    }
                }
            }
        }

        val title = textInputLayout {
            titleEdit = editText(album.id.name) {
                hintResource = R.string.album_title_hint
                maxLines = 1
                inputType = EditorInfo.TYPE_CLASS_TEXT or
                    EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        val artist = textInputLayout {
            artistEdit = editText(album.id.artist.name) {
                hintResource = R.string.album_artist_hint
                maxLines = 1
                inputType = EditorInfo.TYPE_CLASS_TEXT or
                    EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
            }
        }
        val year = textInputLayout {
            val year = album.year.takeIf { it != -1 }
            yearEdit = editText(year?.toString() ?: "") {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                hintResource = R.string.album_year_hint
                maxLines = 1
            }
        }

        val saveBtn = floatingActionButton {
            imageResource = R.drawable.ic_save
            onClick {
                val dialog = progressDialog("Writing Tags...") {
                    max = album.tracks.size
                }.apply { show() }

                try {
                    withContext(Dispatchers.Default) {
                        saveTags(album, dialog)
                    }
                } catch (e: Exception) {
                    error("Failed to save tags", e)
                    toast("Failed to save tags")
                }

                dialog.dismiss()
                finish()
            }
        }

        generateChildrenIds()
        applyConstraintSet {
            val outerMargin = dimen(R.dimen.text_content_margin)
            artwork {
                connect(
                    TOP to TOP of this@constraintLayout,
                    START to START of this@constraintLayout,
                    END to END of this@constraintLayout
                )
                size = matchConstraint
                dimensionRation = "1:1"
            }
            title {
                connect(
                    TOP to BOTTOM of artwork margin outerMargin / 2,
                    START to START of PARENT_ID margin outerMargin,
                    END to END of PARENT_ID margin outerMargin
                )
                width = matchParent
            }
            artist {
                connect(
                    TOP to BOTTOM of title margin outerMargin / 4,
                    START to START of PARENT_ID margin outerMargin,
                    END to END of PARENT_ID margin outerMargin
                )
                width = matchParent
            }
            year {
                connect(
                    TOP to BOTTOM of artist margin outerMargin / 4,
                    START to START of PARENT_ID margin outerMargin,
                    END to END of PARENT_ID margin outerMargin
                )
                width = matchParent
            }
            saveBtn {
                connect(
                    END to END of this@constraintLayout margin outerMargin,
                    BOTTOM to BOTTOM of this@constraintLayout margin outerMargin
                )
            }
        }
    }

    // TODO: Don't use modal dialog.
    private suspend fun saveTags(album: Album, dialog: ProgressDialog) = run {
        val title = titleEdit.text.toString()
        val artist = artistEdit.text.toString()
        val year = yearEdit.text.toString()
        if (title == album.id.name && artist == album.id.artist.name && (year.toIntOrNull() ?: 0) == album.year) {
            return listOf<Unit>()
        }

        album.tracks.parMap { song ->
            Library.sourceForSong(song.id)?.let { path ->
                tryOr(null) {
                    val internal = File(path)
                    val f = if (internal.canWrite()) {
                        AudioFileIO.read(internal)
                    } else {
                        val parts = path.splitToSequence('/').drop(3)
                        val uri = Uri.parse(UserPrefs.sdCardUri.value).buildUpon()
                        var doc = DocumentFile.fromTreeUri(this, Uri.parse(UserPrefs.sdCardUri.value))
                        parts.forEach {
                            println("tags: doc = ${doc?.uri}")
                            doc = doc?.findFile(it)
                        }
                        println("tags: exists? ${doc?.exists()}, uri = ${doc?.uri}")
                        AudioFileIO.read(this, doc)
                    }
                    val tag = f.tag
                    tag.setField(FieldKey.ALBUM, title)
//                tag.setField(FieldKey.ARTIST, artist)
                    tag.setField(FieldKey.ALBUM_ARTIST, artist)
                    tag.setField(FieldKey.DISC_NO, song.disc.toString())
                    tag.setField(FieldKey.YEAR, year)
                    f.commit(this)
                }
                launch { dialog.incrementProgressBy(1) }
                Unit
            }
        }.awaitAll()
    }
}