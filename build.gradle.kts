import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.external.javadoc.StandardJavadocDocletOptions

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

        // Give every module a well-behaved Javadoc task. We deliberately keep
        // this lenient: the codebase is documented incrementally, so a single
        // missing comment should never fail the whole build.
        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).apply {
                encoding = "UTF-8"
                docEncoding = "UTF-8"
                charSet = "UTF-8"
                links("https://docs.oracle.com/en/java/javase/21/docs/api/")
                addStringOption("Xdoclint:none", "-quiet")
                addBooleanOption("html5", true)
            }
            isFailOnError = false
        }
    }
}

/**
 * Aggregated, browser-friendly Javadoc for the whole engine.
 *
 * Running `./gradlew aggregatedJavadoc` collects the public API of every
 * Java module into a single, cross-linked site under
 * `build/docs/aggregated-javadoc`. This is what gets published to GitHub Pages.
 */
val aggregatedJavadoc = tasks.register<Javadoc>("aggregatedJavadoc") {
    group = "documentation"
    description = "Generates a single, browsable Javadoc site for every engine module."

    setDestinationDir(layout.buildDirectory.dir("docs/aggregated-javadoc").get().asFile)
    title = "VoxelGameEngine API"

    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        windowTitle = "VoxelGameEngine API"
        docTitle = "VoxelGameEngine &mdash; Engine API Reference"
        header = "VoxelGameEngine"
        bottom = "Made with care for the VoxelGameEngine project."
        links("https://docs.oracle.com/en/java/javase/21/docs/api/")
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
        // A hand-written landing page that greets people arriving at the docs.
        overview = layout.projectDirectory.file("docs/overview.html").asFile.absolutePath
        // Group the packages into human-friendly buckets in the left sidebar.
        group("Core & Math", "de.heger.voxelengine.core*")
        group("Platform & Windowing", "de.heger.voxelengine.platform*")
        group("Rendering", "de.heger.voxelengine.renderer*")
        group("World & Terrain", "de.heger.voxelengine.world*")
        group("Physics", "de.heger.voxelengine.physics*")
        group("Assets", "de.heger.voxelengine.assets*")
        group("Game & Launcher", "de.heger.voxelengine.game*", "de.heger.voxelengine.launcher*")
    }

    isFailOnError = false
}

// Feed every Java module's main source set into the aggregated Javadoc once each
// subproject has been evaluated (so its source sets actually exist).
subprojects {
    val subproject = this
    plugins.withType<org.gradle.api.plugins.JavaPlugin>().configureEach {
        val main = subproject.extensions
            .getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
            .getByName("main")
        aggregatedJavadoc.configure {
            dependsOn("${subproject.path}:classes")
            source(main.allJava)
            classpath += main.compileClasspath + main.output
        }
    }
}