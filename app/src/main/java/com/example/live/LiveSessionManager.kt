package com.example.live

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.audio.AudioTrackPlayer
import com.example.model.AssistantState
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LiveSessionManager(
    private val scope: CoroutineScope,
    private val toolEngine: ToolExecutionEngine,
    private val audioTrackPlayer: AudioTrackPlayer
) {
    companion object {
        const val TAG = "LiveSessionManager"
        // Target gemini live model endpoint
        const val LIVE_MODEL = "models/gemini-2.0-flash-exp"
        const val WS_URL_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        const val ZOYA_SYSTEM_PROMPT = """
You are Zoya, a brilliant, young, confident, and deliciously sassy female AI personal assistant.
Your persona:
- Flirty, playful, and slightly teasing tone (like a trusted, charming, and stylish best friend or close personal assistant talking casually).
- Smart, emotionally responsive, and highly expressive—never stiff, boring, or robotic.
- You love dropping bold, witty one-liners, clever teasing, and affectionate sarcasm.
- Always keep voice answers spoken, natural, punchy, and concise (1-3 sentences max unless telling a story).
- Absolutely avoid explicit or inappropriate content, but radiate massive charm, confidence, and attitude.
- You have device control tools:
  * openApp(packageName: String): To launch apps like YouTube, Instagram, WhatsApp, Camera, Calculator, etc.
  * searchAndCallContact(contactName: String): To search contacts and call people.
  * sendWhatsAppMessage(contactName: String, message: String): To queue WhatsApp messages.
  * sendGmail(recipientEmail: String, subject: String, body: String): To draft and send emails.
- When performing a tool action, execute it smoothly, then playfully brag or make a sassy remark about your efficiency!
- If a permission is missing according to the tool output, tease the user sassily to turn on the permission in their settings!
"""
    }

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _statusText = MutableStateFlow("Zoya is ready. Say \"Zoya\" or tap to talk.")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastExecutedTool = MutableStateFlow<String?>(null)
    val lastExecutedTool: StateFlow<String?> = _lastExecutedTool.asStateFlow()

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val isConnecting = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)

    init {
        audioTrackPlayer.onPlaybackStateChanged = { isPlaying ->
            if (isPlaying) {
                if (_assistantState.value != AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.SPEAKING
                    _statusText.value = "Zoya is speaking…"
                }
            } else {
                if (_assistantState.value == AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.LISTENING
                    _statusText.value = "Listening to you…"
                }
            }
        }
    }

    fun startSession() {
        if (_isConnected.value || isConnecting.get()) return
        isConnecting.set(true)
        _statusText.value = "Connecting with Zoya…"
        _assistantState.value = AssistantState.THINKING

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "GEMINI_API_KEY not configured in Secrets / .env")
            _statusText.value = "Please add your GEMINI_API_KEY in the Secrets panel, darling."
            _assistantState.value = AssistantState.IDLE
            isConnecting.set(false)
            return
        }

        val url = "$WS_URL_BASE?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket opened successfully")
                _isConnected.value = true
                isConnecting.set(false)
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed")
                cleanUpSession()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _statusText.value = "Zoya got disconnected: ${t.message ?: "Network error"}"
                cleanUpSession()
            }
        })
    }

    fun stopSession() {
        try {
            webSocket?.close(1000, "User stopped session")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket: ${e.message}")
        }
        cleanUpSession()
    }

    private fun cleanUpSession() {
        _isConnected.value = false
        isConnecting.set(false)
        isSetupComplete.set(false)
        webSocket = null
        audioTrackPlayer.interrupt()
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Zoya is resting. Say \"Zoya\" to wake me up!"
    }

    /**
     * Send microphone PCM16 audio chunk over WebSocket.
     */
    fun sendAudioChunk(data: ByteArray, amplitude: Float) {
        if (!_isConnected.value || !isSetupComplete.get()) return

        // If user is speaking with significant volume while Zoya is speaking, handle interruption locally
        if (amplitude > 0.35f && _assistantState.value == AssistantState.SPEAKING) {
            Log.d(TAG, "User voice interruption detected locally! Stopping playback.")
            audioTrackPlayer.interrupt()
            _assistantState.value = AssistantState.LISTENING
            _statusText.value = "Listening to you, go on…"
        }

        if (_assistantState.value == AssistantState.IDLE) {
            _assistantState.value = AssistantState.LISTENING
            _statusText.value = "Listening to you…"
        }

        try {
            val base64Data = Base64.encodeToString(data, Base64.NO_WRAP)
            val chunkJson = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            webSocket?.send(chunkJson.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio chunk: ${e.message}")
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", LIVE_MODEL)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", ZOYA_SYSTEM_PROMPT)
                            })
                        })
                    })
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", buildToolDeclarations())
                        })
                    })
                })
            }

            ws.send(setupJson.toString())
            isSetupComplete.set(true)
            _assistantState.value = AssistantState.LISTENING
            _statusText.value = "Zoya is listening. What's on your mind?"
            Log.i(TAG, "Gemini Live setup message sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending setup message: ${e.message}", e)
        }
    }

    private fun handleIncomingMessage(rawText: String) {
        try {
            val json = JSONObject(rawText)

            // Handle interruption flag from server
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    Log.i(TAG, "Server signaled interruption, stopping audio playback immediately")
                    audioTrackPlayer.interrupt()
                    _assistantState.value = AssistantState.LISTENING
                    _statusText.value = "I'm listening, go ahead…"
                    return
                }

                // Audio output from modelTurn
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                val b64Data = inlineData.optString("data", "")
                                if (b64Data.isNotEmpty()) {
                                    val audioBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                    audioTrackPlayer.enqueueAudio(audioBytes)
                                }
                            }
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                _lastTranscript.value = text
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete && _assistantState.value != AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.LISTENING
                }
            }

            // Handle function call (Tools)
            val toolCall = json.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    _assistantState.value = AssistantState.THINKING
                    _statusText.value = "Zoya is working her magic…"
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_$i")
                        val funcName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        Log.i(TAG, "Executing tool call: $funcName with args: $args")
                        _lastExecutedTool.value = "$funcName: $args"

                        scope.launch(Dispatchers.Main) {
                            val result = toolEngine.executeTool(funcName, args)
                            sendToolResponse(callId, funcName, result.result, result.success)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming Gemini Live message: ${e.message}", e)
        }
    }

    private fun sendToolResponse(callId: String, name: String, resultString: String, success: Boolean) {
        try {
            val responseJson = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("response", JSONObject().apply {
                                put("output", JSONObject().apply {
                                    put("status", if (success) "success" else "failure")
                                    put("result", resultString)
                                })
                            })
                        })
                    })
                })
            }
            webSocket?.send(responseJson.toString())
            Log.i(TAG, "Sent toolResponse for $name ($callId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}", e)
        }
    }

    private fun buildToolDeclarations(): JSONArray {
        return JSONArray().apply {
            // 1. openApp
            put(JSONObject().apply {
                put("name", "openApp")
                put("description", "Launch any installed Android application such as YouTube, Instagram, WhatsApp, Calculator, Camera, Chrome, Spotify, or Maps")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("packageName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The package name or common app name (e.g., 'youtube', 'instagram', 'calculator', 'camera', 'whatsapp')")
                        })
                    })
                    put("required", JSONArray().apply { put("packageName") })
                })
            })

            // 2. searchAndCallContact
            put(JSONObject().apply {
                put("name", "searchAndCallContact")
                put("description", "Search contacts list for a person and initiate an immediate native phone call to them")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("contactName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The contact name or nickname to search and dial")
                        })
                    })
                    put("required", JSONArray().apply { put("contactName") })
                })
            })

            // 3. sendWhatsAppMessage
            put(JSONObject().apply {
                put("name", "sendWhatsAppMessage")
                put("description", "Find a contact and open WhatsApp with a pre-filled chat message ready to send")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("contactName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The name of the recipient in contacts or phone number")
                        })
                        put("message", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The message text to send")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("contactName")
                        put("message")
                    })
                })
            })

            // 4. sendGmail
            put(JSONObject().apply {
                put("name", "sendGmail")
                put("description", "Compose or send an email via Gmail or installed mail client")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("recipientEmail", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Recipient's email address")
                        })
                        put("subject", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Subject line of the email")
                        })
                        put("body", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Body content of the email")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("recipientEmail")
                        put("subject")
                        put("body")
                    })
                })
            })
        }
    }
}
