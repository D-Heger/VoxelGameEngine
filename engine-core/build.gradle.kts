plugins {
    `java-library`
}

dependencies {
    api(libs.joml)
    implementation(libs.fastutil)
    implementation(libs.bundles.logging)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
