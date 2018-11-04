package com.loafofpiecrust.turntable.ui

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
import com.chibatching.kotpref.KotprefModel
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumDetailsUI
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.prefs.SettingsActivityStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.putsMapped
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.group
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        requestLogin()
    }

    private fun requestLogin() {
        try {
            val lastAcc = FirebaseAuth.getInstance().currentUser
            if (lastAcc != null) {
                Sync.login(lastAcc)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        menuItem(R.string.action_library) {
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
            backgroundColor = colorAttr(android.R.attr.windowBackground)
            fragment { NowPlayingFragment() }
        }
        firstSheetPeek {
            id = R.id.miniPlayer
            backgroundColor = colorAttr(android.R.attr.windowBackground)
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
    }.apply {
        sheets = this
        // Start out with collapsed sheets
        hide(true, false)
        bindHidden(MusicService.player.switchMap {
            it?.queue?.map { it.current == null }
        })
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
    }

    lateinit var gclient: GoogleApiClient

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val action = intent.getParcelableExtra<Action>("action")
        info { action }
        when (action) {
            // TODO: Impl with BottomSheet
//            is Action.OpenNowPlaying -> slider?.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            is Action.FriendRequest -> {
                val sender = action.sender
                alert("${sender.name} wants to be friends") {
                    positiveButton(R.string.user_befriend) {
                        Friend.respondToRequest(sender, true)
                    }
                    negativeButton(R.string.request_decline) {
                        Friend.respondToRequest(sender, false)
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
                        Sync.confirmSync(sender)
                        toast("Now synced with ${sender.name}")
                    }
                    negativeButton(R.string.request_decline) {
                        Sync.declineSync(sender)
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
                            MusicService.offer(PlayerAction.PlaySongs(listOf(song)))
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

        MainActivityStarter.fill(this, data?.extras)
        info { action }

        if (requestCode == 69 && data != null) {
            val result = IdpResponse.fromResultIntent(data)
            if (resultCode == RESULT_OK) {
                Sync.login(FirebaseAuth.getInstance().currentUser!!)
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
                val title = url.getQueryParameter("name")!!
                val artist = url.getQueryParameter("artist")!!
                replaceMainContent(
                    AlbumDetailsUI(
                        AlbumId(title, ArtistId(artist))
                    ).createFragment()
                )
            }
            "artist" -> {
                val name = url.getQueryParameter("id")!!
                replaceMainContent(
                    ArtistDetailsUI(ArtistId(name)).createFragment()
                )
            }
            "sync-request" -> {
                val id = url.getQueryParameter("from")!!
//                val displayName = url.getQueryParameter("id")
                User.resolve(id)?.let {
                    Sync.requestSync(it)
                }
            }
            "lets-be-friends" -> {
                val id = url.getQueryParameter("id")
//                val uuid = url.getQueryParameter("id")

                val other = User.resolve(id)?.let {
                    Friend(it, Friend.Status.CONFIRMED)
                }
                if (other != null && !Friend.friends.value.contains(other.user)) {
                    Friend.respondToRequest(other.user, true)
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        // Save preference files here!
        runBlocking(Dispatchers.IO) { KotprefModel.saveFiles() }
    }
}

fun FragmentManager.replaceMainContent(fragment: Fragment, allowBackNav: Boolean, sharedElems: List<View>? = null) {
    beginTransaction().apply {
//        currentFragment?.exitTransition = Fade()
        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        sharedElems?.forEach { view ->
            view.transitionName?.let { tag ->
                addSharedElement(view, tag)
            }
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