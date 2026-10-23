package be.tolly.core.model

/**
 * One-time notices: a sheet that existing users see once after an update, to show a new feature
 * or say that Tolly is on Google Play. Each notice has a number; adding one means raising [LATEST]
 * and giving the new number its text in the app's `NoticeSheet`. Someone who skipped a few updates
 * sees only the newest.
 *
 * New installs never see a notice: everything is new to them. Installs from before notices
 * existed have nothing stored, so a signed-in user without a stored notice counts as one that
 * updated.
 */
object Notices {
    /** The newest notice; 0 would mean there is none. */
    const val LATEST = 1

    sealed interface Decision {
        data class Show(val id: Int) : Decision

        /** A new install: remember the newest as seen, so it never shows. */
        data object MarkSeen : Decision

        data object None : Decision
    }

    /** What to do on start, once the sign-in state is known. [lastSeen] is null when nothing is stored. */
    fun decide(lastSeen: Int?, signedIn: Boolean, demo: Boolean, latest: Int = LATEST): Decision = when {
        latest <= 0 -> Decision.None
        lastSeen != null -> if (latest > lastSeen) Decision.Show(latest) else Decision.None
        signedIn && !demo -> Decision.Show(latest)
        else -> Decision.MarkSeen
    }
}
