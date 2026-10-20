plugins {
    alias(libs.plugins.ble.android.library.compose)
}

android {
    namespace = "com.danyastry.ble.core.ble"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
