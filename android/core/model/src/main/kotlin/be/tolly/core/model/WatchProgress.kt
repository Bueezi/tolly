package be.tolly.core.model

/** How far a recording was watched: [position] of [duration], in milliseconds. */
data class WatchProgress(val position: Long, val duration: Long) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /** What is left to watch, in milliseconds. */
    val remaining: Long get() = (duration - position).coerceAtLeast(0)

    /**
     * Watched to the end, or nearly: a lecture often ends on a minute of an empty room. Opening
     * it again starts from the beginning.
     */
    val finished: Boolean get() = duration > 0 && remaining <= maxOf(END_MARGIN_MS, duration / 50)

    private companion object {
        const val END_MARGIN_MS = 30_000L
    }
}
