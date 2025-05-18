plugins {
    `java-library`
}

dependencies {
    implementation(projects.engineCore)
    implementation(projects.enginePlatform)
    implementation(projects.engineAssets)
    implementation(projects.engineWorld)

    implementation(libs.fastutil)

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.stb)
    implementation(libs.lwjgl.nuklear)

    val lwjglNatives = System.getProperty("os.name")!!.lowercase().let { os ->
        when {
            os.contains("win") -> "natives-windows"
            os.contains("mac") || os.contains("darwin") -> "natives-macos"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "natives-linux"
            else -> throw Error("Unsupported OS: $os")
        }
    }
    testRuntimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    testRuntimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    testRuntimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    testRuntimeOnly("org.lwjgl:lwjgl-nuklear::$lwjglNatives")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.lwjgl.glfw)
}