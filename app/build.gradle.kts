plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.compose)
}

android {
    namespace = "com.adn.dev.climbcontest"
    compileSdk = 36 // For Android 15 Beta. Use 34 for the latest stable release.

    defaultConfig {
        applicationId = "com.adn.dev.climbcontest"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "3.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Adresse du backend, choisie A LA COMPILATION mais surchargeable en ligne de
    // commande -- pas de constante a editer dans le code source.
    //
    //   ./gradlew installDebug                                    -> backend local (emulateur)
    //   ./gradlew installDebug -PserverUrl=https://climbcontest.adn-dev.fr
    //   ./gradlew assembleRelease                                 -> production
    buildTypes {
        debug {
            // 10.0.2.2 : la machine hote vue depuis l'emulateur Android.
            // HTTP en clair, autorise uniquement en debug par
            // src/debug/res/xml/network_security_config.xml.
            buildConfigField("String", "SERVER_URL",
                "\"${project.findProperty("serverUrl") ?: "http://10.0.2.2:5007"}\"")
        }
        release {
            buildConfigField("String", "SERVER_URL",
                "\"${project.findProperty("serverUrl") ?: "https://climbcontestserver.onrender.com"}\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // FIX: This is the correct modern DSL for setting the Kotlin JVM target.
    // The `jvmTarget` property has been replaced by the `jvmToolchain` function.
    kotlin {
        jvmToolchain(8)
    }

    buildFeatures {
        compose = true
        buildConfig = true          // pour SERVER_URL
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.service.base)
    implementation(libs.barcode.scanning)
    implementation(libs.okhttp)
    implementation(libs.androidx.compose.material.icons.extended.android)
    testImplementation(libs.junit)
    // Serveur factice : permet de tester la couche reseau sur la JVM,
    // sans emulateur. Voir ClimbContestApiTest.
    testImplementation(libs.mockwebserver)
    // org.json est stubbe dans l'Android SDK : la JVM a besoin d'une vraie
    // implementation, sinon chaque appel leve « not mocked ».
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
