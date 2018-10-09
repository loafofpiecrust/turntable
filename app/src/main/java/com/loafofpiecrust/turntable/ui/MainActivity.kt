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
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.DrawerLayout
import android.view.Gravity
import android.view.View
import android.view.ViewManager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.AlbumDetails
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragment
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.sync.User
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.consumeEach
import org.jetbrains.anko.*
import org.jetbrains.anko.design.navigationView
import org.jetbrains.anko.support.v4.drawerLayout

@MakeActivityStarter
class MainActivity : BaseActivity(), MultiplePermissionsListener {
    sealed class Action: Parcelable {
        @Parcelize class OpenNowPlaying: Action()
        @Parcelize data class SyncRequest(val sender: User): Action()
        @Parcelize data class FriendRequest(val sender: User): Action()
    }

    @Arg(optional=true) var action: Action? = null

    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken) {
        token.continuePermissionRequest()
    }

    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
        Library.instance.initData()
    }

    private lateinit var sheets: MultiSheetView
    private var drawers: DrawerLayout? = null

    fun toggleDrawer() {
        drawers?.let { drawers ->
            if (drawers.isDrawerOpen(Gravity.START)) {
                drawers.closeDrawer(Gravity.START)
            } else {
                drawers.openDrawer(Gravity.START, true)
            }
        }
    }

    override fun ViewManager.createView() = MultiSheetView(this@MainActivity) {
        backgroundResource = R.color.background

        mainContent {
            drawers = drawerLayout {
                // main content
                frameLayout {
                    id = R.id.mainContentContainer
                    fragment { LibraryFragment() }
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
                        menuItem(R.string.title_activity_library) {
                            isChecked = true
                            onClick {
                                this@drawerLayout.closeDrawers()
                                supportFragmentManager.popAllBackStack()
                            }
                        }
                        menuItem(R.string.action_settings).onClick {
                            this@drawerLayout.closeDrawers()
                            SettingsActivityStarter.start(context)
                        }
                    }
                }.lparams(height = matchParent) {
                    gravity = Gravity.START
                }
            }
            drawers!!
        }
        firstSheet {
            id = R.id.nowPlayingPanel
            backgroundResource = R.color.background
            fragment { NowPlayingFragment() }
        }
        firstSheetPeek {
            id = R.id.miniPlayer
            backgroundResource = R.color.background
            fragment { MiniPlayerFragment() }
        }

        var queueSheet: QueueFragment? = null
        secondSheet {
            id = R.id.queueSlider
            horizontalPadding = dimen(R.dimen.activity_horizontal_margin)
            bottomPadding = dimen(R.dimen.activity_vertical_margin)
            clipToPadding = false
            fragment { QueueFragment().also { queueSheet = it } }
        }

        onSheetStateChanged { sheet, state ->
            if (sheet == MultiSheetView.Sheet.SECOND) {
                queueSheet?.onSheetStateChanged(state)
            }
        }
    }.also {
        it.hide(true, false)
        sheets = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for updates to the app
        // Annoying, but since we host on GH, convenient.
//        AppUpdater(this)
//            .setUpdateFrom(UpdateFrom.GITHUB)
//            .setGitHubUserAndRepo("loafofpiecrust", "turntable")
//            .start()

        window.statusBarColor = Color.TRANSPARENT

        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_CONTACTS
            )
            .withListener(this)
            .check()

        // TODO: Separate requests for reading and writing, maybe?

//        if (savedInstanceState == null) {
//            supportFragmentManager.beginTransaction()
//                .replace(R.uuid.mainContentContainer, LibraryFragment())
//                .commit()
//        }

        // Start out with collapsed sheets
