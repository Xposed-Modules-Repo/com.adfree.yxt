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
        targetSdk = 33
        versionCode = 4
        versionName = "4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Xposed API 仅编译期依赖(运行时由 LSPosed 提供)
    compileOnly(files("libs/xposed-api-82.jar"))
}
