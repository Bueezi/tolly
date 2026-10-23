package be.tolly.core.auth

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toOkHttp(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .path(path)
        .expiresAt(expiresAt)
        .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
        .apply { if (secure) secure() }
        .apply { if (httpOnly) httpOnly() }
        .build()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}

@Serializable
data class StoredUser(
    val id: String,
    val userName: String,
    val displayName: String,
    val email: String? = null,
    val studentId: String? = null,
)

@Serializable
data class StoredSession(
    val cookies: List<StoredCookie> = emptyList(),
    val user: StoredUser? = null,
)

/**
 * Persists the Ultra session (cookies + signed-in user) encrypted with an AES-256-GCM key that is
 * itself wrapped by a non-exportable Android Keystore key. The password is never stored.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore: DataStore<StoredSession> = DataStoreFactory.create(
        serializer = EncryptedSessionSerializer { aead(context) },
        corruptionHandler = ReplaceFileCorruptionHandler { StoredSession() },
        produceFile = { context.dataStoreFile("session.bin") },
    )

    suspend fun read(): StoredSession = dataStore.data.first()

    suspend fun update(transform: (StoredSession) -> StoredSession) {
        dataStore.updateData(transform)
    }

    suspend fun clear() {
        dataStore.updateData { StoredSession() }
    }

    private companion object {
        private const val KEYSET_NAME = "tolly_session_keyset"
        private const val KEYSET_PREFS = "tolly_session_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://tolly_session_master_key"

        @Volatile
        private var cachedAead: Aead? = null

        fun aead(context: Context): Aead = cachedAead ?: synchronized(this) {
            cachedAead ?: run {
                AeadConfig.register()
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            }.also { cachedAead = it }
        }
    }
}

internal class EncryptedSessionSerializer(
    private val aead: () -> Aead,
) : Serializer<StoredSession> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: StoredSession = StoredSession()

    override suspend fun readFrom(input: InputStream): StoredSession {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            val plain = aead().decrypt(bytes, ASSOCIATED_DATA)
            json.decodeFromString(StoredSession.serializer(), plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            // Key lost (e.g. app data restored to another device): start signed out.
            throw CorruptionException("Session could not be decrypted", e)
        }
    }

    override suspend fun writeTo(t: StoredSession, output: OutputStream) {
        val plain = json.encodeToString(StoredSession.serializer(), t).encodeToByteArray()
        output.write(aead().encrypt(plain, ASSOCIATED_DATA))
    }

    private companion object {
        val ASSOCIATED_DATA = "tolly-session-v1".encodeToByteArray()
    }
}
