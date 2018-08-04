package com.loafofpiecrust.turntable.ui

//import com.loafofpiecrust.turntable.youtube.YouTubeExtractor

//import com.google.api.services.drive.DriveScopes
import activitystarter.ActivityStarter
import activitystarter.Arg
import activitystarter.MakeActivityStarter
import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import com.firebase.ui.auth.IdpResponse
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.DetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.util.consumeEach
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.util.task
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.*
import org.jetbrains.anko.design.navigationView
import org.jetbrains.anko.support.v4.drawerLayout

@MakeActivityStarter
class MainActivity : BaseActivity(), MultiplePermissionsListener {

    companion object {
        lateinit var latest: MainActivity private set

        fun replaceContent(
            fragment: Fragment,
            allowBackNav: Boolean = true,
            sharedElems: List<View>? = null
        ) {
            latest.supportFragmentManager.replaceMainContent(
                fragment, allowBackNav, sharedElems
            )
        }
        fun popContent() {
            latest.supportFragmentManager.popBackStack()
        }
    }

    sealed class Action: Parcelable {
        @Parcelize class OpenNowPlaying: Action()
        @Parcelize class SyncRequest(val sender: SyncService.User): Action()
        @Parcelize class FriendRequest(val sender: SyncService.User): Action()
    }

    @Arg(optional=true) var action: Action? = null

    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken) {
        token.continuePermissionRequest()
    }

    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
        Library.with(ctx) {
            it.initData()
        }
    }

    private lateinit var sheets: MultiSheetView

    override fun makeView(ui: ViewManager) = MultiSheetView(ctx) {
        backgroundResource = R.color.background

        lateinit var queueSheet: QueueFragment
        mainContent {
            drawerLayout {
                // main content
                frameLayout {
                    id = R.id.mainContentContainer
                }.lparams(matchParent, matchParent)

                // drawer!
                navigationView {
                    fitsSystemWindows = true

                    setNavigationItemSelectedListener {
                        it.isChecked = true
                        this@drawerLayout.closeDrawers()
                        true
                    }
                    menu.group(1, true, true) {
                        menuItem("Library").onClick {
                            while (supportFragmentManager.popBackStackImmediate()) {}
                        }
                        menuItem("Settings").onClick {
                            SettingsActivityStarter.start(ctx)
                        }
                    }
                }.lparams(width = dimen(R.dimen.material_drawer_width), height = matchParent) {
                    gravity = Gravity.START
                }
            }
        }
        firstSheet {
            backgroundResource = R.color.background
            fragment(supportFragmentManager, NowPlayingFragment())
        }
        firstSheetPeek {
            backgroundResource = R.color.background
            fragment(supportFragmentManager, MiniPlayerFragment())
        }
        secondSheet {
            horizontalPadding = dip(16)
            bottomPadding = dip(16)
            clipToPadding = false
            queueSheet = fragment(supportFragmentManager, QueueFragment())
        }

        onSheetStateChanged { sheet, state ->
            if (sheet == MultiSheetView.Sheet.SECOND) {
                queueSheet.onSheetStateChanged(state)
            }
        }
    }.also { sheets = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latest = this

        // Check for updates to the app
        // Annoying, but since we host on GH, convenient.
        AppUpdater(this)
            .setUpdateFrom(UpdateFrom.GITHUB)
            .setGitHubUserAndRepo("loafofpiecrust", "turntable")
            .start()

//        window.statusBarColor = UserPrefs.primaryColor.value.darken(0.2f)
        window.statusBarColor = Color.TRANSPARENT

        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_CONTACTS
            )
            .withListener(this)
            .check()

        // TODO: Separate requests for reading and writing, maybe?

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.mainContentContainer, LibraryFragmentStarter.newInstance())
                .commit()
        }

        // Start out with collapsed sheets
        sheets.hide(true, false)
        MusicService.instance.switchMap {
            it?.player?.queue ?: produce { send(null) }
        }.consumeEach(UI) { q ->
            if (q?.current == null) {
                if (!sheets.isHidden) {
                    sheets.hide(true, true)
                }
            } else if (sheets.isHidden) {
                sheets.unhide(true)
            }
        }


