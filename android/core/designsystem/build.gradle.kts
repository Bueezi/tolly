plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.android.compose)
}

android {
    namespace = "be.tolly.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
}
