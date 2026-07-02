package com.sprintmark.tracktime.model

import android.graphics.Bitmap

enum class AppMode {
    Launcher,
    Starter,
    Judge
}

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error
}

data class SyncSample(
    val requestId: String,
    val t1ClientSendMs: Long,
    val t2JudgeReceiveMs: Long,
    val t3JudgeSendMs: Long,
    val t4ClientReceiveMs: Long,
    val offsetMs: Long,
    val delayMs: Long
)

data class RaceSchedule(
    val raceId: String,
    val gunTimeMs: Long,
    val setLeadMs: Long,
    val setTimeMs: Long
)

data class JudgeRaceResult(
    val raceId: String,
    val gunTimeMs: Long,
    val setLeadMs: Long = 0L,
    val setTimeMs: Long = 0L,
    val captureStartMs: Long,
    val frameTimestampsMs: List<Long>,
    val selectedFrameIndex: Int? = null,
    val finishTimeMs: Long? = null,
    val lineScanPath: String? = null
)