//        drawer {
//            closeOnClick = true
//
//            primaryItem("Library") {
//                selectable = false
//                onClick { _ ->
////                    supportFragmentManager.replaceMainContent(
////                        LibraryFragmentStarter.newInstance(),
////                        false
////                    )
//                    false
//                }
//            }
//            primaryItem("Settings") {
//                selectable = false
//                onClick { _ ->
//                    SettingsActivityStarter.start(ctx)
//                    false
//                }
//            }
//            primaryItem("User Sync") {
//                selectable = false
//                onClick { _ ->
//                    alert("Who to sync with?") {
//                        var textBox: EditText? = null
//                        customView {
//                            textBox = editText {
//                                hint = "User id to sync with"
//                            }
//                        }
//                        positiveButton("Sync") {
//                            val id = textBox!!.text.toString()
//                            task {
//                                SyncService.User.resolve(id)
//                            }.success(UI) { user ->
//                                if (user != null) {
//                                    SyncService.instance.requestSync(user)
//                                } else {
//                                    println("sync: user $id not found")
//                                    toast("User '$id' not found.")
//                                }
//                            }
//                        }
//                        negativeButton("Cancel") {}
//                    }.show()
//                    false
//                }
//            }
//
//            primaryItem("SD Card Permissions") {
//                selectable = false
//                onClick { _ ->
//                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//                    startActivityForResult(intent, 42)
//                    false
//                }
//            }
//        }


        // TODO: Check if already logged in or something so we don't get the dark flash every startup

//        val lastAcc = GoogleSignIn.getLastSignedInAccount(ctx)
//        val lastAcc = FirebaseAuth.getInstance().currentUser
//        if (lastAcc != null) {
//            SyncService.login(lastAcc)
//        } else {
////            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
////                .requestIdToken("460820546584-r03c9k3p83jse5e627us8r90ra51kqbh.apps.googleusercontent.com")
////                .requestEmail()
////                .requestId()
////                .requestProfile()
////                .build()
////
////            val client = GoogleSignIn.getClient(ctx, gso)
////            startActivityForResult(client.signInIntent, 69)
//            val providers = listOf(
//                AuthUI.IdpConfig.GoogleBuilder().build()
//            )
//
//            // Create and launch sign-in intent
//            startActivityForResult(
//                AuthUI.getInstance()
//                    .createSignInIntentBuilder()
//                    .setAvailableProviders(providers)
//                    .build(),
//                69
//            )
//        }
    }

    lateinit var gclient: GoogleApiClient

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ActivityStarter.fill(this)

        val action = action
        when (action) {
            // TODO: Impl with BottomSheet
//            is Action.OpenNowPlaying -> slider?.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            is Action.FriendRequest -> {
                val sender = action.sender
                alert("${sender.name} wants to be friends") {
                    positiveButton("Befriend") {
                        SyncService.send(
                            SyncService.Message.FriendResponse(true),
                            SyncService.Mode.OneOnOne(sender)
                        )
                        UserPrefs.friends appends SyncService.Friend(sender, SyncService.Friend.Status.CONFIRMED)
                    }
                    negativeButton("Decline") {
                        SyncService.send(
                            SyncService.Message.FriendResponse(false),
                            SyncService.Mode.OneOnOne(sender)
                        )
                    }
                }.show()
            }
            is Action.SyncRequest -> {
                val sender = action.sender
                alert("Sync request from ${sender.name}") {
                    positiveButton("Sync") {
                        // set sync mode to One on One, enable sync
                        // change some UI element to indicate sync mode (in Now Playing?)
                        // TODO: Send display id or have that somewhere.
                        SyncService.confirmSync(sender)
                        toast("Now synced with ${sender.name}")
                    }
                    negativeButton("Decline") {
                        SyncService.send(
                            SyncService.Message.SyncResponse(false),
                            SyncService.Mode.OneOnOne(sender)
                        )
                    }
                }.show()
            }
            else -> {
            }
        }
        if (action == null) {
            if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                val searchType = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
                val query = intent.getStringExtra(SearchManager.QUERY)
                val artist = if (intent.hasExtra(MediaStore.EXTRA_MEDIA_ARTIST)) {
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                } else null
                val album = if (intent.hasExtra(MediaStore.EXTRA_MEDIA_ALBUM)) {
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                } else null
                val title = if (intent.hasExtra(MediaStore.EXTRA_MEDIA_TITLE)) {
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                } else null
//                if (title != null && album != null && artist != null) {
                    task {
                        val song = Spotify.searchSongs(query).firstOrNull()
                        if (song != null) {
                            MusicService.enact(SyncService.Message.PlaySongs(listOf(song)))
                        }
                    }
//                }
            } else if (intent.data != null) {
                handleLink(intent.data)
            }
        }
        this.action = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode != RESULT_OK) return

        if (requestCode == 69 && data != null) {
            val result = IdpResponse.fromResultIntent(data)
            if (resultCode == RESULT_OK) {
                SyncService.login(FirebaseAuth.getInstance().currentUser!!)
            } else {
                // Login failed!
            }
        } else if (requestCode == 42) {
            val uri = data!!.data

            val permissions = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            grantUriPermission(packageName, uri, permissions)
            contentResolver.takePersistableUriPermission(uri, permissions)
            UserPrefs.sdCardUri puts uri.toString()
        }
    }

    override fun onBackPressed() {
        if (!sheets.consumeBackPress()) {
            super.onBackPressed()
        }
    }

    fun collapseDrawers() {
        if (sheets.consumeBackPress()) {
            sheets.consumeBackPress()
        }
    }

