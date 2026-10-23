package be.tolly.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import be.tolly.core.auth.ApplicationScope
import be.tolly.core.auth.CookieStorage
import be.tolly.core.auth.SessionStoreCookieStorage
import be.tolly.core.auth.SilentReauthenticator
import be.tolly.core.auth.WebViewSilentReauthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun cookieStorage(impl: SessionStoreCookieStorage): CookieStorage

    @Binds
    abstract fun silentReauthenticator(impl: WebViewSilentReauthenticator): SilentReauthenticator

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
