plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// These values can be overridden from the command line, for example:
//   gradle assembleRelease -PappName="My Store" -PversionCode=12
val appName: String = (project.findProperty("appName") as String?) ?: "HyDr0 Store"
val appId: String = (project.findProperty("appId") as String?) ?: "tv.hydr0.store"
val buildNumber: Int = ((project.findProperty("versionCode") as String?) ?: "1").toInt()
val catalogUrl: String = (project.findProperty("catalogUrl") as String?)
    ?: "https://raw.githubusercontent.com/Blkmaze/HyDr0-Store/main/catalog/catalog.json"

android {
    namespace = "tv.hydr0.store"
    compileSdk = 34

    defaultConfig {
        applicationId = appId
        minSdk = 22          // Fire OS 5 and newer
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Escape quotes that would break the Android string resource.
        // (AGP already escapes & and < when it writes the XML.)
        val safeName = appName
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        resValue("string", "app_name", safeName)
        buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
    }

    // Release signing comes from environment variables set by the GitHub workflow.
    val keystorePath = System.getenv("STORE_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_KEYSTORE_PASS")
                keyAlias = System.getenv("STORE_KEY_ALIAS")
                keyPassword = System.getenv("STORE_KEY_PASS")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // Lint warnings (like the QUERY_ALL_PACKAGES notice) must not stop a CI build.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }

    // The bundled catalog comes from the same file the app downloads.
    sourceSets {
        getByName("main") {
            assets.srcDirs("../catalog")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
