plugins {
    alias(libs.plugins.android.application)
    // Con AGP 9 il supporto Kotlin è integrato.
}

// Valori passati dal workflow di GitHub Actions.
val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull
val signingKeystore = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.calcolatrice"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.calcolatrice"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "1.0"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Ottimizzazione e riduzione delle dimensioni dell'APK.
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Firma privata per GitHub Actions.
            // Firma debug disponibile per compilazioni locali.
            signingConfig = if (signingKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Scelta della lingua per singola app su Android 13+.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit)
}
