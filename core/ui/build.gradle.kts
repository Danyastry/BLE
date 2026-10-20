plugins {
    alias(libs.plugins.ble.android.library.compose)
}

android {
    namespace = "com.danyastry.ble.core.ui"
}

dependencies {
    api(libs.androidx.material3)
}
