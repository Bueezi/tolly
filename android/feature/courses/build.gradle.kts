plugins {
    alias(libs.plugins.tolly.android.feature)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "be.tolly.feature.courses"
}

dependencies {
    implementation(libs.reorderable)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
