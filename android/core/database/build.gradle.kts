plugins {
    alias(libs.plugins.tolly.android.library)
    alias(libs.plugins.tolly.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "be.tolly.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
