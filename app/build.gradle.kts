plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

if (file("google-services.json").exists() && !providers.gradleProperty("stack.testApplicationIdSuffix").isPresent) {
    apply(plugin = "com.google.gms.google-services")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun secret(name: String): String? =
    keystoreProperties.getProperty(name) ?: System.getenv(name)

val releaseStoreFile = secret("STACK_RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    secret("STACK_RELEASE_STORE_PASSWORD") != null &&
    secret("STACK_RELEASE_KEY_ALIAS") != null &&
    secret("STACK_RELEASE_KEY_PASSWORD") != null

android {
    namespace = "com.stackapp.stack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stackapp.stack"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Deliberately off until provider onboarding and purchase verification are complete.
        buildConfigField("boolean", "PAYMENTS_ENABLED", "false")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = secret("STACK_RELEASE_STORE_PASSWORD")
                keyAlias = secret("STACK_RELEASE_KEY_ALIAS")
                keyPassword = secret("STACK_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildFeatures { buildConfig = true }
    testBuildType = providers.gradleProperty("stack.testBuildType").orElse("debug").get()

    buildTypes {
        debug {
            applicationIdSuffix = providers.gradleProperty("stack.testApplicationIdSuffix").orNull
            buildConfigField("boolean", "USE_FIREBASE", providers.gradleProperty("stack.firebaseDebug").orElse("false").get())
            manifestPlaceholders["analyticsCollectionEnabled"] = "false"
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE", "true")
            manifestPlaceholders["analyticsCollectionEnabled"] = "true"
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("liveTest") {
            initWith(getByName("release"))
            // Real service configuration and signing, with a complete test runtime.
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            manifestPlaceholders["analyticsCollectionEnabled"] = "false"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation(platform("com.google.firebase:firebase-bom:34.14.1"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.navigation3:navigation3-ui:1.0.1")


    implementation("org.jbox2d:jbox2d-library:2.2.1.1")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-common")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-functions")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
