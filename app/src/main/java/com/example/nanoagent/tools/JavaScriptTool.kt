package com.example.nanoagent.tools

import android.content.Context
import android.webkit.WebView
import com.example.nanoagent.agent.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class JavaScriptTool(private val context: Context) : Tool {
    override val name = "runJavaScript"
    override val description = "Executes JavaScript code in a sandbox and returns the result. Use this for calculations, data formatting, decoding ciphers, and running scripts."
    override val parameters = mapOf(
        "code" to "The JavaScript code to execute. The last evaluated statement is returned automatically. Example: 'const x = 5; x * 10;'"
    )

    override suspend fun execute(args: Map<String, String>): String {
        val code = args["code"] ?: return "ERROR: No code provided."
        
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val webView = WebView(context)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }
                    continuation.invokeOnCancellation { webView.destroy() }
                    
                    // Use JSONObject.quote to safely escape the JavaScript string
                    val escapedCode = JSONObject.quote(code)
                    val wrappedCode = """
                        (function() {
                            try {
                                var res = eval($escapedCode);
                                return res !== undefined ? res.toString() : "undefined";
                            } catch (e) {
                                return "ERROR: " + e.message;
                            }
                        })()
                    """.trimIndent()
                    
                    webView.evaluateJavascript(wrappedCode) { result ->
                        if (continuation.isActive) {
                            // evaluateJavascript returns a JSON-formatted string (e.g. "\"result\"" or "4")
                            val cleanResult = if (result != null && result != "null") {
                                if (result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
                                    // Decode JSON string escaping
                                    try {
                                        // A simple JSON array parsing will cleanly extract the string value
                                        val array = org.json.JSONArray("[$result]")
                                        array.optString(0, result)
                                    } catch (e: Exception) {
                                        result.substring(1, result.length - 1)
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n")
                                    }
                                } else {
                                    result
                                }
                            } else {
                                "undefined"
                            }
                            webView.destroy()
                            continuation.resume(cleanResult)
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume("ERROR: ${e.message}")
                    }
                }
            }
        }
    }
}
