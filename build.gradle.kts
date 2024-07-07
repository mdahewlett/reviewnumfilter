plugins {
    id("com.android.application") version "8.1.1" apply false
    kotlin("android") version "1.8.0" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
        classpath("com.android.tools.build:gradle:8.1.1")
    }
}