plugins { id("com.android.application") }

android {
    namespace = "io.github.vhukze.sogouskin"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.vhukze.sogouskin"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