//    var queueSlider: SlidingUpPanelLayout? = null

//    override fun onBackPressed() {
//        if (queueSlider != null && queueSlider?.panelState != SlidingUpPanelLayout.PanelState.COLLAPSED) {
//            queueSlider?.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
//        } else if (slider != null && slider?.panelState != SlidingUpPanelLayout.PanelState.COLLAPSED) {
//            slider?.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
//        } else {
//            super.onBackPressed()
//        }
//    }

    private fun handleLink(url: Uri) = task {
        // Possible urls (recommendations, sync)
        // turntable://album?name=*&artist=*
        // turntable://artist?id=*
        // turntable://sync-request?from=[USERID]
//        val segs = url.pathSegments
//        val params = url.queryParameterNames
        when (url.host) {
            "album" -> {
                val title = url.getQueryParameter("name")
                val artist = url.getQueryParameter("artist")
                ctx.replaceMainContent(
                    DetailsFragmentStarter.newInstance(Album.justForSearch(ArtistId(artist).forAlbum(title))),
                    true
                )
            }
            "artist" -> {
                val name = url.getQueryParameter("id")
                ctx.replaceMainContent(
                    ArtistDetailsFragmentStarter.newInstance(Artist.justForSearch(name)),
                    true
                )
            }
            "sync-request" -> {
                val id = url.getQueryParameter("from")
//                val displayName = url.getQueryParameter("id")
                given(SyncService.User.resolve(id)) {
                    SyncService.requestSync(it)
                }
            }
            "lets-be-friends" -> {
                val id = url.getQueryParameter("id")
//                val id = url.getQueryParameter("id")

                val other = given(SyncService.User.resolve(id)) {
                    SyncService.Friend(it, SyncService.Friend.Status.CONFIRMED)
                }
                if (other != null && !UserPrefs.friends.value.contains(other)) {
                    other.respondToRequest(true)
                }
            }
        }

        null
    }


    override fun onPause() {
        super.onPause()
        // Save preference files here!
        runBlocking { UserPrefs.saveFiles() }
    }
}

fun FragmentManager.replaceMainContent(fragment: Fragment, allowBackNav: Boolean, sharedElems: List<View>? = null) {
    val trans = beginTransaction()
//    trans.setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
//        fragment.sharedElementEnterTransition = ChangeBounds()
    doFromSdk(21) {
        sharedElems?.forEach {
            trans.addSharedElement(it, it.transitionName)
        }
    }
    trans.replace(R.id.mainContentContainer, fragment)
    if (allowBackNav) {
        trans.addToBackStack(null)
    }
    trans.commit()
    MainActivity.latest.collapseDrawers()
}

fun Context.replaceMainContent(fragment: Fragment, allowBackNav: Boolean = true, sharedElems: List<View>? = null) {
    MainActivity.latest.supportFragmentManager.replaceMainContent(
        fragment, allowBackNav, sharedElems
    )
}
fun Context.popMainContent() {
    MainActivity.latest.supportFragmentManager.popBackStack()
}