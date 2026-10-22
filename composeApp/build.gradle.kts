import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "mentalmath.js"
            }
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.vosk.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        val jsMain by getting {
            resources.srcDir("src/androidMain/assets/tts")
        }
    }
}

android {
    namespace = "com.zly.mentalmath"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zly.mentalmath"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    androidResources {
        noCompress += listOf("pcm", "pak", "mdl", "fst", "int", "conf", "dubm", "ie", "mat", "stats", "uuid")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

tasks.matching { it.name == "jsTestPackageJson" }.configureEach {
    doLast {
        val pkgFile = rootProject.layout.buildDirectory
            .file("js/packages/${rootProject.name}-composeApp-test/package.json")
            .get().asFile
        if (pkgFile.exists()) {
            pkgFile.writeText(pkgFile.readText().replace("github:Kotlin/karma#6.4.5", "6.4.4"))
        }
    }
}

// 自动化修补 Kotlin 2.x JS 编译器对 Compose Runtime 内部 AtomicReference/AtomicInt 的 Lowering 缺陷
tasks.matching { it.name.startsWith("jsBrowser") }.configureEach {
    doLast {
        val buildDir = project.layout.buildDirectory.get().asFile
        buildDir.walkTopDown().filter { it.name == "mentalmath.js" }.forEach { jsFile ->
            var content = jsFile.readText(Charsets.UTF_8)
            var modified = false
            if (content.contains("on(Gd).cy=function(){}")) {
                content = content.replace("on(Gd).cy=function(){}", "on(Gd).cy=function(){return this.n24_1}")
                content = content.replace("on(Vd).cy=function(){}", "on(Vd).cy=function(){return this.p1m_1}")
                content = content.replace("jn(void 0,t)?(this.n24_1=n,i=!0)", "jn(this.n24_1,t)?(this.n24_1=n,i=!0)")
                content = content.replace("jn(void 0,t)?(this.p1m_1=n,i=!0)", "jn(this.p1m_1,t)?(this.p1m_1=n,i=!0)")
                content = content.replace("on(Gd).o24=function(t){this.n24_1=t}", "on(Gd).o24=function(t){var o=this.n24_1;this.n24_1=t;return o}")
                content = content.replace("this.p1m_1=void 0+t|0", "this.p1m_1=this.p1m_1+t|0")
                modified = true
            }
            if (modified) {
                jsFile.writeText(content, Charsets.UTF_8)
                println(">>> [MentalMath Patch] Successfully auto-patched AtomicFU in ${jsFile.relativeTo(projectDir)}")
            }
        }
    }
}
