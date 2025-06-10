plugins {
    `java-library`
}

dependencies {
    implementation(project(":engine-platform"))
    implementation(project(":engine-world"))
    implementation(project(":engine-renderer"))
    implementation(project(":engine-physics"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}