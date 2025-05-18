import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

version = "0.0.1-SNAPSHOT"

plugins {
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(projects.engineCore)
    implementation(projects.enginePlatform)
    implementation(projects.engineRenderer)
    implementation(projects.engineWorld)
    // implementation(projects.enginePhysics)
    // implementation(projects.engineAssets)

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.lwjgl.core)
    implementation(libs.lwjgl.stb)
    // implementation(libs.lwjgl.openal)
    implementation(libs.lwjgl.nuklear)

    val lwjglNatives = System.getProperty("os.name")!!.lowercase().let { os ->
        when {
            os.contains("win") -> "natives-windows"
            os.contains("mac") || os.contains("darwin") -> "natives-macos"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "natives-linux"
            else -> throw Error("Unsupported OS: $os")
        }
    }

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    // runtimeOnly("org.lwjgl:lwjgl-jemalloc::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-nuklear::$lwjglNatives")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

application {
    mainClass.set("de.heger.voxelengine.launcher.Main")
}

tasks.named<JavaExec>("run") {
     jvmArgs("-Xmx2G")
     // enableAssertions = true
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to "VoxelGameEngine",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "D. Heger",
        )
    }
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("VoxelGameEngine-fat") // More descriptive name
    archiveClassifier.set("") // Avoid classifier like 'all'
    archiveVersion.set(project.version.toString())
    manifest {
        attributes(
            "Implementation-Title" to "VoxelGameEngine",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "D. Heger",
            "Main-Class" to application.mainClass.get()
        )
    }
    // It's often good practice to merge service files if libraries use ServiceLoader
    mergeServiceFiles()
    // You might need to exclude signature files if you encounter issues
    // exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
