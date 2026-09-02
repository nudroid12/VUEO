plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vueoTvCiVersionCode =
    System.getenv("VUEO_VERSION_CODE")
        ?.toIntOrNull()
val vueoTvCiVersionName =
    System.getenv("VUEO_VERSION_NAME")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePath =
    System.getenv("VUEO_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
val vueoKeystorePassword =
    System.getenv("VUEO_KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.vueotv.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vueotv.app"
        minSdk = 23
        targetSdk = 36
        versionCode = vueoTvCiVersionCode ?: 1
        versionName = vueoTvCiVersionName ?: "0.1.0"
    }

    signingConfigs {
        create("vueoTvRelease") {
            if (
                vueoKeystorePath != null &&
                vueoKeystorePassword != null
            ) {
                storeFile = file(vueoKeystorePath)
                storePassword = vueoKeystorePassword
                keyAlias = "vueo"
                keyPassword = vueoKeystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (
                vueoKeystorePath != null &&
                vueoKeystorePassword != null
            ) {
                signingConfig =
                    signingConfigs.getByName("vueoTvRelease")
            }
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
