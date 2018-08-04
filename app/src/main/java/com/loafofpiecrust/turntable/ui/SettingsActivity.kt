package com.loafofpiecrust.turntable.ui

import activitystarter.MakeActivityStarter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.prefs.MainPrefsFragment
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.verticalLayout

@MakeActivityStarter
class SettingsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AnkoContext.create(this, this).verticalLayout {
            id = R.id.mainContent

            fragmentManager.beginTransaction()
                .add(id, MainPrefsFragment())
                .commit()
        })
    }
}