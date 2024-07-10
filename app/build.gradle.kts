plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// accessing the API key from local properties
import java.util.Properties

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

val mapsApiKey: String = localProperties["PLACES_API_KEY"] as String


android {
    namespace = "com.example.mygooglemapsfilterapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mygooglemapsfilterapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // making the API available elsewhere as a string resource
        // e.g. kotlin getString(R.string.google_maps_key)
        // e.g. xml "@string/google_maps_key"
        resValue("string", "google_maps_key", mapsApiKey) // 
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-stdlib:1.9.0"))
    implementation("androidx.appcompat:appcompat:1.3.1")

    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("com.google.android.gms:play-services-maps:18.0.2")
    implementation("com.google.android.libraries.places:places:3.5.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

secrets {
    propertiesFileName = "secrets.properties"

    defaultPropertiesFileName = "local.defaults.properties"

    ignoreList.add("keyToIgnore") 
    ignoreList.add("sdk.*")       
}
        