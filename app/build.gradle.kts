plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.yn.logcatlivewallpaper"
  compileSdk {
    version = release(36) {
      minorApiLevel = 1
    }
  }

  defaultConfig {
    applicationId = "com.yn.logcatlivewallpaper"
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
    multiDexEnabled = true
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  // libsu
  implementation(libs.libsu.core)
  implementation(libs.libsu.service)
  implementation(libs.libsu.nio)
  // shizuku
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)

  testImplementation(libs.junit)
}
