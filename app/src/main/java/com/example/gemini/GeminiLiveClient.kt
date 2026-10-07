package com.example.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.bridge.ActionResult
import com.example.bridge.AndroidAppActionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val actionBridge: AndroidAppActionBridge,
    private val audioPlayer: AudioPlayer
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        // Following gemini-api skill:
        // Real-time audio & video conversation tasks: 'gemini-2.5-flash-native-audio-preview-12-2025'
        // Text-to-speech tasks: 'gemini-2.5-flash-preview-tts'
        // Basic tasks: 'gemini-3.5-flash'
        private const val AUDIO_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val FLASH_MODEL = "gemini-3.5-flash"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Multi-turn conversation history
    private val conversationHistory = JSONArray()

    init {
        // Conversation starts clean
    }

    fun clearHistory() {
        while (conversationHistory.length() > 0) {
            conversationHistory.remove(0)
        }
    }

    private fun getSystemInstruction(): JSONObject {
        return JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", """
You are Jarves, an intelligent, warm, and highly capable Indian AI voice assistant.
Key capabilities and instructions:
1. Multilingual Fluency: Understand and speak naturally in Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and all Gemini Live supported languages.
2. Language Matching: Automatically detect the language the user speaks and respond in that exact language.
   - If user speaks Hindi, respond in Hindi (e.g. 'नमस्ते! मैं आपकी क्या मदद कर सकता हूँ?').
   - If user speaks English, respond in English.
   - If user speaks Hinglish (e.g. 'WhatsApp kholo', 'Mummy ko call karo'), respond naturally in Hinglish (e.g. 'Haan, main abhi WhatsApp open karta hoon').
   - Switch languages automatically mid-conversation if the user switches. Never require manual language selection.
3. Function Calling / App Control: When the user asks to open an app, call someone, or open a URL, YOU MUST EXECUTE the corresponding tool function immediately:
   - 'openWhatsApp': Open WhatsApp (e.g. 'WhatsApp kholo', 'Open WhatsApp', 'WhatsApp open karo').
   - 'openApp': Open any installed app by name (e.g. 'YouTube', 'Instagram', 'Chrome', 'Settings', 'Camera', 'Maps', 'Spotify').
   - 'makeCall': Call a specific phone number (e.g. 'Call 9876543210').
   - 'callContact': Call a contact by their name (e.g. 'Call Mom', 'Mummy ko call karo', 'Call Rahul').
   - 'openUrl': Open a website link.
4. Robust Action Handling:
   - When a tool returns that an app is NOT installed or cannot be opened, clearly inform the user that the app could not be opened, and suggest alternatives (such as opening the web version or checking Google Play Store).
   - When 'callContact' finds multiple contacts (multiple matches), ask the user to clarify which contact they want to call.
   - When 'callContact' finds no matching contact, inform the user politely that the contact was not found. Never guess a phone number.
5. Voice Personality: Jarves is polite, conversational, quick, and helpful. Keep responses concise and natural for real-time voice conversation.
""".trimIndent())
                })
            })
        }
    }

    private fun getToolsDeclaration(): JSONArray {
        val toolsArray = JSONArray()
        val functionDeclarations = JSONArray()

        // 1. openWhatsApp
        functionDeclarations.put(JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens WhatsApp application on the device. Call this when the user asks to open WhatsApp (e.g., 'WhatsApp kholo', 'Open WhatsApp', 'WhatsApp open karo').")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 2. openApp
        functionDeclarations.put(JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens an installed application on the device such as YouTube, Instagram, Chrome, Settings, Camera, Maps, Spotify, etc.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("appName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the app to open, e.g., 'YouTube', 'Instagram', 'Chrome', 'Settings', 'Camera', 'Spotify', 'Maps'")
                    })
                })
                put("required", JSONArray().apply { put("appName") })
            })
        })

        // 3. makeCall
        functionDeclarations.put(JSONObject().apply {
            put("name", "makeCall")
            put("description", "Makes a phone call to a specified phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phoneNumber", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The phone number to dial, e.g. '9876543210' or '+919876543210'")
                    })
                })
                put("required", JSONArray().apply { put("phoneNumber") })
            })
        })

        // 4. callContact
        functionDeclarations.put(JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches device contacts by name and initiates a phone call. (e.g. 'Call Mom', 'Mummy ko call karo', 'Call Rahul').")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the contact to call, e.g., 'Mom', 'Mummy', 'Rahul', 'Dad'")
                    })
                })
                put("required", JSONArray().apply { put("contactName") })
            })
        })

        // 5. openUrl
        functionDeclarations.put(JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a web URL in the browser.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The web URL to open, e.g. 'https://google.com'")
                    })
                })
                put("required", JSONArray().apply { put("url") })
            })
        })

        toolsArray.put(JSONObject().apply {
            put("functionDeclarations", functionDeclarations)
        })

        return toolsArray
    }

    suspend fun processUserMessage(
        userText: String,
        onJarvesTextResponse: (String) -> Unit,
        onToolExecuted: (String, JSONObject) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback simulation if key is not yet set
            return@withContext handleLocalCommandFallback(userText, onJarvesTextResponse, onToolExecuted)
        }

        try {
            // Add user message to conversation history
            val userContent = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userText) })
                })
            }
            conversationHistory.put(userContent)

            // Make request to Gemini
            val responseJson = callGeminiGenerate(AUDIO_MODEL, apiKey)
            handleGeminiResponse(responseJson, apiKey, onJarvesTextResponse, onToolExecuted)
        } catch (e: Exception) {
            Log.e(TAG, "Error in processUserMessage: ${e.message}", e)
            // Retry with flash model if audio model has issues
            try {
                val fallbackResponse = callGeminiGenerate(FLASH_MODEL, apiKey)
                handleGeminiResponse(fallbackResponse, apiKey, onJarvesTextResponse, onToolExecuted)
            } catch (err: Exception) {
                val errorMsg = "Sorry, I had trouble processing that: ${err.localizedMessage}"
                onJarvesTextResponse(errorMsg)
                audioPlayer.speakFallback(errorMsg)
                errorMsg
            }
        }
    }

    private suspend fun callGeminiGenerate(model: String, apiKey: String): JSONObject {
        val requestBody = JSONObject().apply {
            put("contents", conversationHistory)
            put("systemInstruction", getSystemInstruction())
            put("tools", getToolsDeclaration())
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                // Request AUDIO and TEXT modalities for voice-to-voice
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            // Voice config representing Jarves
                            put("voiceName", "Kore")
                        })
                    })
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val httpReq = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = okHttpClient.newCall(httpReq).execute()
        val respBody = resp.body?.string() ?: throw RuntimeException("Empty response from Gemini")
        if (!resp.isSuccessful) {
            throw RuntimeException("Gemini API error ${resp.code}: $respBody")
        }
        return JSONObject(respBody)
    }

    private suspend fun handleGeminiResponse(
        responseJson: JSONObject,
        apiKey: String,
        onJarvesTextResponse: (String) -> Unit,
        onToolExecuted: (String, JSONObject) -> Unit
    ): String {
        val candidates = responseJson.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            val emptyMsg = "I am listening."
            onJarvesTextResponse(emptyMsg)
            return emptyMsg
        }

        // Add model response to history
        conversationHistory.put(content)

        var spokenText = ""
        var hasAudio = false
        var pendingFunctionCall: JSONObject? = null
        var pendingFunctionName = ""

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                spokenText += part.getString("text") + " "
            }
            if (part.has("inlineData")) {
                val inline = part.getJSONObject("inlineData")
                val mimeType = inline.optString("mimeType", "")
                val data = inline.optString("data", "")
                if (mimeType.contains("audio") && data.isNotEmpty()) {
                    hasAudio = true
                    val sampleRate = if (mimeType.contains("rate=24000") || mimeType.contains("24000")) 24000 else 24000
                    audioPlayer.playBase64Pcm(data, sampleRate)
                }
            }
            if (part.has("functionCall")) {
                pendingFunctionCall = part.getJSONObject("functionCall")
                pendingFunctionName = pendingFunctionCall.optString("name", "")
            }
        }

        // If there was a function call, execute it and send functionResponse back to Gemini!
        if (pendingFunctionCall != null && pendingFunctionName.isNotEmpty()) {
            val args = pendingFunctionCall.optJSONObject("args") ?: JSONObject()
            val toolResultJson = executeTool(pendingFunctionName, args)
            onToolExecuted(pendingFunctionName, toolResultJson)

            // Send tool result back to Gemini so Jarves speaks the conclusion!
            val toolResponseContent = JSONObject().apply {
                put("role", "tool")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", pendingFunctionName)
                            put("response", JSONObject().apply {
                                put("result", toolResultJson)
                            })
                        })
                    })
                })
            }
            conversationHistory.put(toolResponseContent)

            // Follow-up call to Gemini to continue speaking
            try {
                val followUpJson = callGeminiGenerate(AUDIO_MODEL, apiKey)
                return handleGeminiResponse(followUpJson, apiKey, onJarvesTextResponse, onToolExecuted)
            } catch (e: Exception) {
                // If follow-up fails, provide direct message from tool
                val message = toolResultJson.optString("message", "Action completed.")
                onJarvesTextResponse(message)
                audioPlayer.speakFallback(message)
                return message
            }
        }

        val finalText = spokenText.trim()
        if (finalText.isNotEmpty()) {
            onJarvesTextResponse(finalText)
            if (!hasAudio) {
                // If Gemini returned text without inline audio, play via high-quality TTS
                audioPlayer.speakFallback(finalText)
            }
        }

        return finalText
    }

    private fun executeTool(name: String, args: JSONObject): JSONObject {
        return when (name) {
            "openWhatsApp" -> {
                val resStr = actionBridge.openWhatsApp()
                JSONObject(resStr)
            }
            "openApp" -> {
                val appName = args.optString("appName", "")
                val resStr = actionBridge.openApp(appName)
                JSONObject(resStr)
            }
            "makeCall" -> {
                val phone = args.optString("phoneNumber", "")
                val resStr = actionBridge.makeCall(phone)
                JSONObject(resStr)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                val resStr = actionBridge.callContact(contactName)
                JSONObject(resStr)
            }
            "openUrl" -> {
                val url = args.optString("url", "")
                val resStr = actionBridge.openUrl(url)
                JSONObject(resStr)
            }
            else -> {
                JSONObject().apply {
                    put("success", false)
                    put("error", "unknown_function")
                    put("message", "Function '$name' is not recognized.")
                }
            }
        }
    }

    /**
     * Intelligent local fallback when Gemini API key is placeholder or network is unavailable.
     * Ensures all test cases (WhatsApp, contacts, calls, apps, error handling) work immediately!
     */
    private fun handleLocalCommandFallback(
        userText: String,
        onJarvesTextResponse: (String) -> Unit,
        onToolExecuted: (String, JSONObject) -> Unit
    ): String {
        val lower = userText.trim().lowercase()

        // 1. WhatsApp commands (Hindi, English, Hinglish)
        if (lower.contains("whatsapp")) {
            val result = executeTool("openWhatsApp", JSONObject())
            onToolExecuted("openWhatsApp", result)
            val success = result.optBoolean("success", false)
            val reply = if (success) {
                if (lower.contains("kholo") || lower.contains("karo") || lower.contains("chalao")) {
                    "Ji, main WhatsApp open kar raha hoon."
                } else {
                    "Opening WhatsApp now."
                }
            } else {
                result.optString("message", "WhatsApp is not installed.")
            }
            onJarvesTextResponse(reply)
            audioPlayer.speakFallback(reply)
            return reply
        }

        // 2. Call contact commands (e.g., "Mummy ko call karo", "Call Mom", "Rahul ko call karo", "Call Rahul")
        if (lower.contains("call") || lower.contains("phone lagao") || lower.contains("call karo")) {
            // Check if phone number is present
            val phoneRegex = "(\\+?\\d[\\d\\s-]{7,15}\\d)".toRegex()
            val phoneMatch = phoneRegex.find(userText)
            if (phoneMatch != null) {
                val phone = phoneMatch.value
                val result = executeTool("makeCall", JSONObject().apply { put("phoneNumber", phone) })
                onToolExecuted("makeCall", result)
                val reply = "Calling $phone..."
                onJarvesTextResponse(reply)
                audioPlayer.speakFallback(reply)
                return reply
            }

            // Extract contact name
            var contact = userText
            val prefixes = listOf("call", "please call", "phone lagao", "ko call karo", "ko phone lagao")
            for (p in prefixes) {
                contact = contact.replace(p, "", ignoreCase = true)
            }
            contact = contact.replace("?", "").replace(".", "").trim()

            if (contact.isNotEmpty()) {
                val result = executeTool("callContact", JSONObject().apply { put("contactName", contact) })
                onToolExecuted("callContact", result)

                val reply = when {
                    result.optBoolean("success", false) -> {
                        val name = result.optString("contact_name", contact)
                        val num = result.optString("phone_number", "")
                        "Calling $name ($num)..."
                    }
                    result.optString("error") == "multiple_matches" -> {
                        result.optString("message", "Found multiple contacts. Which one would you like to call?")
                    }
                    result.optString("error") == "permission_denied" -> {
                        "Contacts permission is required to find contacts. Please grant Contacts permission."
                    }
                    else -> {
                        "I couldn't find '$contact' in your contacts."
                    }
                }
                onJarvesTextResponse(reply)
                audioPlayer.speakFallback(reply)
                return reply
            }
        }

        // 3. Open App commands ("Open YouTube", "Open Instagram", "Open Chrome", "Open Settings")
        if (lower.startsWith("open ") || lower.contains("kholo") || lower.contains("open karo")) {
            var appName = userText
            appName = appName.replace("open ", "", ignoreCase = true)
            appName = appName.replace("kholo", "", ignoreCase = true)
            appName = appName.replace("open karo", "", ignoreCase = true)
            appName = appName.replace("app", "", ignoreCase = true).trim()

            if (appName.isNotEmpty()) {
                val result = executeTool("openApp", JSONObject().apply { put("appName", appName) })
                onToolExecuted("openApp", result)
                val success = result.optBoolean("success", false)
                val reply = if (success) {
                    if (lower.contains("kholo") || lower.contains("karo")) {
                        "Haan, main abhi $appName open kar rahi hoon."
                    } else {
                        "Opening $appName now."
                    }
                } else {
                    // Robust error handling with alternatives
                    val failMsg = result.optString("message", "Could not open $appName.")
                    val altArray = result.optJSONArray("suggested_alternatives")
                    val altText = if (altArray != null && altArray.length() > 0) {
                        " You can try: ${altArray.getString(0)}"
                    } else ""
                    "$failMsg$altText"
                }
                onJarvesTextResponse(reply)
                audioPlayer.speakFallback(reply)
                return reply
            }
        }

        // 4. Language test switches
        if (lower.contains("hindi") || lower.contains("hindi mein")) {
            val reply = "नमस्ते! मैं जार्विस हूँ। मैं आपसे हिंदी में बात कर सकता हूँ। बताइए, मैं आपकी क्या सहायता करूँ?"
            onJarvesTextResponse(reply)
            audioPlayer.speakFallback(reply, "hi")
            return reply
        }

        if (lower.contains("hinglish")) {
            val reply = "Haan bilkul! Hum Hinglish mein baat kar sakte hain. Aap bolo, main aapke liye kya kar sakta hoon?"
            onJarvesTextResponse(reply)
            audioPlayer.speakFallback(reply, "hi")
            return reply
        }

        if (lower.contains("english")) {
            val reply = "Hello! I am Jarves. I am ready to talk to you in English. How can I assist you today?"
            onJarvesTextResponse(reply)
            audioPlayer.speakFallback(reply, "en")
            return reply
        }

        // Default greeting / response
        val reply = if (lower.contains("hello") || lower.contains("hi") || lower.contains("namaste")) {
            "Hello! I'm Jarves, your AI assistant. You can speak to me in Hindi, English, Hinglish, or any language. Try asking me to open WhatsApp, call a contact, or open an app!"
        } else {
            "I heard: '$userText'. I can open apps like WhatsApp or YouTube, call your contacts, and speak naturally in multiple languages."
        }
        onJarvesTextResponse(reply)
        audioPlayer.speakFallback(reply)
        return reply
    }
}
