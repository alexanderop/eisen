plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** The commit the app is built from, so a bug report can name the exact build. */
val commit: String = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }.getOrElse("unknown")

/** Set in CI. Without them a release build is signed with the debug key. */
val keystorePath: String? = System.getenv("EISEN_KEYSTORE")

android {
    namespace = "io.github.alexanderop.eisen"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.alexanderop.eisen"
        minSdk = 36
        targetSdk = 36
        versionCode = (System.getenv("EISEN_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        buildConfigField("String", "COMMIT", "\"$commit\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("EISEN_KEYSTORE_PASSWORD")
                keyAlias = "eisen"
                keyPassword = System.getenv("EISEN_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The exercise pictures and the spoken cues are the PWA's files: one copy for both apps.
    sourceSets["main"].assets.srcDir("../../web/src/assets")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.serialization.json)
    implementation(libs.health.connect)
    // Google's code scanner: the barcode of a product without a camera permission or a camera library of our own.
    implementation(libs.code.scanner)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.archunit)
}

// FixturesTest reads the fixtures of the verify-eisen skill: an edit to one of them runs the tests again.
tasks.withType<Test>().configureEach {
    inputs.dir("../../../.claude/skills/verify-eisen/fixtures").withPropertyName("verifyFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
}
