package com.danyastry.ble

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.danyastry.ble.catalog.FeatureCatalog
import com.danyastry.ble.catalog.FeatureRegistry
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.navigation.BleNavHost

class MainActivity : ComponentActivity() {

    private val catalog = FeatureCatalog(FeatureRegistry.features, deviceSdk = Build.VERSION.SDK_INT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BleTheme {
                BleNavHost(catalog)
            }
        }
    }
}
