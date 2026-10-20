import com.danyastry.ble.buildlogic.library
import com.danyastry.ble.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("ble.android.library.compose")

        dependencies {
            add("implementation", project(":core:ble"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.library("androidx-material3"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
