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
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("ExecutableKotlinJs") }.configureEach {
    doLast {
        val buildDir = project.layout.buildDirectory.get().asFile
        buildDir.walkTopDown().filter { it.name == "compose-multiplatform-core-compose-runtime-runtime.js" }.forEach { runtimeJs ->
            var content = runtimeJs.readText(Charsets.UTF_8)
            val d = "$"
            
            // 1. 动态提取 AtomicReference 属性名 (例如 this.r25_1 = value)
            val refPropRegex = Regex("function AtomicReference\\(value\\)\\s*\\{\\s*this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;")
            val refMatch = refPropRegex.find(content)
            if (refMatch != null) {
                val refProp = refMatch.groupValues[1]
                // 修补 AtomicReference.getValue
                content = content.replace(
                    Regex("protoOf\\(AtomicReference\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(\\)\\s*\\{[\\s\\S]*?return\\s+tmp" + "\\" + d + "ret" + "\\" + d + "1;\\s*\\};"),
                    "protoOf(AtomicReference).$1 = function () { return this.$refProp; };"
                )
                // 修补 AtomicReference.getAndSet
                content = content.replace(
                    Regex("protoOf\\(AtomicReference\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(value\\)\\s*\\{[\\s\\S]*?var oldValue\\s*=[\\s\\S]*?return oldValue;\\s*\\};"),
                    "protoOf(AtomicReference).$1 = function (value) { var oldValue = this.$refProp; this.$refProp = value; return oldValue; };"
                )
                // 修补 AtomicReference.compareAndSet
                content = content.replace(
                    Regex("protoOf\\(AtomicReference\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(expect,\\s*newValue\\)\\s*\\{[\\s\\S]*?\\n\\s*\\};"),
                    "protoOf(AtomicReference).$1 = function (expect, newValue) { if (equals(this.$refProp, expect)) { this.$refProp = newValue; return true; } return false; };"
                )
            }

            // 2. 动态提取 AtomicInt 属性名 (例如 this.t1n_1 = value)
            val intPropRegex = Regex("function AtomicInt\\(value\\)\\s*\\{\\s*this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;")
            val intMatch = intPropRegex.find(content)
            if (intMatch != null) {
                val intProp = intMatch.groupValues[1]
                // 修补 AtomicInt.getValue
                content = content.replace(
                    Regex("protoOf\\(AtomicInt\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(\\)\\s*\\{[\\s\\S]*?return\\s+tmp" + "\\" + d + "ret" + "\\" + d + "1;\\s*\\};"),
                    "protoOf(AtomicInt).$1 = function () { return this.$intProp; };"
                )
                // 修补 AtomicInt.addAndGet
                content = content.replace(
                    Regex("protoOf\\(AtomicInt\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(amount\\)\\s*\\{[\\s\\S]*?return\\s+tmp" + "\\" + d + "ret" + "\\" + d + "3;\\s*\\};"),
                    "protoOf(AtomicInt).$1 = function (amount) { this.$intProp = this.$intProp + amount | 0; return this.$intProp; };"
                )
                // 修补 AtomicInt.compareAndSet
                content = content.replace(
                    Regex("protoOf\\(AtomicInt\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*function\\s*\\(expect,\\s*newValue\\)\\s*\\{[\\s\\S]*?\\n\\s*\\};"),
                    "protoOf(AtomicInt).$1 = function (expect, newValue) { if (equals(this.$intProp, expect)) { this.$intProp = newValue; return true; } return false; };"
                )
            }

            runtimeJs.writeText(content, Charsets.UTF_8)
            println(">>> [MentalMath Patch] Successfully pre-patched runtime.js (refProp=${refMatch?.groupValues?.get(1)}, intProp=${intMatch?.groupValues?.get(1)})")
        }
    }
}
