package com.zly.mentalmath

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        val body = document.body ?: return@onWasmReady
        ComposeViewport(body) {
            var cnFontFamily by remember { mutableStateOf<FontFamily?>(null) }

            LaunchedEffect(Unit) {
                try {
                    val response = window.fetch("app_cn.ttf").await()
                    if (response.ok) {
                        val buffer: ArrayBuffer = response.arrayBuffer().await()
                        val i8 = Int8Array(buffer)
                        val bytes = ByteArray(i8.length) { idx -> i8.asDynamic()[idx] as Byte }
                        cnFontFamily = FontFamily(
                            Font(identity = "MentalMathCNRegular", data = bytes, weight = FontWeight.Normal),
                            Font(identity = "MentalMathCNMedium", data = bytes, weight = FontWeight.Medium),
                            Font(identity = "MentalMathCNSemiBold", data = bytes, weight = FontWeight.SemiBold),
                            Font(identity = "MentalMathCNBold", data = bytes, weight = FontWeight.Bold),
                            Font(identity = "MentalMathCNExtraBold", data = bytes, weight = FontWeight.ExtraBold)
                        )
                    }
                } catch (_: Throwable) {
                }
            }

            if (cnFontFamily != null) {
                App(customFontFamily = cnFontFamily)
            }
        }
    }
}
