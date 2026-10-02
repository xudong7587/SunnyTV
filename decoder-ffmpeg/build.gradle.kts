plugins { id("com.android.library") }
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 35
    ndkVersion = "26.1.10909125"
    defaultConfig {
        minSdk = 24
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    externalNativeBuild { cmake { path = file("src/main/jni/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api("androidx.media3:media3-decoder:1.9.4")
    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.annotation:annotation:1.9.1")
    compileOnly("org.checkerframework:checker-qual:3.33.0")
}
