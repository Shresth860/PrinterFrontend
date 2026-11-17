import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
val serviceBaseUrl = providers.gradleProperty("BASE_URL")
    .orElse(providers.environmentVariable("BASE_URL"))
    .orElse("https://printer-backend-2.onrender.com/")
    .get()
    .trimEnd('/') + "/"
require(serviceBaseUrl.startsWith("https://", ignoreCase = true)) {
    "BASE_URL must use HTTPS."
}
val projectLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val configuredBbnBaseUrl = providers.gradleProperty("BBN_BASE_URL")
    .orElse(providers.environmentVariable("BBN_BASE_URL"))
    .orElse(projectLocalProperties.getProperty("BBN_BASE_URL") ?: "")
    .get().trim()
require(configuredBbnBaseUrl.isNotBlank()) {
    "BBN_BASE_URL is required. Set it in local.properties, as a Gradle property, or in the BBN_BASE_URL environment variable."
}
val bbnBaseUrl = configuredBbnBaseUrl.trimEnd('/') + "/"
require(bbnBaseUrl.startsWith("https://", ignoreCase = true) && bbnBaseUrl.endsWith("/api/")) {
    "BBN_BASE_URL must be an HTTPS API root ending in /api/ (for example: https://api.bizbitenow.in/api/)."
}

tasks.configureEach {
    if (name == "validateSigningRelease") {
        doFirst {
            val androidExtension = project.extensions.getByName("android") as com.android.build.gradle.AppExtension
            check(androidExtension.signingConfigs.getByName("release").storeFile?.exists() == true) {
                "Release signing requires a valid keystore. Configure keystore.properties or KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD."
            }
        }
    }
}

android {
    namespace = "com.bizbite.printer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bizbite.printer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "BASE_URL",
            "\"$serviceBaseUrl\""
        )
        buildConfigField("String", "BBN_BASE_URL", "\"$bbnBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingProperties.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
            val storePass = signingProperties.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
            val alias = signingProperties.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
            val keyPass = signingProperties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPass.isNullOrBlank()
            ) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
