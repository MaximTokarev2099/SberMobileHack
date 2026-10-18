import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localBuildSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
localBuildSettings.getProperty("build.dir")?.let { buildRoot ->
    layout.buildDirectory.set(file("$buildRoot/app"))
}

android {
    namespace = "com.sbermobilehack.magicball"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sbermobilehack.magicball"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.apply {
        srcDir("../assets")
        srcDir("src/main/assets")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.caverock:androidsvg-aar:1.4")
    testImplementation("junit:junit:4.13.2")
}

val syncConfig by tasks.registering(Copy::class) {
    from(rootProject.file("config.json"))
    into(layout.buildDirectory.dir("generated/configAssets"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/configAssets"))
tasks.named("preBuild").configure { dependsOn(syncConfig) }

tasks.withType<Test>().configureEach {
    defaultCharacterEncoding = "UTF-8"
}
