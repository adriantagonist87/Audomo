plugins { id("com.android.application") }
val dropboxAppKey = providers.gradleProperty("audomoDropboxAppKey")
    .orElse(providers.environmentVariable("AUDOMO_DROPBOX_APP_KEY"))
    .orElse("tyhojvodo6itbg5").get().trim()
require(dropboxAppKey.isEmpty() || dropboxAppKey.matches(Regex("[a-z0-9]+"))) {
    "Für audomoDropboxAppKey den öffentlichen App-Key aus den Dropbox-App-Einstellungen angeben."
}
android {
    namespace = "de.audomo"
    compileSdk = 37
    defaultConfig {
        applicationId = "de.audomo"
        minSdk = 34
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 21
        versionName = "1.0.1"
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
        manifestPlaceholders["dropboxScheme"] = "db-" + dropboxAppKey.ifEmpty { "audomo-unconfigured" }
        manifestPlaceholders["dropboxConfigured"] = dropboxAppKey.isNotEmpty().toString()
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.dropbox.core:dropbox-android-sdk:7.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("commons-net:commons-net:3.13.0")
    implementation("com.github.mwiede:jsch:2.28.8")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.core:core:1.18.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    testImplementation("junit:junit:4.13.2")
}
