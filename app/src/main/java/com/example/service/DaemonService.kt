package com.example.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed class DaemonEvent {
    data class TextChunk(val chunk: String) : DaemonEvent()
    data class ToolCall(val name: String, val argsJson: String, val id: String, val risk: String = "medium") : DaemonEvent()
    data class ToolResult(val name: String, val result: String) : DaemonEvent()
    data class StatusUpdate(val status: String) : DaemonEvent()
    data class Error(val message: String) : DaemonEvent()
    object SessionEnded : DaemonEvent()
}

class DaemonService {
    private val client = OkHttpClient()
    
    @Volatile
    private var webSocket: WebSocket? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableSharedFlow<DaemonEvent>(replay = 1, extraBufferCapacity = 16)
    val events: SharedFlow<DaemonEvent> = _events

    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeUrl: String = "ws://localhost:3005"
    private var activeApiKey: String = ""

    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5

    private fun scheduleReconnect() {
        if (reconnectAttempt >= maxReconnectAttempts) {
            _events.tryEmit(DaemonEvent.Error("Max reconnection attempts reached"))
            return
        }
        reconnectAttempt++
        val delayMs = minOf(1000L * reconnectAttempt, 30000L)
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                _events.tryEmit(DaemonEvent.StatusUpdate("Reconnecting (attempt $reconnectAttempt)..."))
                connect(activeUrl, activeApiKey)
            }
        }
    }

    fun connect(url: String, daemonApiKey: String = "") {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            disconnect()
        }
        
        activeUrl = url
        activeApiKey = daemonApiKey
        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            val requestBuilder = Request.Builder().url(url)
            if (daemonApiKey.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $daemonApiKey")
                requestBuilder.addHeader("X-API-Key", daemonApiKey)
            }
            val request = requestBuilder.build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionState.CONNECTED
                    scope.launch {
                        _events.emit(DaemonEvent.StatusUpdate("Agent session initialized"))
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("DaemonService", "Received: ${text.take(200)}...")
                    scope.launch {
                        try {
                            val json = JSONObject(text)
                            when (json.optString("type")) {
                                "text_chunk" -> {
                                    val chunk = json.optString("text")
                                    _events.emit(DaemonEvent.TextChunk(chunk))
                                }
                                "tool_call" -> {
                                    val name = json.optString("name")
                                    val args = json.optJSONObject("arguments")?.toString() ?: "{}"
                                    val callId = json.optString("callId", System.currentTimeMillis().toString())
                                    val risk = json.optString("risk", "medium")
                                    _events.emit(DaemonEvent.ToolCall(name, args, callId, risk))
                                }
                                "tool_result" -> {
                                    val name = json.optString("name")
                                    val result = json.optString("result")
                                    _events.emit(DaemonEvent.ToolResult(name, result))
                                }
                                "status" -> {
                                    val status = json.optString("message")
                                    _events.emit(DaemonEvent.StatusUpdate(status))
                                }
                                "error" -> {
                                    val err = json.optString("message")
                                    _events.emit(DaemonEvent.Error(err))
                                }
                                "end" -> {
                                    _events.emit(DaemonEvent.SessionEnded)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DaemonService", "Error parsing message", e)
                            // Raw text falls back to direct append if desired
                            _events.emit(DaemonEvent.TextChunk(text))
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    scope.launch {
                        _events.emit(DaemonEvent.Error("Connection failure: ${t.localizedMessage}"))
                    }
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            scope.launch {
                _events.emit(DaemonEvent.Error("Invalid socket configuration: ${e.localizedMessage}"))
            }
        }
    }

    fun sendMessage(text: String) {
        val payload = JSONObject().apply {
            put("type", "user_message")
            put("text", text)
        }
        webSocket?.send(payload.toString())
    }

    fun approveTool(callId: String, approve: Boolean, remember: Boolean) {
        val payload = JSONObject().apply {
            put("type", "tool_approval")
            put("callId", callId)
            put("approved", approve)
            put("always", remember)
        }
        webSocket?.send(payload.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
