pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Chaquopy is on Maven Central
    }
}

rootProject.name = "NuclearBoy"

include(
    ":app",
    ":common",
    ":api-deepseek",
    ":agent-core",
    ":python-bridge",
    ":memory",
    ":skills",
    ":tools-docgen",
    ":ui-chat",
    ":ui-workspace",
)
