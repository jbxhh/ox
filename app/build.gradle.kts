import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

plugins {
    id("com.android.application")
}

abstract class RustlsPlatformVerifierVersion : ValueSource<String, RustlsPlatformVerifierVersion.Params> {
    interface Params : ValueSourceParameters {
        val lockFile: RegularFileProperty
    }

    override fun obtain(): String {
        val lockFile = parameters.lockFile.get().asFile
        val lines = lockFile.readLines()
        val nameIndex = lines.indexOfFirst { it.trim() == "name = \"rustls-platform-verifier-android\"" }
        check(nameIndex >= 0) { "Failed to find rustls-platform-verifier-android in $lockFile" }
        return lines
            .drop(nameIndex + 1)
            .first { it.trim().startsWith("version = ") }
            .trim()
            .removePrefix("version = ")
            .trim('"')
    }
}

val rustlsPlatformVerifierVersion =
    providers.of(RustlsPlatformVerifierVersion::class.java) {
        parameters.lockFile.set(rootProject.layout.projectDirectory.file("monfluo-oxide-jni/Cargo.lock"))
    }

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.rustls" && requested.name == "rustls-platform-verifier") {
            useVersion(rustlsPlatformVerifierVersion.get())
        }
    }
}

android {
    defaultConfig {
        applicationId = "org.monfluo.wallet.android"
        minSdk = 26
        targetSdk = 36
        compileSdk = 36
        versionCode = 1001
        versionName = "0.10.1-ALPHA-SNAPSHOT"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    lintOptions {
        disable("MissingTranslation")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    namespace = "org.monfluo.wallet.android"
    base.archivesName = "monfluo-${android.defaultConfig.versionName?.replace('.', 'x') ?: "unknown"}"
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
    // Oxide
    implementation(project(":monfluo-oxide-kotlin"))
    // Android component required by rustls-platform-verifier, its version is resolved from the Cargo lockfile
    implementation("org.rustls:rustls-platform-verifier")
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    // Slide to Send tx sliders
    implementation("com.ncorti:slidetoact:0.11.0")
    // QR Code stuff
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
