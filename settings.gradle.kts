pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "AsterionWriting"
include(":app")
val existingArtBrain = file("art-upstream/brain")
if (existingArtBrain.isDirectory) {
    include(":brain")
    project(":brain").projectDir = existingArtBrain
}
