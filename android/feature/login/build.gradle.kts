plugins {
    alias(libs.plugins.tolly.android.feature)
}

android {
    namespace = "be.tolly.feature.login"
}

dependencies {
    implementation(libs.androidx.webkit)
}
