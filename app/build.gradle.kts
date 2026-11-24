plugins {
    alias(libs.plugins.android.application)
    // Con AGP 9 il supporto Kotlin è integrato: nessun plugin Kotlin aggiuntivo.
}

// Valori passati dal workflow di GitHub Actions (vedi .github/workflows/build.yml).
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
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8: rimuove il codice inutilizzato, ottimizza e offusca. Rimuove anche le risorse inutili.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Con i Secrets configurati usa la tua chiave; altrimenti la chiave di debug,
            // così l'APK di release è comunque installabile.
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
        // Scelta della lingua per singola app nelle impostazioni di Android 13+.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit)
}
