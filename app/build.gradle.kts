/* 
* Build file, along with settings.gradle and properties.gradle
* The jr toy maker or independent contributor, building the app 
*/

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// accessing the API key from local properties
import java.util.Properties

val localPropertiesFile = rootProject.file("local.properties")
val properties = Properties().apply {
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    } else {
        load(rootProject.file("secrets.properties").inputStream())
    }
}

val mapsApiKey: String = properties["PLACES_API_KEY"] as String


android {
    namespace = "com.example.mygooglemapsfilterapp"
    compileSdk = 34 // should match targetSdk

    defaultConfig {
        applicationId = "com.example.mygooglemapsfilterapp"
        minSdk = 21
        targetSdk = 34 // highest version tested app on
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // making the API available elsewhere as a string resource
        // e.g. kotlin getString(R.string.google_maps_key)
        // e.g. xml "@string/google_maps_key"
        resValue("string", "google_maps_key", mapsApiKey)
    }

    buildTypes {
        release {
            isMinifyEnabled = false // when true, shortens var names and removes unused lines
            proguardFiles( // makes your code harder to reverse engineer
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions { // what versions of JAVA the code should be compatible with
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { // also making code compatible with JAVA
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true // makes the BuildConfig class available
        viewBinding = true // makes class for each xml layout file, access views w/o findVyewById
    }
}

// aka libraries, bits of these will be imported into other files
dependencies { 
    // standards
    implementation(platform("org.jetbrains.kotlin:kotlin-stdlib:1.9.0"))
    implementation("androidx.appcompat:appcompat:1.3.1")

    // features
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("com.google.android.gms:play-services-maps:18.0.2")
    implementation("com.google.android.libraries.places:places:3.5.0")

    // testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

tasks.withType<Test> { // tells all test tasks to use JUnit
    useJUnitPlatform()
}
        