plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "be.tolly.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:auth"))
    implementation(project(":core:model"))
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
}
