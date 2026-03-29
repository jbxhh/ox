import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
}

android {
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.monfluo.wallet.beta"
        minSdk = 26
        targetSdk = 36
        compileSdk = 36
        versionCode = 90500
        versionName = "0.9.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    val buildAll = project.hasProperty("monfluo-all")
    val buildArm32 = project.hasProperty("monfluo-arm32")
    val buildArm64 = project.hasProperty("monfluo-arm64")
    val buildX8664 = project.hasProperty("monfluo-x86_64")
    val buildUniversal = project.hasProperty("monfluo-universal")

    splits {
        abi {
            isEnable = true
            reset()
            isUniversalApk = buildUniversal
            when {
                buildAll -> include("armeabi-v7a", "arm64-v8a", "x86_64")
                buildArm32 -> include("armeabi-v7a")
                buildArm64 -> include("arm64-v8a")
                buildX8664 -> include("x86_64")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    namespace = "org.monfluo.wallet"
    base.archivesName = "monfluo"
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 4)
        variant.outputs.forEach { output ->
            val baseVersionCode = output.versionCode.get()
            val abiCode =
                output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
                    ?.let { abiCodes[it] }
                    ?: 0
            output.versionCode.set(abiCode + 10 * baseVersionCode)
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

dependencies {
    // Android stuff
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("com.google.android.material:material:1.13.0")
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    // Slide to Send tx sliders
    implementation("com.ncorti:slidetoact:0.11.0")
    // QR Code stuff
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
}
