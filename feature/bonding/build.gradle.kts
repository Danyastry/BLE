plugins {
    alias(libs.plugins.ble.android.feature)
}

android {
    namespace = "com.danyastry.ble.feature.bonding"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
