package com.example.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class AndroidAppActionBridge(
    private val context: Context,
    private val onActionResult: ((ActionResult) -> Unit)? = null
) {
    private val contactsManager = ContactsManager(context)

    // Predefined popular applications and their package / web fallbacks
    data class AppMetadata(
        val packageName: String?,
        val webFallback: String?,
        val customIntent: (() -> Intent)? = null
    )

    private val commonApps = mapOf(
        "whatsapp" to AppMetadata("com.whatsapp", "https://web.whatsapp.com"),
        "whatsapp business" to AppMetadata("com.whatsapp.w4b", "https://web.whatsapp.com"),
        "youtube" to AppMetadata("com.google.android.youtube", "https://www.youtube.com"),
        "instagram" to AppMetadata("com.instagram.android", "https://www.instagram.com"),
        "chrome" to AppMetadata("com.android.chrome", "https://www.google.com"),
        "google chrome" to AppMetadata("com.android.chrome", "https://www.google.com"),
        "browser" to AppMetadata("com.android.chrome", "https://www.google.com"),
        "settings" to AppMetadata(
            null,
            null,
            customIntent = { Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ),
        "camera" to AppMetadata(
            null,
            null,
            customIntent = { Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ),
        "maps" to AppMetadata("com.google.android.apps.maps", "https://maps.google.com"),
        "google maps" to AppMetadata("com.google.android.apps.maps", "https://maps.google.com"),
        "spotify" to AppMetadata("com.spotify.music", "https://open.spotify.com"),
        "gmail" to AppMetadata("com.google.android.gm", "https://mail.google.com"),
        "email" to AppMetadata("com.google.android.gm", "https://mail.google.com"),
        "twitter" to AppMetadata("com.twitter.android", "https://x.com"),
        "x" to AppMetadata("com.twitter.android", "https://x.com"),
        "telegram" to AppMetadata("org.telegram.messenger", "https://web.telegram.org"),
        "facebook" to AppMetadata("com.facebook.katana", "https://www.facebook.com"),
        "play store" to AppMetadata("com.android.vending", "https://play.google.com"),
        "playstore" to AppMetadata("com.android.vending", "https://play.google.com"),
        "clock" to AppMetadata(
            null,
            null,
            customIntent = { Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ),
        "photos" to AppMetadata("com.google.android.apps.photos", null),
        "gallery" to AppMetadata(
            null,
            null,
            customIntent = {
                Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        )
    )

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean = true

    @JavascriptInterface
    fun openApp(appName: String): String {
        val trimmed = appName.trim().lowercase()
        val pm = context.packageManager

        // 1. Check known apps
        val meta = commonApps[trimmed]
        if (meta != null) {
            // Check custom intent
            if (meta.customIntent != null) {
                try {
                    val intent = meta.customIntent.invoke()
                    if (intent.resolveActivity(pm) != null) {
                        context.startActivity(intent)
                        val result = ActionResult.Success(
                            action = "openApp",
                            message = "Opened $appName successfully."
                        )
                        onActionResult?.invoke(result)
                        return JSONObject().apply {
                            put("success", true)
                            put("action", "openApp")
                            put("app_name", appName)
                            put("message", "Opened $appName successfully.")
                        }.toString()
                    }
                } catch (e: Exception) {
                    // Fall through to error handling
                }
            }

            // Check package launch intent
            if (meta.packageName != null) {
                val launchIntent = pm.getLaunchIntentForPackage(meta.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val result = ActionResult.Success(
                        action = "openApp",
                        message = "Opened $appName successfully."
                    )
                    onActionResult?.invoke(result)
                    return JSONObject().apply {
                        put("success", true)
                        put("action", "openApp")
                        put("app_name", appName)
                        put("package_name", meta.packageName)
                        put("message", "Opened $appName successfully.")
                    }.toString()
                }
            }

            // App known but not installed on device
            val alternatives = mutableListOf<String>()
            if (meta.webFallback != null) {
                alternatives.add("Open $appName on Web browser (${meta.webFallback})")
            }
            if (meta.packageName != null) {
                alternatives.add("Download $appName from Google Play Store")
            }

            val failMsg = "The app '$appName' is not installed on this device."
            val failResult = ActionResult.Failure(
                action = "openApp",
                reason = "not_installed",
                message = failMsg,
                suggestedAlternatives = alternatives,
                webFallbackUrl = meta.webFallback,
                playStorePackage = meta.packageName
            )
            onActionResult?.invoke(failResult)

            return JSONObject().apply {
                put("success", false)
                put("action", "openApp")
                put("error", "not_installed")
                put("app_name", appName)
                put("message", failMsg)
                put("suggested_alternatives", JSONArray(alternatives))
                put("web_fallback_url", meta.webFallback ?: JSONObject.NULL)
                put("play_store_url", meta.packageName?.let { "market://details?id=$it" } ?: JSONObject.NULL)
            }.toString()
        }

        // 2. Search installed packages by label or package name
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label == trimmed || label.contains(trimmed) || appInfo.packageName.contains(trimmed)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val result = ActionResult.Success(
                            action = "openApp",
                            message = "Opened ${pm.getApplicationLabel(appInfo)} successfully."
                        )
                        onActionResult?.invoke(result)
                        return JSONObject().apply {
                            put("success", true)
                            put("action", "openApp")
                            put("app_name", appName)
                            put("package_name", appInfo.packageName)
                            put("message", "Opened ${pm.getApplicationLabel(appInfo)} successfully.")
                        }.toString()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Not installed & not matched
        val alternatives = listOf(
            "Search for '$appName' on Google Play Store",
            "Search for '$appName' in web browser"
        )
        val errorMsg = "Could not find or launch '$appName'. The application does not appear to be installed on this device."
        val failResult = ActionResult.Failure(
            action = "openApp",
            reason = "not_found",
            message = errorMsg,
            suggestedAlternatives = alternatives,
            webFallbackUrl = "https://www.google.com/search?q=${Uri.encode(appName)}",
            playStorePackage = null
        )
        onActionResult?.invoke(failResult)

        return JSONObject().apply {
            put("success", false)
            put("action", "openApp")
            put("error", "not_found")
            put("app_name", appName)
            put("message", errorMsg)
            put("suggested_alternatives", JSONArray(alternatives))
            put("web_fallback_url", "https://www.google.com/search?q=${Uri.encode(appName)}")
        }.toString()
    }

    @JavascriptInterface
    fun openWhatsApp(): String {
        val pm = context.packageManager
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

        for (pkg in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val result = ActionResult.Success(
                    action = "openWhatsApp",
                    message = "Opened WhatsApp successfully."
                )
                onActionResult?.invoke(result)
                return JSONObject().apply {
                    put("success", true)
                    put("action", "openWhatsApp")
                    put("message", "Opened WhatsApp successfully.")
                }.toString()
            }
        }

        // WhatsApp not installed
        val alternatives = listOf(
            "Open WhatsApp Web in browser (https://web.whatsapp.com)",
            "Download WhatsApp from Google Play Store"
        )
        val failResult = ActionResult.Failure(
            action = "openWhatsApp",
            reason = "not_installed",
            message = "WhatsApp is not installed on this device.",
            suggestedAlternatives = alternatives,
            webFallbackUrl = "https://web.whatsapp.com",
            playStorePackage = "com.whatsapp"
        )
        onActionResult?.invoke(failResult)

        return JSONObject().apply {
            put("success", false)
            put("action", "openWhatsApp")
            put("error", "not_installed")
            put("message", "WhatsApp is not installed on this device.")
            put("suggested_alternatives", JSONArray(alternatives))
            put("web_fallback_url", "https://web.whatsapp.com")
            put("play_store_url", "market://details?id=com.whatsapp")
        }.toString()
    }

    @JavascriptInterface
    fun makeCall(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isEmpty()) {
            val failResult = ActionResult.Failure(
                action = "makeCall",
                reason = "invalid_number",
                message = "The provided phone number '$phoneNumber' is invalid."
            )
            onActionResult?.invoke(failResult)
            return JSONObject().apply {
                put("success", false)
                put("action", "makeCall")
                put("error", "invalid_number")
                put("message", "Invalid phone number provided.")
            }.toString()
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // Safe fallback to phone dialer pre-filled with number
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
            val mode = if (hasCallPermission) "direct_call" else "dialer_opened"
            val successResult = ActionResult.Success(
                action = "makeCall",
                message = if (hasCallPermission) "Calling $cleanNumber..." else "Opened dialer for $cleanNumber",
                details = mapOf("phone_number" to cleanNumber, "mode" to mode)
            )
            onActionResult?.invoke(successResult)
            return JSONObject().apply {
                put("success", true)
                put("action", "makeCall")
                put("phone_number", cleanNumber)
                put("mode", mode)
                put("message", if (hasCallPermission) "Calling $cleanNumber..." else "Opened phone dialer with $cleanNumber")
            }.toString()
        } catch (e: Exception) {
            val failResult = ActionResult.Failure(
                action = "makeCall",
                reason = "execution_failed",
                message = "Failed to launch phone call: ${e.message}"
            )
            onActionResult?.invoke(failResult)
            return JSONObject().apply {
                put("success", false)
                put("action", "makeCall")
                put("error", "execution_failed")
                put("message", "Could not start call: ${e.localizedMessage}")
            }.toString()
        }
    }

    @JavascriptInterface
    fun callContact(contactName: String): String {
        val trimmed = contactName.trim()
        if (trimmed.isEmpty()) {
            val fail = ActionResult.Failure(
                action = "callContact",
                reason = "empty_name",
                message = "Contact name cannot be empty."
            )
            onActionResult?.invoke(fail)
            return JSONObject().apply {
                put("success", false)
                put("action", "callContact")
                put("error", "empty_name")
                put("message", "Please specify a contact name.")
            }.toString()
        }

        if (!contactsManager.hasContactsPermission()) {
            val fail = ActionResult.Failure(
                action = "callContact",
                reason = "permission_denied",
                message = "Contacts permission is not granted. Please allow access to contacts to call by name."
            )
            onActionResult?.invoke(fail)
            return JSONObject().apply {
                put("success", false)
                put("action", "callContact")
                put("error", "permission_denied")
                put("message", "Contacts permission is not granted.")
            }.toString()
        }

        val matches = contactsManager.searchContacts(trimmed)

        when {
            matches.isEmpty() -> {
                val fail = ActionResult.Failure(
                    action = "callContact",
                    reason = "not_found",
                    message = "No contact found matching '$trimmed'."
                )
                onActionResult?.invoke(fail)
                return JSONObject().apply {
                    put("success", false)
                    put("action", "callContact")
                    put("error", "not_found")
                    put("contact_name", trimmed)
                    put("message", "I couldn't find '$trimmed' in your contacts.")
                }.toString()
            }
            matches.size == 1 -> {
                val match = matches.first()
                makeCall(match.phoneNumber)
                val success = ActionResult.Success(
                    action = "callContact",
                    message = "Calling ${match.name} (${match.phoneNumber})...",
                    details = mapOf("name" to match.name, "phone" to match.phoneNumber)
                )
                onActionResult?.invoke(success)
                return JSONObject().apply {
                    put("success", true)
                    put("action", "callContact")
                    put("contact_name", match.name)
                    put("phone_number", match.phoneNumber)
                    put("message", "Found ${match.name}. Calling ${match.phoneNumber}...")
                }.toString()
            }
            else -> {
                // Multiple matches found - disambiguation needed
                val names = matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                val disambiguation = ActionResult.DisambiguationNeeded(
                    action = "callContact",
                    query = trimmed,
                    message = "Found ${matches.size} contacts matching '$trimmed'. Which one should I call?",
                    candidates = matches
                )
                onActionResult?.invoke(disambiguation)

                val candidatesArray = JSONArray()
                for (c in matches) {
                    candidatesArray.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone_number", c.phoneNumber)
                        put("label", c.typeLabel)
                    })
                }

                return JSONObject().apply {
                    put("success", false)
                    put("action", "callContact")
                    put("error", "multiple_matches")
                    put("count", matches.size)
                    put("candidates", candidatesArray)
                    put("message", "I found ${matches.size} contacts matching '$trimmed': $names. Which one would you like to call?")
                }.toString()
            }
        }
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val result = ActionResult.Success(
                action = "openUrl",
                message = "Opened $cleanUrl in browser."
            )
            onActionResult?.invoke(result)
            return JSONObject().apply {
                put("success", true)
                put("action", "openUrl")
                put("url", cleanUrl)
                put("message", "Opened $cleanUrl successfully.")
            }.toString()
        } catch (e: Exception) {
            val fail = ActionResult.Failure(
                action = "openUrl",
                reason = "browser_not_found",
                message = "Could not open URL: ${e.localizedMessage}"
            )
            onActionResult?.invoke(fail)
            return JSONObject().apply {
                put("success", false)
                put("action", "openUrl")
                put("error", "browser_not_found")
                put("message", "Could not open $cleanUrl.")
            }.toString()
        }
    }
}
