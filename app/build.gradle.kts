plugins {
    alias(libs.plugins.ble.android.application)
    alias(libs.plugins.ble.android.application.compose)
}

android {
    namespace = "com.danyastry.ble"

    defaultConfig {
        applicationId = "com.danyastry.ble"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.core.ui)

    implementation(projects.feature.peripheral)
    implementation(projects.feature.scanner)
    implementation(projects.feature.explorer)
    implementation(projects.feature.throughput)
    implementation(projects.feature.connection)
    implementation(projects.feature.bonding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material3)
}
