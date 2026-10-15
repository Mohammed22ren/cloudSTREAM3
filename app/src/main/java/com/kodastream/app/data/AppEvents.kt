package com.kodastream.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/** One-way messages from the Activity to the UI. */
object AppEvents {
    /** A repository link (cloudstreamrepo://...) that was opened from outside the app. */
    val pendingRepoUrl = MutableStateFlow<String?>(null)
}