//        sheets.hide(true, false)
        launch {
            MusicService.instance.switchMap {
                it?.player?.queue
            }.consumeEach { q ->
                if (q.current == null) {
                    if (!sheets.isHidden) {
                        sheets.hide(true, true)
                    }
                } else if (sheets.isHidden) {
                    sheets.unhide(true)
                }
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
//                                hint = "User uuid to sync with"
//                            }
//                        }
//                        positiveButton("Sync") {
//                            val uuid = textBox!!.text.toString()
//                            task {
//                                SyncService.User.resolve(uuid)
//                            }.success(UI) { user ->
//                                if (user != null) {
//                                    SyncService.instance.requestSync(user)
//                                } else {
//                                    println("sync: user $uuid not found")
//                                    toast("User '$uuid' not found.")
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
        try {
            val lastAcc = FirebaseAuth.getInstance().currentUser
            if (lastAcc != null) {
                SyncService.login(lastAcc)
            } else {
                val providers = listOf(
                    AuthUI.IdpConfig.GoogleBuilder().build()
                )

                // Create and launch sign-in intent
                startActivityForResult(
                    AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                    69
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    lateinit var gclient: GoogleApiClient

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ActivityStarter.fill(this, intent.extras)

        val action = action
        when (action) {
            // TODO: Impl with BottomSheet
//            is Action.OpenNowPlaying -> slider?.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            is Action.FriendRequest -> {
                val sender = action.sender
                alert("${sender.name} wants to be friends") {
                    positiveButton(R.string.user_befriend) {
                        SyncService.send(
                            Message.FriendResponse(true),
                            SyncService.Mode.OneOnOne(sender)
                        )
                        UserPrefs.friends putsMapped { it + SyncService.Friend(sender, SyncService.Friend.Status.CONFIRMED) }
                    }
                    negativeButton(R.string.request_decline) {
                        SyncService.send(
                            Message.FriendResponse(false),
                            SyncService.Mode.OneOnOne(sender)
                        )
                    }
                }.show()
            }
            is Action.SyncRequest -> {
                val sender = action.sender
                alert("Sync request from ${sender.name}") {
                    positiveButton(R.string.user_sync_accept) {
                        // set sync mode to One on One, enable sync
                        // change some UI element to indicate sync mode (in Now Playing?)
                        // TODO: Send display uuid or have that somewhere.
                        SyncService.confirmSync(sender)
                        toast("Now synced with ${sender.name}")
                    }
                    negativeButton(R.string.request_decline) {
                        SyncService.send(
                            Message.SyncResponse(false),
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
                    GlobalScope.launch {
                        val song = Spotify.searchSongs(query).firstOrNull()
                        if (song != null) {
                            MusicService.enact(PlayerAction.PlaySongs(listOf(song)))
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
                result?.error?.printStackTrace()
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
            val drawers = drawers
            if (drawers != null && drawers.isDrawerOpen(Gravity.START)) {
                drawers.closeDrawers()
            } else {
                super.onBackPressed()
            }
        }
    }

    fun collapseDrawers() {
        drawers?.closeDrawers()
        if (sheets.consumeBackPress()) {
            sheets.consumeBackPress()
        }
    }

    private fun handleLink(url: Uri) = launch(Dispatchers.Default) {
        // Possible urls (recommendations, sync)
        // turntable://album?name=*&artist=*
        // turntable://artist?uuid=*
        // turntable://sync-request?from=[USERID]
//        val segs = url.pathSegments
//        val params = url.queryParameterNames
        when (url.host) {
            "album" -> {
                val title = url.getQueryParameter("name")
                val artist = url.getQueryParameter("artist")
                replaceMainContent(
                    AlbumDetails(
                        AlbumId(title, ArtistId(artist))
                    ).createFragment()
                )
            }
            "artist" -> {
                val name = url.getQueryParameter("uuid")
                given(ArtistDetailsFragment.fromId(ArtistId(name))) {
                    replaceMainContent(it, true)
                }
            }
            "sync-request" -> {
                val id = url.getQueryParameter("from")
//                val displayName = url.getQueryParameter("uuid")
                given(User.resolve(id)) {
                    SyncService.requestSync(it)
                }
            }
            "lets-be-friends" -> {
                val id = url.getQueryParameter("uuid")
//                val uuid = url.getQueryParameter("uuid")

                val other = given(User.resolve(id)) {
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
        runBlocking(Dispatchers.IO) { UserPrefs.saveFiles() }
    }
}

fun FragmentManager.replaceMainContent(fragment: Fragment, allowBackNav: Boolean, sharedElems: List<View>? = null) {
    beginTransaction().apply {
//        currentFragment?.exitTransition = Fade()
        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        sharedElems?.forEach {
            addSharedElement(it, it.transitionName)
        }
        replace(R.id.mainContentContainer, fragment)
        if (allowBackNav) {
            addToBackStack(backStackEntryCount.toString())
        }
        commit()
    }
//    executePendingTransactions()
}

fun Context.replaceMainContent(fragment: Fragment, allowBackNav: Boolean = true, sharedElems: List<View>? = null) {
    if (this is BaseActivity) {
        supportFragmentManager.replaceMainContent(
            fragment, allowBackNav, sharedElems
        )
        (this as? MainActivity)?.collapseDrawers()
    }
}
fun Context.popMainContent() {
    if (this is BaseActivity) {
        supportFragmentManager.popBackStack()
    }
}
fun FragmentManager.popAllBackStack() {
    popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
}

val FragmentManager.currentFragment: Fragment? get() = findFragmentById(R.id.mainContentContainer)
val Context.currentFragment: Fragment? get() {
    return if (this is MainActivity) {
        supportFragmentManager.currentFragment
    } else null
}