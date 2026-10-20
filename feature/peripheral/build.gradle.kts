plugins {
    alias(libs.plugins.ble.android.feature)
}

android {
    namespace = "com.danyastry.ble.feature.peripheral"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
