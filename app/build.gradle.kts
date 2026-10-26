plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val galaxyOnlyBuild = providers.gradleProperty("galaxyOnly").getOrElse("false").toBoolean()
val qaBuild = providers.gradleProperty("qaBuild").getOrElse("false").toBoolean()
require(!(qaBuild && providers.gradleProperty("bbcDiagnostic").getOrElse("false").toBoolean())) { "QA and BBC diagnostic cannot share an artifact" }
val bbcDiagnosticBuild = providers.gradleProperty("bbcDiagnostic").getOrElse("false").toBoolean()
android {
    namespace = "com.nabd.news"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nabd.news"
        if(galaxyOnlyBuild) ndk { abiFilters += "arm64-v8a" }
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.1.2-personal"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if(qaBuild) testInstrumentationRunnerArguments["clearPackageData"] = "true"
        manifestPlaceholders["nabdAppLabel"] = if(qaBuild) "نبض — اختبار" else "نبض"
        manifestPlaceholders["bbcDiagnosticEnabled"] = bbcDiagnosticBuild.toString()
        manifestPlaceholders["nabdMainEnabled"] = (!bbcDiagnosticBuild).toString()
    }
    buildTypes.getByName("debug") {
        if (qaBuild) applicationIdSuffix = ".qa"
        if (bbcDiagnosticBuild) {
            applicationIdSuffix = ".bbcdiagnostic"
            versionNameSuffix = "-bbc-diagnostic"
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions {
        unitTests.isIncludeAndroidResources = true
        if(qaBuild) execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20250107")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    listOf("robolectric.offline", "robolectric.dependency.dir").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
    systemProperty("nabd.bbcReference", providers.gradleProperty("bbcReference").getOrElse("false"))
    systemProperty("nabd.liveFeeds", providers.gradleProperty("liveFeeds").getOrElse("false"))
}

// Instrumented tests may edit the notebook and settings. Never install them over personal data.
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doFirst { check(qaBuild) { "Device tests require -PqaBuild=true (isolated com.nabd.news.qa). Personal data is not a test fixture." } }
}
