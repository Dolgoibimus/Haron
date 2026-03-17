import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vamp.haron"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vamp.haron"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ndk abiFilters removed — splits.abi handles architecture filtering

        val localProps = Properties()
        project.rootProject.file("local.properties").reader(Charsets.UTF_8).use { localProps.load(it) }
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${localProps.getProperty("GOOGLE_CLIENT_ID", "")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${localProps.getProperty("GOOGLE_CLIENT_SECRET", "")}\"")
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            project.rootProject.file("local.properties").reader(Charsets.UTF_8).use { localProps.load(it) }
            storeFile = file(localProps.getProperty("STORE_FILE"))
            storePassword = localProps.getProperty("STORE_PASSWORD")
            keyAlias = localProps.getProperty("KEY_ALIAS")
            keyPassword = localProps.getProperty("KEY_PASSWORD")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    packaging {
        jniLibs {
            // libaums ships native .so with 4KB alignment — incompatible with 16KB page size requirement.
            // We use Android USB Host API (not native libusb), so these are not needed.
            excludes += listOf("**/libusb-lib.so", "**/liberrno-lib.so")
            // VLC and ffmpeg-kit both ship libc++_shared.so — pick first to avoid conflict
            pickFirsts += "**/libc++_shared.so"
        }
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
    }
}

configurations.all {
    // smbj brings bcprov-jdk15to18, which conflicts with bcprov-jdk18on from dcerpc
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
}

dependencies {
    // Ecosystem Core
    implementation("com.vamp:core:1.0.0")

    // Shizuku (access to Android/data and Android/obb on Android 11+)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Document support (.doc)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    // PDF text extraction (content search)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Archive support (7z, rar, password-protected zip)
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // 7-Zip-JBinding for Android (RAR5 + password support via native 7z engine)
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    // Media3 ExoPlayer (inline previews)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // VLC (fullscreen player — universal codec support: AVI, DivX, Xvid, WMV, etc.)
    implementation("org.videolan.android:libvlc-all:3.6.5")

    // FFmpeg (video transcoding for Chromecast — AVI/MKV/WMV → MP4)
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // hilt-work/hilt-compiler removed — manual WorkerFactory avoids KSP bug with generic @AssistedFactory

    // ML Kit Image Labeling + OCR Text Recognition
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Tesseract OCR (better Cyrillic support)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Glance widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // HTTP server (Ktor CIO — file transfer + WebSocket for TV remote)
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-partial-content:2.3.12")
    implementation("io.ktor:ktor-server-auto-head-response:2.3.12")
    implementation("io.ktor:ktor-server-websockets:2.3.12")

    // Cloud storage
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.30.0")
    implementation("com.dropbox.core:dropbox-core-sdk:7.0.0")
    // OneDrive uses direct HTTP calls to Graph REST API (no SDK needed)
    implementation("androidx.browser:browser:1.8.0")
    // OkHttp (reliable large file uploads — keepalive, write timeout, HTTP/2)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JAudiotagger (write album art into ID3/Vorbis/M4A tags)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // libtorrent4j (torrent download — zero upload mode)
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")

    // Sora Editor (large file editing — renders only visible lines, handles MB-sized files)
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    implementation("io.github.Rosemoe.sora-editor:editor")

    // Diff utils (file comparison)
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")

    // ZXing QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // CameraX + ML Kit Barcode Scanning (QR scanner)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // FTP client (Apache Commons Net) + embedded FTP server (Apache FtpServer)
    implementation("commons-net:commons-net:3.11.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")

    // SMB client (smbj + RPC for share enumeration)
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.13")

    // SSH client (modern JSch fork — ed25519, rsa-sha2, curve25519)
    implementation("com.github.mwiede:jsch:0.2.18")

    // USB Mass Storage (libaums — FAT32 access + FS detection without root)
    implementation("me.jahnen.libaums:core:0.10.0")

    // Google Cast SDK (Chromecast)
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")

    // MediaRouter (Miracast + Cast route discovery)
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // AndroidX
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // ViewModel
    implementation(libs.viewmodel.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}