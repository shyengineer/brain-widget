plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shyengineer.brainwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shyengineer.brainwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // 고정 키로 서명해야 새 버전을 지우지 않고 덮어 설치할 수 있다.
        create("fixed") {
            storeFile = rootProject.file("keystore/brainwidget.jks")
            storePassword = "brainwidget"
            keyAlias = "brainwidget"
            keyPassword = "brainwidget"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
