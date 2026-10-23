import java.util.Properties

plugins {
    alias(libs.plugins.tolly.android.application)
    alias(libs.plugins.tolly.android.compose)
    alias(libs.plugins.tolly.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: environment variables in CI, or a git-ignored keystore.properties locally
// (storeFile, storePassword, keyAlias, keyPassword). Without either, release builds use the debug key.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(property: String, env: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(property)

val releaseStoreFile = signingValue("storeFile", "TOLLY_KEYSTORE_FILE")

android {
    namespace = "be.tolly"

    defaultConfig {
        // The app's identity on devices and Google Play; it can never change once published. The
        // code itself stays in the be.tolly package (namespace above).
        applicationId = "be.tolly"
        // The release workflow passes these; local builds keep the defaults.
        versionCode = providers.gradleProperty("tolly.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("tolly.versionName").orNull ?: "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "TOLLY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TOLLY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TOLLY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        localeFilters += listOf("en", "nl")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:login"))
    implementation(project(":feature:courses"))
    implementation(project(":feature:announcements"))
    implementation(project(":feature:agenda"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.backdrop)

    implementation(project(":core:database"))
}
