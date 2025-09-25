version = "0.0.1-SNAPSHOT"

plugins {
    application
}

dependencies {
    implementation(projects.engineCore)
    implementation(projects.enginePlatform)
    implementation(projects.engineRenderer)
    implementation(projects.engineWorld)
    implementation(projects.enginePhysics)
    // implementation(projects.engineAssets)
    implementation(projects.game)

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.lwjgl.core)
    implementation(libs.lwjgl.stb)
    // implementation(libs.lwjgl.openal)

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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
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
