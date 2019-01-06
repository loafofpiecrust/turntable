package com.loafofpiecrust.turntable.ui

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
import com.firebase.ui.auth.IdpResponse
import com.github.ajalt.timberkt.Timber
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.AlbumDetailsUI
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.player.MiniPlayerFragment
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.player.NowPlayingFragment
import com.loafofpiecrust.turntable.player.QueueFragment
import com.loafofpiecrust.turntable.prefs.SettingsActivityStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.Sync
import com.loafofpiecrust.turntable.ui.universal.UniversalFragment
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.util.group
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.scopedName
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.views.MultiSheetView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.tasks.await
import org.jetbrains.anko.*
import org.jetbrains.anko.design.navigationView
import org.jetbrains.anko.support.v4.drawerLayout

class MainActivity : BaseActivity() {
    sealed class Action: Parcelable {
        @Parcelize class OpenNowPlaying: Action()
        @Parcelize data class SyncRequest(
            val request: Sync.SentMessage<Sync.Request>
        ): Action()
        @Parcelize data class FriendRequest(
            val sender: User
        ): Action()
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
        backgroundColor = colorAttr(android.R.attr.windowBackground)
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
            clipToPadding = false
            clipToOutline = false
            elevation = dimen(R.dimen.medium_elevation).toFloat()
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

        askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { res ->
            if (res.isAccepted) {
                Library.initData(applicationContext)
                Sync.requestLogin(this, soft = false)
            } else {
                toast("Unable to load local music")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val action = intent.getParcelableExtra<Action>("action")
        when (action) {
            // TODO: Impl with BottomSheet
            is Action.OpenNowPlaying -> sheets.unhide(true)
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
                val req = action.request
                alert("Sync request from ${req.sender.name}") {
                    positiveButton(R.string.user_sync_accept) {
                        // set sync mode to One on One, enable sync
                        // change some UI element to indicate sync mode (in Now Playing?)
                        // TODO: Send display uuid or have that somewhere.
                        Sync.confirmSync(req)
                        val target = req.message.mode ?: req.sender
                        toast("Now synced with $target")
                    }
                    negativeButton(R.string.request_decline) {
                        Sync.declineSync(req)
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
            } else if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                val rawUri = intent.data!!
                Timber.d { rawUri.toString() }
                launch {
                    val uri = FirebaseDynamicLinks.getInstance()
                        .getDynamicLink(intent)
                        .await()?.link

                    handleLink(uri ?: rawUri)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 69 && data != null) {
            val result = IdpResponse.fromResultIntent(data)
            if (resultCode == RESULT_OK) {
                Sync.login(FirebaseAuth.getInstance().currentUser!!)
            } else {
                // Login failed!
                Timber.e(result?.error)
            }
        } else if (requestCode == 42) {
            val uri = data!!.data!!

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
        // collapse the side drawer
        drawers?.closeDrawers()

        // collapse the queue
        if (sheets.consumeBackPress()) {
            // collapse the whole Now Playing sheet
            sheets.consumeBackPress()
        }
    }

    private suspend fun handleLink(url: Uri) {
        // Possible urls (recommendations, sync)
        // turntable://album?name=*&artist=*
        // turntable://artist?uuid=*
        // turntable://sync-request?from=[USERID]
        val parts = url.pathSegments
        // parts[0] == 'turntable'
        when (parts[1]) {
            "album" -> withContext(Dispatchers.Main) {
                val title = url.getQueryParameter("name")!!
                val artist = url.getQueryParameter("artist")!!

                replaceMainContent(
                    AlbumDetailsUI(
                        AlbumId(title, ArtistId(artist))
                    ).createFragment()
                )
            }
            "artist" -> withContext(Dispatchers.Main) {
                val name = url.getQueryParameter("id")!!

                replaceMainContent(
                    ArtistDetailsUI(ArtistId(name)).createFragment()
                )
            }
            "sync-request" -> withContext(Dispatchers.Default) {
                val id = url.getQueryParameter("from")!!
//                val displayName = url.getQueryParameter("id")
                User.resolve(id)?.let {
                    Sync.requestSync(it)
                }
            }
            "lets-be-friends" -> withContext(Dispatchers.Default) {
                val id = url.getQueryParameter("id")!!

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
        runBlocking { KotprefModel.saveFiles() }
    }
}

private fun FragmentManager.replaceMainContent(fragment: Fragment, allowBackNav: Boolean, sharedElems: List<View>? = null) {
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

        val screenName = if (fragment is UniversalFragment) {
            fragment.component::class.scopedName
        } else {
            fragment.javaClass.scopedName
        }
        FirebaseAnalytics.getInstance(this).setCurrentScreen(this, screenName, null)
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