import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing credentials, or null when this machine has none.
 *
 * Read from `keystore.properties` at the repository root, which is excluded from
 * source control — see `keystore.properties.example` for the four keys it needs and
 * the keytool command that produces the store. Nothing secret is committed, and a
 * checkout without the file still builds: the release variant falls back to the
 * debug key, which is what every CI run and every other machine will do.
 */
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.takeIf { props ->
        // All four or none. A half-filled file would otherwise produce a signing
        // config that fails deep inside the packaging task with no clue why.
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
            .all { !props.getProperty(it).isNullOrBlank() }
    }

android {
    namespace = "com.ezygo.attendance"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ezygo.attendance"
        minSdk = 26
        targetSdk = 37
        // versionName is what people read; versionCode is what Android compares.
        // The code must strictly increase on every build anyone installs — an APK
        // whose code is not higher than the installed one is refused, silently as
        // far as the user is concerned. Kept tied to the name (5.1 -> 51) so there
        // is never a question of which build is newer.
        versionCode = 51
        versionName = "5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when the credentials are actually present. Declaring it
        // unconditionally with empty strings would let a build get all the way to
        // packaging before failing on a keystore that was never there.
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // v2 only, and v1 left off deliberately: minSdk is 26, every
                // device that can install this verifies v2, and AGP drops v1
                // anyway above minSdk 24. Asking for it just makes a larger APK
                // and a slower build for nobody.
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The real key when this machine has it, the debug key otherwise.
            //
            // The fallback is not a convenience: it is what keeps a checkout without
            // the keystore buildable and testable. A release APK signed with the
            // debug key is emphatically not distributable — that key is public — so
            // check `signingConfig` in the build output before shipping anything.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export keeps migration history reviewable in source control.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.vico.compose.m3)

    // Home-screen widgets. Glance gives size-responsive widgets from Compose-style
    // code, which is what lets one widget adapt to whatever cell the launcher gives it.
    implementation(libs.glance.appwidget)

    // Declared explicitly, not left to Glance. Glance composes every widget inside a
    // WorkManager worker, so WorkManager is on the critical path for the widget
    // drawing at all — and the version Glance asks for is 2.7.1, from 2021, which
    // this app would otherwise run under targetSdk 37.
    implementation(libs.androidx.work.runtime)

    // The Samarth portal has no API, only pages. Parsing them with a real HTML
    // parser rather than regexes is the difference between a scraper that bends
    // when the markup shifts and one that breaks.
    implementation(libs.jsoup)

    // Android can render a PDF but cannot read one: PdfRenderer rasterises to a
    // bitmap and exposes no text at all. Copying text out of a grade card, and
    // reading the SGPA off it, both need a real parser.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
