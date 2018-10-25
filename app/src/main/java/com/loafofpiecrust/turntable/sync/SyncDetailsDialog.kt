package com.loafofpiecrust.turntable.sync

import activitystarter.MakeActivityStarter
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.ui.BaseDialogFragment
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.textView
import org.jetbrains.anko.verticalLayout

@MakeActivityStarter
class SyncDetailsDialog: BaseDialogFragment() {
    override fun ViewManager.createView(): View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?) = alert {
        titleResource = R.string.sync_details
        customView {
            verticalLayout {
                val modeLabel = textView()
                val latencyLabel = textView()

                launch {
                    Sync.mode.consumeEach { mode ->
                        modeLabel.text = when (mode) {
                            is Sync.Mode.OneOnOne -> ctx.getString(R.string.synced_with_user, mode.other.name)
                            is Sync.Mode.InGroup -> ctx.getString(R.string.synced_with_group, mode.group.name)
                            else -> ""
                        }
                    }
                }
//                launch {
//                    Sync.latency.openSubscription()
//                        .map { getString(R.string.sync_latency, it) }
//                        .consumeEach { latencyLabel.text = it }
//                }
            }
        }

        positiveButton(R.string.sync_disconnect) {
            Sync.disconnect()
        }
        cancelButton {}
    }.build() as Dialog
}