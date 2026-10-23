plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.hilt)
}

android {
    namespace = "be.tolly.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:auth"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.turbine)
}
