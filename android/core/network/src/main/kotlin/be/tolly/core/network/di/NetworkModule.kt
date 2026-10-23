package be.tolly.core.network.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import be.tolly.core.auth.SessionCookieJar
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.network.BuildConfig
import be.tolly.core.network.ReauthInterceptor
import be.tolly.core.network.UltraApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client with long timeouts for file downloads. Shares the session cookie jar. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun okHttpClient(
        @ApplicationContext context: Context,
        cookieJar: SessionCookieJar,
        reauth: ReauthInterceptor,
    ): OkHttpClient {
        val userAgent = userAgent(context)
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .addInterceptor(reauth)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Cookie")
                            redactHeader("Set-Cookie")
                        },
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @DownloadClient
    fun downloadClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder()
            .readTimeout(2, TimeUnit.MINUTES)
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(UltraEndpoints.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun ultraApi(retrofit: Retrofit): UltraApi = retrofit.create(UltraApi::class.java)

    /** `Tolly/<app version> (Android <release>; …)`, so Toledo's logs can tell app versions apart. */
    private fun userAgent(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "Tolly${version?.let { "/$it" }.orEmpty()} (Android ${Build.VERSION.RELEASE}; unofficial KU Leuven Toledo client) okhttp"
    }
}
