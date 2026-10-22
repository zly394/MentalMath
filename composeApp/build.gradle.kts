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
            
            // 1. 动态提取属性名 (refProp, intProp)
            val refPropMatch = Regex("this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;\\s*\\}\\s*protoOf\\(AtomicReference\\)").find(content)
                ?: Regex("function AtomicReference\\(value\\)\\s*\\{\\s*this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;").find(content)
            val intPropMatch = Regex("function AtomicInt\\(value\\)\\s*\\{\\s*this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;").find(content)
                ?: Regex("this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*value;\\s*\\}\\s*protoOf\\(AtomicInt\\)").find(content)
                
            val refProp = refPropMatch?.groupValues?.get(1) ?: "n24_1"
            val intProp = intPropMatch?.groupValues?.get(1) ?: "p1m_1"

            // 2. 收集整个运行时中在 Atomic 实例上被调用的所有方法
            val refFields = Regex("this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*new AtomicReference").findAll(content).map { it.groupValues[1] }.toSet()
            val refMethods = mutableSetOf<String>()
            for (f in refFields) {
                Regex("\\b" + Regex.escape(f) + "\\.([a-zA-Z0-9_" + d + "]+)\\s*\\(").findAll(content).forEach {
                    refMethods.add(it.groupValues[1])
                }
            }

            val intFields = Regex("this\\.([a-zA-Z0-9_" + d + "]+)\\s*=\\s*new AtomicInt").findAll(content).map { it.groupValues[1] }.toSet()
            val intMethods = mutableSetOf<String>()
            for (f in intFields) {
                Regex("\\b" + Regex.escape(f) + "\\.([a-zA-Z0-9_" + d + "]+)\\s*\\(").findAll(content).forEach {
                    intMethods.add(it.groupValues[1])
                }
            }
            Regex("protoOf\\(AtomicInt\\)\\.([a-zA-Z0-9_" + d + "]+)\\s*=").findAll(content).forEach {
                intMethods.add(it.groupValues[1])
            }

            // 3. 构建无缺陷的纯正 JS 类实现
            val sb = StringBuilder()
            sb.append("function AtomicReference(value) {\n    this.").append(refProp).append(" = value;\n  }\n")
            for (m in refMethods) {
                val isCas = Regex("\\." + Regex.escape(m) + "\\s*\\([^,()]+,[^,()]+\\)").containsMatchIn(content)
                val isZeroArg = Regex("\\." + Regex.escape(m) + "\\s*\\(\\s*\\)").containsMatchIn(content)
                if (isZeroArg) {
                    sb.append("  protoOf(AtomicReference).").append(m).append(" = function () { return this.").append(refProp).append("; };\n")
                } else if (isCas) {
                    sb.append("  protoOf(AtomicReference).").append(m).append(" = function (expect, newValue) { if (equals(this.").append(refProp).append(", expect)) { this.").append(refProp).append(" = newValue; return true; } return false; };\n")
                } else {
                    sb.append("  protoOf(AtomicReference).").append(m).append(" = function (value) { var oldValue = this.").append(refProp).append("; this.").append(refProp).append(" = value; return oldValue; };\n")
                }
            }

            sb.append("  function AtomicInt(value) {\n    this.").append(intProp).append(" = value;\n  }\n")
            for (m in intMethods) {
                val isCas = Regex("\\." + Regex.escape(m) + "\\s*\\([^,()]+,[^,()]+\\)").containsMatchIn(content)
                val isZeroArg = Regex("\\." + Regex.escape(m) + "\\s*\\(\\s*\\)").containsMatchIn(content)
                val isAmount = content.contains("protoOf(AtomicInt)." + m + " = function (amount)") || Regex("\\." + Regex.escape(m) + "\\s*\\(\\s*amount\\s*\\)").containsMatchIn(content)
                if (isZeroArg) {
                    sb.append("  protoOf(AtomicInt).").append(m).append(" = function () { return this.").append(intProp).append("; };\n")
                } else if (isCas) {
                    sb.append("  protoOf(AtomicInt).").append(m).append(" = function (expect, newValue) { if (equals(this.").append(intProp).append(", expect)) { this.").append(intProp).append(" = newValue; return true; } return false; };\n")
                } else if (isAmount) {
                    sb.append("  protoOf(AtomicInt).").append(m).append(" = function (amount) { this.").append(intProp).append(" = this.").append(intProp).append(" + amount | 0; return this.").append(intProp).append("; };\n")
                } else {
                    sb.append("  protoOf(AtomicInt).").append(m).append(" = function (value) { var oldValue = this.").append(intProp).append("; this.").append(intProp).append(" = value; return oldValue; };\n")
                }
            }

            val startIdx = content.indexOf("function AtomicReference(value) {")
            val endIdx = content.indexOf("function trackWrite(", startIdx)
            if (startIdx != -1 && endIdx != -1) {
                content = content.substring(0, startIdx) + sb.toString() + "  " + content.substring(endIdx)
                runtimeJs.writeText(content, Charsets.UTF_8)
                println(">>> [MentalMath Patch] Successfully injected clean Atomic classes (refProp=" + refProp + ", intProp=" + intProp + ", refMethods=" + refMethods + ", intMethods=" + intMethods + ")")
            }
        }
    }
}
