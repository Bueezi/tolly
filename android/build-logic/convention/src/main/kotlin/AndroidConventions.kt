import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal object TollySdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
    const val BUILD_TOOLS = "37.0.0"
    val JAVA = JavaVersion.VERSION_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun ApplicationExtension.configureTollyAndroid() {
    compileSdk = TollySdk.COMPILE
    buildToolsVersion = TollySdk.BUILD_TOOLS
    defaultConfig {
        minSdk = TollySdk.MIN
        targetSdk = TollySdk.TARGET
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = TollySdk.JAVA
        targetCompatibility = TollySdk.JAVA
    }
}

internal fun LibraryExtension.configureTollyAndroid() {
    compileSdk = TollySdk.COMPILE
    buildToolsVersion = TollySdk.BUILD_TOOLS
    defaultConfig {
        minSdk = TollySdk.MIN
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = TollySdk.JAVA
        targetCompatibility = TollySdk.JAVA
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Unit tests run against the debug variant only. The release copies test the same code, so they
 * would only double the time `./gradlew test` takes.
 */
internal fun Project.debugUnitTestsOnly() {
    val components = extensions.getByType(AndroidComponentsExtension::class.java)
    components.beforeVariants(components.selector().withBuildType("release")) { variant ->
        (variant as? HasHostTestsBuilder)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.enable = false
    }
}
