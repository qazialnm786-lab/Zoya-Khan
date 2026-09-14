package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject

data class ToolResult(
    val success: Boolean,
    val result: String,
    val toolName: String
)

class ToolExecutionEngine(private val context: Context) {

    fun executeTool(toolName: String, args: JSONObject): ToolResult {
        return try {
            when (toolName) {
                "openApp" -> {
                    val packageName = args.optString("packageName", "")
                    openApp(packageName)
                }
                "searchAndCallContact" -> {
                    val contactName = args.optString("contactName", "")
                    searchAndCallContact(contactName)
                }
                "sendWhatsAppMessage" -> {
                    val contactName = args.optString("contactName", "")
                    val message = args.optString("message", "")
                    sendWhatsAppMessage(contactName, message)
                }
                "sendGmail" -> {
                    val recipientEmail = args.optString("recipientEmail", "")
                    val subject = args.optString("subject", "")
                    val body = args.optString("body", "")
                    sendGmail(recipientEmail, subject, body)
                }
                else -> {
                    ToolResult(
                        success = false,
                        result = "Unknown tool '$toolName'. Zoya doesn't know that trick yet, honey.",
                        toolName = toolName
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = "Oops, something tripped up: ${e.localizedMessage ?: "Unknown error"}",
                toolName = toolName
            )
        }
    }

    fun openApp(target: String): ToolResult {
        val trimmed = target.trim().lowercase()
        if (trimmed.isEmpty()) {
            return ToolResult(false, "Which app do you want me to open? You didn't tell me!", "openApp")
        }

        val pm = context.packageManager

        // Special system shortcuts
        if (trimmed.contains("camera")) {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (cameraIntent.resolveActivity(pm) != null) {
                context.startActivity(cameraIntent)
                return ToolResult(true, "Camera opened! Say cheese, superstar.", "openApp")
            }
        }

        if (trimmed.contains("setting")) {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return ToolResult(true, "Settings opened! Go tweak whatever you like.", "openApp")
        }

        // Direct package lookup
        var launchIntent: Intent? = null
        if (trimmed.contains(".")) {
            launchIntent = pm.getLaunchIntentForPackage(target.trim())
        }

        // Common app name mappings
        if (launchIntent == null) {
            val knownPackages = mapOf(
                "youtube" to "com.google.android.youtube",
                "whatsapp" to "com.whatsapp",
                "instagram" to "com.instagram.android",
                "gmail" to "com.google.android.gm",
                "calculator" to "com.google.android.calculator",
                "chrome" to "com.android.chrome",
                "maps" to "com.google.android.apps.maps",
                "spotify" to "com.spotify.music",
                "play store" to "com.android.vending",
                "twitter" to "com.twitter.android",
                "x" to "com.twitter.android"
            )

            for ((name, pkg) in knownPackages) {
                if (trimmed.contains(name)) {
                    launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) break
                }
            }
        }

        // Search through all launcher applications
        if (launchIntent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
            for (app in resolvedApps) {
                val label = app.loadLabel(pm).toString().lowercase()
                val pkg = app.activityInfo.packageName.lowercase()
                if (label.contains(trimmed) || pkg.contains(trimmed)) {
                    launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                    if (launchIntent != null) break
                }
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolResult(true, "Boom! Opened $target for you. You're welcome!", "openApp")
        } else {
            ToolResult(
                false,
                "I couldn't find an installed app called '$target'. Are you sure it's installed on your phone?",
                "openApp"
            )
        }
    }

    fun searchAndCallContact(contactName: String): ToolResult {
        val trimmed = contactName.trim()
        if (trimmed.isEmpty()) {
            return ToolResult(false, "Who do you want me to call? Give me a name!", "searchAndCallContact")
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission || !hasContactPermission) {
            return ToolResult(
                false,
                "Permission needed: Darling, you haven't given me permission to access Contacts or make Phone Calls. Enable them so I can call people for you!",
                "searchAndCallContact"
            )
        }

        // Search in Contacts Provider
        var phoneNumber: String? = null
        var foundDisplayName: String? = null

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$trimmed%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (numIdx >= 0) phoneNumber = it.getString(numIdx)
                    if (nameIdx >= 0) foundDisplayName = it.getString(nameIdx)
                }
            }
        } catch (e: Exception) {
            return ToolResult(false, "Failed to query contacts: ${e.message}", "searchAndCallContact")
        }

        if (phoneNumber == null) {
            return ToolResult(
                false,
                "I checked your contacts, but couldn't find '$trimmed'. Maybe you have them saved under a nickname?",
                "searchAndCallContact"
            )
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            ToolResult(
                true,
                "Calling ${foundDisplayName ?: trimmed} at $phoneNumber right now! Turn on the charm!",
                "searchAndCallContact"
            )
        } catch (e: Exception) {
            ToolResult(false, "Could not start call: ${e.message}", "searchAndCallContact")
        }
    }

    fun sendWhatsAppMessage(contactName: String, message: String): ToolResult {
        val trimmed = contactName.trim()
        if (message.isBlank()) {
            return ToolResult(false, "What message do you want me to send? Don't leave me hanging!", "sendWhatsAppMessage")
        }

        // Check if user provided digits directly
        val rawDigits = trimmed.replace("[^0-9+]".toRegex(), "")
        var phoneNumber = if (rawDigits.length >= 7) rawDigits else null
        var resolvedName = trimmed

        if (phoneNumber == null && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$trimmed%"),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numIdx >= 0) phoneNumber = it.getString(numIdx)?.replace("[^0-9+]".toRegex(), "")
                        if (nameIdx >= 0) resolvedName = it.getString(nameIdx) ?: trimmed
                    }
                }
            } catch (_: Exception) {
                // Ignore and fallback
            }
        }

        return try {
            if (phoneNumber != null) {
                // Direct phone URL scheme
                val cleanPhone = phoneNumber!!.removePrefix("+")
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    ToolResult(
                        true,
                        "WhatsApp opened for $resolvedName with your message ready: \"$message\". Hit send whenever you're ready!",
                        "sendWhatsAppMessage"
                    )
                } else {
                    // Generic fallback
                    val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                    ToolResult(
                        true,
                        "WhatsApp chat opened with your message!",
                        "sendWhatsAppMessage"
                    )
                }
            } else {
                // Fallback: Share via WhatsApp
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (sendIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(sendIntent)
                    ToolResult(
                        true,
                        "WhatsApp opened with message: \"$message\". Pick $resolvedName and send!",
                        "sendWhatsAppMessage"
                    )
                } else {
                    ToolResult(
                        false,
                        "WhatsApp doesn't seem to be installed on this device.",
                        "sendWhatsAppMessage"
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult(false, "WhatsApp failed to launch: ${e.message}", "sendWhatsAppMessage")
        }
    }

    fun sendGmail(recipientEmail: String, subject: String, body: String): ToolResult {
        val trimmedEmail = recipientEmail.trim()
        if (trimmedEmail.isEmpty()) {
            return ToolResult(false, "Who should I email? I need an email address, silly!", "sendGmail")
        }

        return try {
            val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$trimmedEmail")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mailIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mailIntent)
                ToolResult(
                    true,
                    "Drafted email to $trimmedEmail with subject \"$subject\". Ready for your approval!",
                    "sendGmail"
                )
            } else {
                ToolResult(
                    false,
                    "No email app found to handle mailto intent.",
                    "sendGmail"
                )
            }
        } catch (e: Exception) {
            ToolResult(false, "Email launch failed: ${e.message}", "sendGmail")
        }
    }
}
