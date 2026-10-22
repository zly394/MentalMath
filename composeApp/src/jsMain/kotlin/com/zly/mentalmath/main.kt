package com.zly.mentalmath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        val body = document.body ?: return@onWasmReady
        ComposeViewport(body) {
            var cnFontFamily by remember { mutableStateOf<FontFamily?>(null) }
            var isReady by remember { mutableStateOf(false) }
            val fontFamilyResolver = LocalFontFamilyResolver.current

            LaunchedEffect(Unit) {
                withTimeoutOrNull(2000L) {
                    try {
                        val response = window.fetch("app_cn.ttf").await()
                        if (response.ok) {
                            val buffer: ArrayBuffer = response.arrayBuffer().await()
                            val bytes = Int8Array(buffer).unsafeCast<ByteArray>()
                            val family = FontFamily(
                                Font(identity = "MentalMathCNRegular", data = bytes, weight = FontWeight.Normal),
                                Font(identity = "MentalMathCNMedium", data = bytes, weight = FontWeight.Medium),
                                Font(identity = "MentalMathCNSemiBold", data = bytes, weight = FontWeight.SemiBold),
                                Font(identity = "MentalMathCNBold", data = bytes, weight = FontWeight.Bold),
                                Font(identity = "MentalMathCNExtraBold", data = bytes, weight = FontWeight.ExtraBold)
                            )
                            fontFamilyResolver.preload(family)
                            cnFontFamily = family
                        }
                    } catch (e: Throwable) {
                        console.error("Failed to load app_cn.ttf:", e)
                    }
                }
                isReady = true
            }

            if (isReady) {
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF1D4ED8),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
