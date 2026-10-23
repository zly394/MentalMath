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

// 修补当前 Kotlin/Compose JS 组合的原子操作生成缺陷，避免初始化死循环和状态通知丢失。
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("ExecutableKotlinJs") }.configureEach {
    doLast {
        val buildDir = project.layout.buildDirectory.get().asFile
        buildDir.walkTopDown().filter { it.name == "compose-multiplatform-core-compose-ui-ui.js" }.forEach { uiJs ->
            var content = uiJs.readText(Charsets.UTF_8)
            val start = content.indexOf("function GlobalSnapshotManager\$ensureStarted\$lambda(")
            val end = content.indexOf("var GlobalSnapshotManager_instance;", start)
            check(start >= 0 && end > start) {
                "Compose GlobalSnapshotManager changed; review the JS atomic compatibility patch"
            }
            var manager = content.substring(start, end)
            val startedField = Regex(
                "function GlobalSnapshotManager\\(\\)\\s*\\{\\s*this\\.([\\w$]+) = 0;"
            ).find(manager)?.groupValues?.get(1)
            check(startedField != null) { "Cannot locate GlobalSnapshotManager startup flag" }

            // 仅恢复丢失的 CAS 读取，保留原有通知合并、协程和帧调度流程。
            val notificationRead = Regex(
                "var ([\\w$]+);\\s*GlobalSnapshotManager_instance\\.([\\w$]+);\\s*if \\(equals\\(\\1, 0\\)\\)"
            )
            manager = notificationRead.replace(manager) { match ->
                "var ${match.groupValues[1]} = GlobalSnapshotManager_instance.${match.groupValues[2]};\n" +
                    "        if (equals(${match.groupValues[1]}, 0))"
            }
            val startupRead = Regex("var ([\\w$]+);\\s*if \\(equals\\(\\1, 0\\)\\)")
            manager = startupRead.replace(manager) { match ->
                "var ${match.groupValues[1]} = this.$startedField;\n" +
                    "      if (equals(${match.groupValues[1]}, 0))"
            }
            check(!Regex("var ([\\w$]+);\\s*(?:GlobalSnapshotManager_instance\\.[\\w$]+;\\s*)?if \\(equals\\(\\1, 0\\)\\)")
                .containsMatchIn(manager)) { "Unpatched GlobalSnapshotManager atomic read" }
            content = content.substring(0, start) + manager + content.substring(end)
            uiJs.writeText(content, Charsets.UTF_8)
        }
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
            fun hasCompareAndSetCall(method: String): Boolean {
                val calls = Regex("\\." + Regex.escape(method) + "\\s*\\(").findAll(content)
                return calls.any { call ->
                    var depth = 1
                    var commas = 0
                    var quote: Char? = null
                    var escaped = false
                    var pos = call.range.last + 1
                    while (pos < content.length && depth > 0) {
                        val char = content[pos++]
                        if (quote != null) {
                            if (escaped) escaped = false
                            else if (char == '\\') escaped = true
                            else if (char == quote) quote = null
                        } else {
                            when (char) {
                                '\'', '"' -> quote = char
                                '(', '[', '{' -> depth++
                                ')', ']', '}' -> depth--
                                ',' -> if (depth == 1) commas++
                            }
                        }
                    }
                    commas == 1
                }
            }

            val sb = StringBuilder()
            sb.append("function AtomicReference(value) {\n    this.").append(refProp).append(" = value;\n  }\n")
            for (m in refMethods) {
                val isCas = hasCompareAndSetCall(m)
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
                val isCas = hasCompareAndSetCall(m)
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
