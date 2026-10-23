plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "be.tolly.core.auth"
}

dependencies {
    api(libs.okhttp)
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
