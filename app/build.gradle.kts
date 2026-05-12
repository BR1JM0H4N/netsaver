plugins {
  id("com.android.application")
}

android {

  namespace = "com.mohan.netsaver"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.mohan.netsaver"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  splits {

    abi {

      isEnable = true
      reset()

      include(
        "armeabi-v7a",
        "arm64-v8a"
      )

      isUniversalApk = false
    }
  }

buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

  compileOptions {

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    viewBinding = true
  }
}

repositories {
  google()
  mavenCentral()
}

dependencies {

  implementation("androidx.core:core:1.13.1")

  implementation("androidx.appcompat:appcompat:1.7.0")

  implementation("com.google.android.material:material:1.12.0")

  implementation("androidx.webkit:webkit:1.11.0")

  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  implementation("androidx.recyclerview:recyclerview:1.3.2")

  implementation("androidx.media3:media3-exoplayer:1.4.1")

  implementation("androidx.media3:media3-ui:1.4.1")
}