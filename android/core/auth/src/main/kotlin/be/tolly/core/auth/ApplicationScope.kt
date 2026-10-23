package be.tolly.core.auth

import javax.inject.Qualifier

/** A process-wide [kotlinx.coroutines.CoroutineScope] for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
