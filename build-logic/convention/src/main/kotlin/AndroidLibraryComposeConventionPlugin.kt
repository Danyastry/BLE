import com.android.build.api.dsl.LibraryExtension
import com.danyastry.ble.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("ble.android.library")
        configureAndroidCompose(extensions.getByType<LibraryExtension>())
    }
}
