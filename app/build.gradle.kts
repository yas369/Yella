plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.smsledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smsledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Two builds from one codebase (README "Builds"). Same applicationId and key, so installing
    // `sms` over `offline` keeps the user's data. Neither build has internet access.
    flavorDimensions += "capture"
    productFlavors {
        // No SMS permissions: installs from an APK file anywhere. Manual entry only.
        create("offline") {
            dimension = "capture"
            buildConfigField("boolean", "SMS_CAPTURE", "false")
        }
        // Automatic capture after in-app consent. READ_SMS/RECEIVE_SMS live in src/sms/AndroidManifest.xml.
        // Play Protect blocks this build when sideloaded from a file; install over USB or via the Play Store.
        create("sms") {
            dimension = "capture"
            buildConfigField("boolean", "SMS_CAPTURE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

// Exported so every schema change ships with a migration (tasks/todo.md T6).
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Expressive and layout APIs are still marked experimental; opted in once here rather than per file.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
