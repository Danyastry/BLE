import com.android.build.api.dsl.LibraryExtension
import com.danyastry.ble.buildlogic.BuildConfig
import com.danyastry.ble.buildlogic.configureKotlinAndroid
import com.danyastry.ble.buildlogic.library
import com.danyastry.ble.buildlogic.libs
import com.danyastry.ble.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply(libs.pluginId("kotlin-android"))

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            resourcePrefix = path.substringAfterLast(':') + "_"
            testOptions.targetSdk = BuildConfig.TARGET_SDK
            lint.targetSdk = BuildConfig.TARGET_SDK
        }

        dependencies {
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("androidTestImplementation", libs.library("androidx-test-runner"))
            add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.library("truth"))
        }
    }
}
