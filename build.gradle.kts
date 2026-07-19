plugins {
    id("com.android.application") version "9.3.0" apply false
    // Compose 编译器插件版本需与 AGP 9.3.0 内置的 Kotlin(KGP 2.2.10)一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
