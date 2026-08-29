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
    // La cle d'API des juges (spec 012). Elle ne doit JAMAIS entrer dans le
    // depot : les deux depots ClimbContest sont publics, et `gradle.properties`
    // est suivi par git. On la lit donc a la compilation, depuis :
    //
    //   1. la ligne de commande     ./gradlew assembleRelease -PreleaseApiKey=...
    //   2. l'environnement          CLIMBCONTEST_API_KEY=...  (pour la CI)
    //   3. ~/.gradle/gradle.properties, hors du depot
    //
    // ⚠️ Une cle compilee dans un APK distribue publiquement s'extrait en
    // quelques minutes. Elle arrete un robot qui balaie Internet, pas quelqu'un
    // qui a l'application et veut fausser la competition. Le choix est
    // documente dans specs/012-cle-api-juges/spec.md.
    fun trouverCle(propriete: String): String? =
        (project.findProperty(propriete) as String?)?.takeIf { it.isNotBlank() }
            ?: System.getenv("CLIMBCONTEST_API_KEY")?.takeIf { it.isNotBlank() }

    // Le debogage a une valeur par defaut, celle du serveur de developpement :
    // `installDebug` doit marcher sans rien configurer. Sinon la premiere chose
    // que ferait un developpeur presse serait de poser la vraie cle dans un
    // fichier commite.
    val cleDebug = trouverCle("apiKey") ?: "dev"
    val cleRelease = trouverCle("releaseApiKey") ?: ""

    // Le release, lui, n'a pas de defaut : un APK sans cle serait refuse par le
    // serveur, et le decouvrir le jour de la competition serait le pire moment.
    //
    // La verification passe par le graphe de taches et NON par un `require()`
    // dans le bloc `release { }` : ce bloc est evalue a la CONFIGURATION de
    // Gradle pour n'importe quelle tache, donc un `require` y ferait echouer
    // `installDebug`. Le meme piege que sur `serverUrl`, deja documente
    // ci-dessous.
    gradle.taskGraph.whenReady {
        val faitUnRelease = allTasks.any {
            it.name.contains("Release") && it.project.path == project.path
        }
        if (faitUnRelease && cleRelease.isBlank()) {
            throw GradleException(
                "Cle d'API manquante pour un build release. Relance avec " +
                "-PreleaseApiKey=... ou pose CLIMBCONTEST_API_KEY dans " +
                "l'environnement. Ne la mets JAMAIS dans gradle.properties : " +
                "ce fichier est suivi par git et le depot est public."
            )
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 : la machine hote vue depuis l'emulateur Android.
            // HTTP en clair, autorise uniquement en debug par
            // src/debug/res/xml/network_security_config.xml.
            buildConfigField("String", "SERVER_URL",
                "\"${project.findProperty("serverUrl") ?: "http://10.0.2.2:5007"}\"")
            buildConfigField("String", "API_KEY", "\"$cleDebug\"")
        }
        release {
            // Le release lit `releaseServerUrl`, PAS `serverUrl`. Deux raisons,
            // et la seconde a ete trouvee a la relecture :
            //
            // 1. findProperty lit la ligne de commande MAIS AUSSI
            //    gradle.properties, ~/.gradle/gradle.properties et les reglages
            //    de l'IDE. Un « serverUrl=http://10.0.2.2:5007 » pose un jour
            //    pour se simplifier les tests produirait, trois semaines plus
            //    tard, un APK Play Store pointant sur la machine de dev. Avec
            //    deux noms distincts, c'est structurellement impossible.
            //
            // 2. Ce bloc est evalue a la CONFIGURATION de Gradle, pour
            //    n'importe quelle tache. Un `require()` portant sur `serverUrl`
            //    faisait donc echouer `installDebug -PserverUrl=http://...` --
            //    la commande meme que docs/tester-avec-l-emulateur.md
            //    recommande pour tester depuis un telephone du wifi.
            val urlRelease = (project.findProperty("releaseServerUrl")
                ?: "https://climbcontestserver.onrender.com").toString()
            require(urlRelease.startsWith("https://")) {
                "L'adresse d'un build release doit etre en HTTPS, obtenu : $urlRelease"
            }
            buildConfigField("String", "SERVER_URL", "\"$urlRelease\"")
            buildConfigField("String", "API_KEY", "\"$cleRelease\"")
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
        buildConfig = true          // pour SERVER_URL et API_KEY
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
