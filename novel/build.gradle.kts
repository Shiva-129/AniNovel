plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.novel"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.presentationCore)
    implementation(projects.i18n)

    // Compose
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    // Voyager navigation
    implementation(libs.bundles.voyager)

    // Swipe actions
    implementation(libs.swipe)

    // Coroutines
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // Serialization
    implementation(kotlinx.bundles.serialization)

    // Networking - NiceHttp (OkHttp wrapper used by QuickNovel providers)
    api("com.github.Blatzar:NiceHttp:0.4.16")

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)

    // Fuzzy search for novel lookup
    implementation("me.xdrop:fuzzywuzzy:1.4.0")

    // ML Kit translation
    implementation("com.google.mlkit:translate:17.0.3")

    // Rich text / Markdown rendering for novel content
    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "com.atlassian.commonmark")
    }
    implementation("io.noties.markwon:html:4.6.2") {
        exclude(group = "com.atlassian.commonmark")
    }
    implementation("org.commonmark:commonmark:0.21.0")

    // PDF reading support
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Shimmer loading effect
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Color picker (TTS / reader settings)
    implementation("com.jaredrummler:colorpicker:1.1.0")

    // Safe file URI handling
    implementation("com.github.LagradOst:SafeFile:a926e7615a")

    // Media session for TTS notification controls
    implementation("androidx.media:media:1.7.0")

    // WorkManager for download jobs
    implementation(androidx.workmanager)

    // HTML parsing for provider scrapers
    implementation(libs.jsoup)

    // DI
    implementation(libs.injekt)

    // Jackson (used by some QuickNovel providers for JSON parsing)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

    // Preferences
    implementation(libs.preferencektx)

    // SLF4J (transitive from pdfbox)
    implementation("org.slf4j:slf4j-android:1.7.36")

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
