package com.zly.mentalmath

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import com.zly.mentalmath.speech.SpeechDiagnostics
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView

private fun logFontError(message: String): Unit = js("console.error(message)")
private fun recordSpeechDiagnostic(event: String, detail: String): Unit =
    js("globalThis.mentalMathDiagnostics?.record(event, detail)")
private fun logFontReady(): Unit = js("console.log('>>> [MentalMath] Custom font successfully preloaded!')")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    SpeechDiagnostics.listener = ::recordSpeechDiagnostic
    val body = document.body ?: error("Document body is unavailable")
    ComposeViewport(body) {
        LaunchedEffect(Unit) {
            document.getElementById("boot-status")?.remove()
        }
        var cnFontFamily by remember { mutableStateOf<FontFamily?>(null) }
        val fontFamilyResolver = LocalFontFamilyResolver.current

        LaunchedEffect(Unit) {
            try {
                val response = window.fetch("app_cn.ttf").await()
                check(response.ok) { "app_cn.ttf: HTTP ${response.status}" }
                val buffer: ArrayBuffer = response.arrayBuffer().await()
                val view = DataView(buffer)
                val bytes = ByteArray(buffer.byteLength) { idx -> view.getInt8(idx) }
                val family = FontFamily(
                    Font(identity = "MentalMathCNRegular", data = bytes, weight = FontWeight.Normal),
                    Font(identity = "MentalMathCNMedium", data = bytes, weight = FontWeight.Medium),
                    Font(identity = "MentalMathCNSemiBold", data = bytes, weight = FontWeight.SemiBold),
                    Font(identity = "MentalMathCNBold", data = bytes, weight = FontWeight.Bold),
                    Font(identity = "MentalMathCNExtraBold", data = bytes, weight = FontWeight.ExtraBold)
                )
                fontFamilyResolver.preload(family)
                cnFontFamily = family
                logFontReady()
            } catch (e: Exception) {
                logFontError("Failed to load app_cn.ttf: ${e.message}")
            }
        }

        val family = cnFontFamily
        if (family != null) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family)
            ) {
                App(customFontFamily = family)
            }
        } else {
            App(customFontFamily = null)
        }
    }
}
