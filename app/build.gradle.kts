import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

// --- scrcpy-server: staženo a ověřeno při buildu, stejně jako v ShizuCallRecorder ---
// Verze je v libs.versions.toml a MUSÍ zůstat 4.0 - ScrcpyClient.kt parsuje binární
// protokol specifický pro tuhle verzi. Při změně verze aktualizuj i SHA256 níže.
val scrcpyVersion = libs.versions.scrcpy.get()
val scrcpyServerUrl = "https://github.com/Genymobile/scrcpy/releases/download/v$scrcpyVersion/scrcpy-server-v$scrcpyVersion"
val scrcpyServerSha256 = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"
val scrcpyServerAssetName = "scrcpy-server"
val scrcpyDownloadDir = layout.buildDirectory.dir("generated/scrcpy/assets")

abstract class DownloadAssetTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val assetName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val targetFile = outputDir.get().file(assetName.get()).asFile

        if (targetFile.exists() && calculateSha256(targetFile).equals(sha256.get(), ignoreCase = true)) {
            logger.info("${assetName.get()} je už aktuální.")
            return
        }

        targetFile.parentFile.mkdirs()
        logger.lifecycle("Stahuji ${assetName.get()}...")

        URI(url.get()).toURL().openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val actualHash = calculateSha256(targetFile)
        if (!actualHash.equals(sha256.get(), ignoreCase = true)) {
            targetFile.delete()
            throw GradleException("SHA256 nesedí! Očekáváno ${sha256.get()} ale je $actualHash")
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val downloadScrcpyServer = tasks.register<DownloadAssetTask>("downloadScrcpyServer") {
    url.set(scrcpyServerUrl)
    sha256.set(scrcpyServerSha256)
    assetName.set(scrcpyServerAssetName)
    outputDir.set(scrcpyDownloadDir)
}
// --- konec scrcpy-server bloku ---

android {
    namespace = project.property("APP_ID").toString()
    compileSdk = libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        setProperty("archivesBaseName", "phone-$versionCode")

        buildConfigField("String", "SCRCPY_VERSION", "\"$scrcpyVersion\"")
        buildConfigField("String", "SCRCPY_SERVER_SHA256", "\"$scrcpyServerSha256\"")
        buildConfigField("String", "SCRCPY_SERVER_ASSET_NAME", "\"$scrcpyServerAssetName\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        aidl = true
        viewBinding = true
        buildConfig = true
        compose = false
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val javaVersion = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(
        JvmTarget.fromTarget(libs.versions.app.build.kotlinJVMTarget.get())
    )
}

// Zaregistruje vygenerovanou scrcpy-server assets složku pro každý build variant,
// stejně jako to dělá ShizuCallRecorder.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            downloadScrcpyServer,
            DownloadAssetTask::outputDir
        )
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}


dependencies {
    implementation(project(":mtsbase-local"))
    implementation(libs.bundles.shizuku)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.ui.graphics)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.autofit.text.view)
}