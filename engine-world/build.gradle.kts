plugins {
    `java-library`
}

dependencies {
    api(libs.joml)
    implementation(projects.engineCore)
    implementation(libs.fastutil)
    implementation(libs.jnoise)

    // Jackson for JSON parsing
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
}