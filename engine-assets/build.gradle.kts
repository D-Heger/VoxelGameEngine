plugins {
    `java-library`
}

dependencies {
    implementation(projects.engineCore)
    // Add LWJGL STB dependency
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.stb)

    // Add natives for runtime execution (needed for STB)
    val lwjglNatives = System.getProperty("os.name")!!.lowercase().let { os ->
        when {
            os.contains("win") -> "natives-windows"
            os.contains("mac") || os.contains("darwin") -> "natives-macos"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "natives-linux"
            else -> throw Error("Unsupported OS: $os")
        }
    }
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")


    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}