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
        versionCode = 5
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ndk abiFilters removed — splits.abi handles architecture filtering

        externalNativeBuild {
            cmake {
                cFlags("-std=c11")
            }
        }

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
            isEnable = project.findProperty("enableAbiSplits")?.toString()?.toBoolean() != false
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            // All features: torrent, hidden camera, root, floating window
            buildConfigField("Boolean", "HAS_TORRENT", "true")
            buildConfigField("Boolean", "HAS_ROOT_ACCESS", "true")
            buildConfigField("Boolean", "HAS_HIDDEN_CAMERA", "true")
            buildConfigField("Boolean", "HAS_FLOATING_WINDOW", "true")
            buildConfigField("Boolean", "HAS_VIDEO_PLAYER", "true")
            buildConfigField("Boolean", "HAS_TRANSCODING", "true")
            buildConfigField("Boolean", "HAS_OCR", "true")
            buildConfigField("Boolean", "HAS_DOCUMENT_READER", "true")
            buildConfigField("Boolean", "HAS_PDF_READER", "true")
            buildConfigField("Boolean", "HAS_QR_SCANNER", "true")
        }
        create("play") {
            dimension = "store"
            // Google Play safe — no restricted features
            buildConfigField("Boolean", "HAS_TORRENT", "false")
            buildConfigField("Boolean", "HAS_ROOT_ACCESS", "false")
            buildConfigField("Boolean", "HAS_HIDDEN_CAMERA", "false")
            buildConfigField("Boolean", "HAS_FLOATING_WINDOW", "false")
            buildConfigField("Boolean", "HAS_VIDEO_PLAYER", "true")
            buildConfigField("Boolean", "HAS_TRANSCODING", "true")
            buildConfigField("Boolean", "HAS_OCR", "true")
            buildConfigField("Boolean", "HAS_DOCUMENT_READER", "true")
            buildConfigField("Boolean", "HAS_PDF_READER", "true")
            buildConfigField("Boolean", "HAS_QR_SCANNER", "true")
        }
        create("mini") {
            dimension = "store"
            // Lightweight — no media player, no readers, no OCR (~45 MB)
            buildConfigField("Boolean", "HAS_TORRENT", "false")
            buildConfigField("Boolean", "HAS_ROOT_ACCESS", "true")
            buildConfigField("Boolean", "HAS_HIDDEN_CAMERA", "false")
            buildConfigField("Boolean", "HAS_FLOATING_WINDOW", "true")
            buildConfigField("Boolean", "HAS_VIDEO_PLAYER", "false")
            buildConfigField("Boolean", "HAS_TRANSCODING", "false")
            buildConfigField("Boolean", "HAS_OCR", "false")
            buildConfigField("Boolean", "HAS_DOCUMENT_READER", "false")
            buildConfigField("Boolean", "HAS_PDF_READER", "false")
            buildConfigField("Boolean", "HAS_QR_SCANNER", "false")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
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

    // Shared source set for full + play (everything mini doesn't have)
    sourceSets {
        getByName("full") {
            java.srcDir("src/notMini/java")
            kotlin.srcDir("src/notMini/java")
        }
        getByName("play") {
            java.srcDir("src/notMini/java")
            kotlin.srcDir("src/notMini/java")
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

    // Torrent streaming (full flavor only — banned on Google Play)
    // 2.1.0-35: 1.x doesn't connect to modern peers
    "fullImplementation"("org.libtorrent4j:libtorrent4j:2.1.0-35")
    "fullImplementation"("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-35")
    "fullImplementation"("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-35")

    // Document support (.doc) — not in mini
    "fullImplementation"("org.apache.poi:poi:5.2.5")
    "fullImplementation"("org.apache.poi:poi-scratchpad:5.2.5")
    "playImplementation"("org.apache.poi:poi:5.2.5")
    "playImplementation"("org.apache.poi:poi-scratchpad:5.2.5")

    // PDF text extraction (content search) — not in mini
    "fullImplementation"("com.tom-roush:pdfbox-android:2.0.27.0")
    "playImplementation"("com.tom-roush:pdfbox-android:2.0.27.0")

    // Archive support (7z, rar, password-protected zip)
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("org.slf4j:slf4j-api:2.0.13") // slf4j-nop заменён своим NoOpSLF4JServiceProvider (common/logging/)
    // zip4j replaced by AesZipHelper (common/util/AesZipHelper.kt)
    // 7-Zip-JBinding for Android (RAR5 + password support via native 7z engine)
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    // Media3 — common + session for all (Cast needs it), exoplayer + ui only for full/play
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    "fullImplementation"("androidx.media3:media3-exoplayer:1.5.1")
    "fullImplementation"("androidx.media3:media3-ui:1.5.1")
    "playImplementation"("androidx.media3:media3-exoplayer:1.5.1")
    "playImplementation"("androidx.media3:media3-ui:1.5.1")

    // VLC (fullscreen player) — not in mini
    "fullImplementation"("org.videolan.android:libvlc-all:3.6.5")
    "playImplementation"("org.videolan.android:libvlc-all:3.6.5")

    // FFmpeg (video transcoding for Chromecast) — not in mini
    "fullImplementation"("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
    "playImplementation"("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // FileIndexJobService replaces WorkManager (no dependency needed)

    // ML Kit Image Labeling + OCR Text Recognition — not in mini
    "fullImplementation"("com.google.mlkit:image-labeling:17.0.9")
    "fullImplementation"("com.google.mlkit:text-recognition:16.0.1")
    "playImplementation"("com.google.mlkit:image-labeling:17.0.9")
    "playImplementation"("com.google.mlkit:text-recognition:16.0.1")

    // Tesseract OCR (better Cyrillic support) — not in mini
    "fullImplementation"("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    "playImplementation"("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Glance widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // HTTP server: SimpleHttpServer (raw sockets, replaces Ktor CIO ~3 MB)
    // No external dependency needed — see data/transfer/SimpleHttpServer.kt

    // Cloud storage
    // Google Drive — uses direct HTTP REST API (no SDK needed)
    implementation("com.dropbox.core:dropbox-core-sdk:7.0.0")
    // OneDrive uses direct HTTP calls to Graph REST API (no SDK needed)
    implementation("androidx.browser:browser:1.8.0")
    // OkHttp (reliable large file uploads — keepalive, write timeout, HTTP/2)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    // Core library desugaring (Java 11+ API on older Android)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Sora Editor (code editor with syntax highlighting)
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.6"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")

    // java-diff-utils заменён своим MyersDiff (common/util/MyersDiff.kt)

    // QR code generation — Nayuki (common/qr/QrCode.java), no external dependency

    // CameraX + ML Kit Barcode Scanning (QR scanner) — not in mini
    "fullImplementation"("androidx.camera:camera-camera2:1.4.1")
    "fullImplementation"("androidx.camera:camera-lifecycle:1.4.1")
    "fullImplementation"("androidx.camera:camera-view:1.4.1")
    "fullImplementation"("com.google.mlkit:barcode-scanning:17.3.0")
    "playImplementation"("androidx.camera:camera-camera2:1.4.1")
    "playImplementation"("androidx.camera:camera-lifecycle:1.4.1")
    "playImplementation"("androidx.camera:camera-view:1.4.1")
    "playImplementation"("com.google.mlkit:barcode-scanning:17.3.0")

    // FTP client: SimpleFtpClient (raw socket, replaces Apache Commons Net)
    // Embedded FTP server: SimpleFtpServer (pure socket, RFC 959, replaces Apache ftpserver-core)

    // SMB client (smbj + RPC for share enumeration)
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.13")

    // BouncyCastle (required by smbj for NTLM/SPNEGO; POI brings it in full/play, mini needs explicit)
    implementation("org.bouncycastle:bcprov-jdk15to18:1.72")

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