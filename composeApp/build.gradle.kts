import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

tasks.matching { it.name == "wasmJsBrowserDistribution" }.configureEach {
    doLast {
        val distribution = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile
        val assets = distribution.walkTopDown().filter {
            it.isFile && it.name != "sw.js" && it.name != "pwa-assets.json" && it.extension != "map"
        }.sortedBy { it.relativeTo(distribution).invariantSeparatorsPath }.toList()
        val digest = MessageDigest.getInstance("SHA-256")
        val serviceWorker = file("src/wasmJsMain/resources/sw.js").readText()
        digest.update(serviceWorker.toByteArray())
        assets.forEach { file ->
            digest.update(file.relativeTo(distribution).invariantSeparatorsPath.toByteArray())
            digest.update(file.readBytes())
        }
        val version = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        distribution.resolve("pwa-assets.json").writeText(
            assets.joinToString(prefix = "[", postfix = "]") {
                "\"./${it.relativeTo(distribution).invariantSeparatorsPath}\""
            }
        )
        distribution.resolve("sw.js").writeText(
            serviceWorker.replace("__BUILD_VERSION__", version)
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.zly.mentalmath.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "mentalmath.js"
            }
        }
        binaries.executable()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.vosk.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain {
            resources.srcDir("src/androidMain/assets/tts")
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
