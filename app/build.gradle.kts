import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assinatura de release: lida de keystore.properties (fora do git).
// Sem o arquivo, o release é assinado com a chave de debug, só para teste local.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "app.novadrive.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.novadrive.launcher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // App só em pt-BR: descarta traduções das bibliotecas para outros idiomas.
        localeFilters += listOf("pt", "pt-rBR")
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    // Imagens de marca (HR-V) ficam em branding/, fora do git (repositório público).
    // Se a pasta existir, os recursos dela substituem os genéricos de src/main/res.
    val brandingRes = rootProject.file("branding/res")
    if (brandingRes.isDirectory) {
        sourceSets.getByName("debug").res.directories.add(brandingRes.path)
        sourceSets.getByName("release").res.directories.add(brandingRes.path)
    }

    lint {
        // Sugestões de estilo KTX não mudam comportamento; o resto continua bloqueando o build.
        disable += "UseKtx"
        warningsAsErrors = false
        abortOnError = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    // org.json real na JVM (o android.jar dos testes só tem stubs).
    testImplementation(libs.org.json)
}
