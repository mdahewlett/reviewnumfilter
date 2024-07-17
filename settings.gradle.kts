/* 
* Build file, along with build.gradle.kts
* Project manager to build.gradle's, 
* telling it what it can use to build the toy 
*/

rootProject.name = "reviewnumfilter"

// What modules to build together
include("app")

// What repos to get plugins from
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// What plugs to get, some also in build.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" // // allow automatic download of JDKs
}

// What repos to get dependencies from
dependencyResolutionManagement {

    // Only get dependencies from repos listed
    // Stops modules pulling different versions of dependencies, or from sketchy repos
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}