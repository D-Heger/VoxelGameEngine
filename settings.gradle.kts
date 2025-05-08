rootProject.name = "VoxelGameEngine"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "engine-core",
    "engine-platform",
    "engine-renderer",
    "engine-world",
    "engine-physics",
    "engine-assets",
    "game",
    "launcher"
)
