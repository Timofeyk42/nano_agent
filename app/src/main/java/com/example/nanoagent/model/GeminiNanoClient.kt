package com.example.nanoagent.model

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel

/** Thin, truthful adapter around AICore. It never fabricates model output. */
class GeminiNanoClient(private val context: Context) {
    private var generativeModel: GenerativeModel? = null

    var isInitialized = false
        private set
    var initializationError: String? = null
        private set

    var temperature: Float = DEFAULT_TEMPERATURE
        set(value) {
            field = value.coerceIn(0f, 1f)
            initializeModel()
        }

    var topK: Int = DEFAULT_TOP_K
        set(value) {
            field = value.coerceAtLeast(1)
            initializeModel()
        }

    init { initializeModel() }

    private fun initializeModel() {
        try {
            generativeModel = GenerativeModel(
                com.google.ai.edge.aicore.generationConfig {
                    context = this@GeminiNanoClient.context
                    temperature = this@GeminiNanoClient.temperature
                    topK = this@GeminiNanoClient.topK
                }
            )
            isInitialized = true
            initializationError = null
        } catch (error: Exception) {
            generativeModel = null
            isInitialized = false
            initializationError = error.message ?: "Unknown AICore error"
            Log.e(TAG, "AICore initialization failed", error)
        }
    }

    suspend fun generateContent(prompt: String): String {
        val model = generativeModel
            ?: throw IllegalStateException("AICore is unavailable. Gemini Nano must be installed and enabled on this device.")
        return try {
            model.generateContent(prompt).text.orEmpty()
        } catch (error: Exception) {
            Log.e(TAG, "Gemini Nano inference failed", error)
            throw IllegalStateException("Gemini Nano could not generate a response.", error)
        }
    }

    private companion object {
        const val TAG = "GeminiNanoClient"
        const val DEFAULT_TEMPERATURE = 0.4f
        const val DEFAULT_TOP_K = 40
    }
}
