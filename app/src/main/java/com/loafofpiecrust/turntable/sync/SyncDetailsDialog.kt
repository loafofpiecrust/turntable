package com.loafofpiecrust.turntable.sync

import activitystarter.MakeActivityStarter
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.alert

@MakeActivityStarter
class SyncDetailsDialog: BaseDialogFragment() {
    override fun ViewManager.createView(): View? = verticalLayout {
        padding = dimen(R.dimen.dialog_content_margin)

        val modeLabel = textView()
        val latencyLabel = textView()

        SyncSession.mode.consumeEachAsync { mode ->
            modeLabel.text = when (mode) {
                is Sync.Mode.OneOnOne -> getString(R.string.synced_with_user, mode.other.name)
                is Sync.Mode.InGroup -> getString(R.string.synced_with_group, mode.group.name)
                else -> ""
            }
        }

        SyncSession.latency.consumeEachAsync {
            latencyLabel.text = getString(R.string.sync_latency, it)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = alert {
        titleResource = R.string.sync_details
        customView { createView() }

        positiveButton(R.string.sync_disconnect) {
            SyncSession.stop()
        }
        cancelButton {}
    }.build() as Dialog
}