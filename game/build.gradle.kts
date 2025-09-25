plugins {
    `java-library`
}

dependencies {
    implementation(project(":engine-platform"))
    implementation(project(":engine-world"))
    implementation(project(":engine-renderer"))
    implementation(project(":engine-physics"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}