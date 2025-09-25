plugins {
    `java-library`
}

dependencies {
    api(projects.engineCore)
    api(projects.engineWorld)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}