plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("androidx.navigation.safeargs")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.billiontests.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.billiontests.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

//        externalNativeBuild {
//            cmake {
//                cppFlags.addAll(listOf("-frtti", "-fexceptions"))
//                abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
//                arguments.add("-DOpenCV_DIR=" + "C:\\Users\\harsh\\Upjao\\Projects\\android\\Color-calibaration\\app\\ColorCalibaration\\opencv\\native")
//            }
//        }

//        ndk {
//            // Set the supported SO library architectures (developers can choose one or more platforms as needed)
//            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
//        }

    }

    signingConfigs {
        create("release") {
            storeFile = file("D:\\Upjao-old\\Color-calibaration\\play-store-details\\BillionTests\\billiontests.jks")
            storePassword = "billiontests123"
            keyAlias = "key0"
            keyPassword = "billiontests123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Views/Fragments integration
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Feature module support for Fragments
    implementation(libs.androidx.navigation.dynamic.features.fragment)

    implementation(libs.androidx.viewpager2)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // CameraX core library using the camera2 implementation
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation(libs.androidx.camera.core)
    implementation(libs.camera.camera2)
    // If you want to additionally use the CameraX Lifecycle library
    implementation(libs.camera.lifecycle)
    // If you want to additionally use the CameraX VideoCapture library
    implementation(libs.camera.video)
    // If you want to additionally use the CameraX View class
    implementation(libs.camera.view)
    implementation (libs.camera.extensions)

    implementation (libs.lottie)

    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-storage")

    implementation("com.github.jenly1314.WeChatQRCode:opencv:2.4.0")

    // OpenCV ABI (optional), choose the SO library architecture you want to support (at least one is required)
    implementation("com.github.jenly1314.WeChatQRCode:opencv-armv7a:2.4.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-armv64:2.4.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-x86:2.4.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-x86_64:2.4.0")

//    // OpenCV QR code recognition feature (optional)
//    implementation("com.github.jenly1314.WeChatQRCode:opencv-qrcode:2.4.0")
//
//    // OpenCV QR code scanning feature (optional)
//    implementation("com.github.jenly1314.WeChatQRCode:opencv-qrcode-scanning:2.4.0")

    // WeChat QR code recognition feature (optional)
    implementation(libs.wechat.qrcode)

    // WeChat QR code scanning feature (optional)
//    implementation("com.github.jenly1314.WeChatQRCode:wechat-qrcode-scanning:2.4.0")

    implementation (libs.glide)

    implementation (libs.retrofit)
    implementation (libs.retrofit2.converter.gson)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    // To use Kotlin Symbol Processing (KSP)
    kapt(libs.androidx.room.compiler)
    implementation(libs.room.ktx)

    // Kotlin + coroutines
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
}