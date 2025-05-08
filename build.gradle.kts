import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

subprojects {
    plugins.withType<org.gradle.api.plugins.JavaBasePlugin>().configureEach {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}