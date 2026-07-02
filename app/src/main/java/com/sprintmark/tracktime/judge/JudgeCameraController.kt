package com.sprintmark.tracktime.judge

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sprintmark.tracktime.model.JudgeRaceResult
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JudgeCameraController(
    private val application: Application,
    private val onConnectionStateChanged: (String) -> Unit,
    private val onCaptureStateChanged: (String) -> Unit,
    private val onRaceResult: (JudgeRaceResult, Bitmap) -> Unit
) {
    private val connected = AtomicBoolean(false)
    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var workerBridge: WorkerBridge? = null
    private var cameraSession: JudgeCameraSession? = null

    fun connect(baseUrl: String) {
        if (connected.get()) return
        checkCameraPermission()
        onConnectionStateChanged("接続中...")
        workerBridge = WorkerBridge(
            client = okHttpClient,
            baseUrl = baseUrl,
            onStateChanged = onConnectionStateChanged,
            onDisconnected = {
                connected.set(false)
                onConnectionStateChanged("未接続")
            },
            onMessage = ::handleWorkerMessage
        )
        workerBridge?.connect()
        connected.set(true)
    }

    fun disconnect() {
        workerBridge?.close()
        workerBridge = null
        cameraSession?.close()
        cameraSession = null
        connected.set(false)
        onConnectionStateChanged("未接続")
        onCaptureStateChanged("待機中")
    }

    fun startManualCapture() {
        checkCameraPermission()
        cameraSession?.close()
        cameraSession = JudgeCameraSession(
            context = application,
            onCaptureStateChanged = onCaptureStateChanged,
            onResult = onRaceResult
        )
        onCaptureStateChanged("手動撮影中")
        cameraSession?.startManual()
    }

    private fun handleWorkerMessage(message: JSONObject) {
        when (message.optString("type")) {
            "sync-request" -> {
                workerBridge?.sendSyncResponse(
                    requestId = message.getString("requestId"),
                    t1ClientSendMs = message.getLong("t1ClientSendMs")
                )
            }
            "start-schedule" -> {
                val raceId = message.getString("raceId")
                val gunTimeMs = message.getLong("gunTimeMs")
                val setLeadMs = message.getLong("setLeadMs")
                val setTimeMs = message.getLong("setTimeMs")
                onCaptureStateChanged("準備中")
                cameraSession?.close()
                cameraSession = JudgeCameraSession(
                    context = application,
                    onCaptureStateChanged = onCaptureStateChanged,
                    onResult = onRaceResult
                )
                cameraSession?.start(
                    raceId = raceId,
                    gunTimeMs = gunTimeMs,
                    setLeadMs = setLeadMs,
                    setTimeMs = setTimeMs
                )
            }
            "registered" -> {
                onConnectionStateChanged("接続済み")
            }
        }
    }

    private fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(application, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        check(granted) { "Camera permission is required for judge mode." }
    }

    fun close() {
        disconnect()
    }
}

private class WorkerBridge(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val onStateChanged: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onMessage: (JSONObject) -> Unit
) {
    private var socket: okhttp3.WebSocket? = null

    fun connect() {
        val wsUrl = baseUrl
            .trimEnd('/')
            .replaceFirst("^https://".toRegex(), "wss://")
            .replaceFirst("^http://".toRegex(), "ws://") + "/ws"
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                onStateChanged("登録中...")
                webSocket.send(
                    JSONObject()
                        .put("type", "register")
                        .put("role", "judge")
                        .toString()
                )
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                runCatching { JSONObject(text) }
                    .onSuccess(onMessage)
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                onStateChanged("接続失敗")
                onDisconnected()
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                onStateChanged("未接続")
                onDisconnected()
            }
        })
    }

    fun sendSyncResponse(requestId: String, t1ClientSendMs: Long) {
        val now = System.currentTimeMillis()
        socket?.send(
            JSONObject()
                .put("type", "sync-response")
                .put("requestId", requestId)
                .put("t1ClientSendMs", t1ClientSendMs)
                .put("t2JudgeReceiveMs", now)
                .put("t3JudgeSendMs", now)
                .toString()
        )
    }

    fun close() {
        socket?.close(1000, "shutdown")
        socket = null
    }
}
