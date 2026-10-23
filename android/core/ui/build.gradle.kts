plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.android.compose)
}

android {
    namespace = "be.tolly.core.ui"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.kotlinx.coroutines.android)
}
