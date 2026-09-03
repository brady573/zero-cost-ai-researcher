plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.zerocost.researcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zerocost.researcher"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "inference"
    productFlavors {
        create("stub") {
            dimension = "inference"
            applicationIdSuffix = ".stub"
            versionNameSuffix = "-stub"
        }
        create("llama") {
            dimension = "inference"
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    if (rootProject.findProject(":llamaAndroid") != null) {
        add("llamaImplementation", project(":llamaAndroid"))
    }

    testImplementation(libs.junit)
}

androidComponents {
    val hasLlama = rootProject.findProject(":llamaAndroid") != null
    beforeVariants(selector().withFlavor("inference" to "llama")) { builder ->
        if (!hasLlama) builder.enable = false
    }
}


ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
