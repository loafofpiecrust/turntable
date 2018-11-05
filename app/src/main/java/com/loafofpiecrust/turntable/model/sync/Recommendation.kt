package com.loafofpiecrust.turntable.model.sync

import com.loafofpiecrust.turntable.model.Recommendable


data class Recommendation(
    val music: Recommendable,
    val sender: User
)