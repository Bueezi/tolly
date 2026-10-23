package be.tolly.core.auth

/**
 * Whether the app shows its made-up demo account (for reviewers and anyone trying it out without
 * a KU Leuven login). While it is on, refreshes don't go to the network: they succeed without
 * changing the cached demo data. A process-wide flag, as the data layer's request wrapper has no
 * session to ask.
 */
object DemoMode {
    @Volatile
    var active: Boolean = false
        internal set
}
