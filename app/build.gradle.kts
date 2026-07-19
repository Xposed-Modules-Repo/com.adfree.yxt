plugins {
    id("com.android.application")
    // AGP 9 内置 Kotlin,无需 kotlin.android 插件;仅需 Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.adfree.yxt"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.adfree.yxt"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 用自动生成的 debug 证书签名,免密钥配置即可产出可安装的 release APK
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
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

    // Jetpack Compose(BOM 统一版本)+ Material3 + Activity 集成
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
}
