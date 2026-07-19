plugins {
    id("com.android.application")
}

android {
    namespace = "com.adfree.yxt"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.adfree.yxt"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    // LibXposed 现代 Xposed API(运行时由 LSPosed 提供)
    compileOnly("io.github.libxposed:api:102.0.0")
}
