package com.sprintmark.tracktime.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sprintmark.tracktime.model.AppMode
import com.sprintmark.tracktime.model.JudgeRaceResult
import com.sprintmark.tracktime.model.RaceSchedule
import com.sprintmark.tracktime.model.SyncSample
import com.sprintmark.tracktime.audio.StarterSoundPlayer
import com.sprintmark.tracktime.network.WorkerClient
import com.sprintmark.tracktime.judge.JudgeCameraController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class TrackTimeUiState(
    val mode: AppMode = AppMode.Launcher,
    val workerBaseUrl: String = "https://YOUR-WORKER-DOMAIN.example",
    val connectionState: String = "未接続",
    val syncState: String = "同期前",
    val syncSamples: List<SyncSample> = emptyList(),
    val clockOffsetMs: Long = 0L,
    val clockDelayMs: Long = 0L,
    val plannedRace: RaceSchedule? = null,
    val latestJudgeResult: JudgeRaceResult? = null,
    val latestLineScanBitmap: Bitmap? = null,
    val captureState: String = "待機中",
    val errorMessage: String? = null,
    val lineScanPreviewPath: String? = null
)

class TrackTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(TrackTimeUiState())
    val uiState = _uiState.asStateFlow()
    private val workerClient = WorkerClient()
    private val starterSoundPlayer = StarterSoundPlayer(
        context = application.applicationContext,
        setSoundResId = StarterSoundPlayer.DEFAULT_SET_SOUND_RES,
        gunSoundResId = StarterSoundPlayer.DEFAULT_GUN_SOUND_RES
    )
    private var cueJob: Job? = null
    private val judgeCameraController = JudgeCameraController(
        application = application,
        onConnectionStateChanged = { state ->
            _uiState.update { it.copy(connectionState = state) }
        },
        onCaptureStateChanged = { state ->
            _uiState.update { it.copy(captureState = state) }
        },
        onRaceResult = { result, bitmap ->
            val previous = _uiState.value.latestJudgeResult
            val merged = if (previous?.raceId == result.raceId) {
                result.copy(
                    selectedFrameIndex = previous.selectedFrameIndex,
                    finishTimeMs = previous.finishTimeMs
                )
            } else {
                result
            }
            _uiState.update {
                it.copy(
                    latestJudgeResult = merged,
                    latestLineScanBitmap = bitmap,
                    lineScanPreviewPath = null
                )
            }
        }
    )

    fun setMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun setWorkerBaseUrl(url: String) {
        _uiState.update { it.copy(workerBaseUrl = url.trim().trimEnd('/')) }
    }

    fun connectJudge() {
        val baseUrl = _uiState.value.workerBaseUrl
        _uiState.update { it.copy(connectionState = "接続中...") }
        viewModelScope.launch {
            runCatching {
                judgeCameraController.connect(baseUrl)
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        connectionState = "接続失敗",
                        errorMessage = throwable.message ?: "接続に失敗しました"
                    )
                }
            }
        }
    }

    fun disconnectJudge() {
        viewModelScope.launch {
            judgeCameraController.disconnect()
            _uiState.update { it.copy(connectionState = "未接続", captureState = "待機中") }
        }
    }

    fun startManualJudgeCapture() {
        _uiState.update {
            it.copy(
                latestJudgeResult = null,
                latestLineScanBitmap = null,
                lineScanPreviewPath = null,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            runCatching {
                judgeCameraController.startManualCapture()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        captureState = "手動開始失敗",
                        errorMessage = throwable.message ?: "手動計測を開始できませんでした"
                    )
                }
            }
        }
    }

    fun runClockSync(samples: Int = 20) {
        val baseUrl = _uiState.value.workerBaseUrl
        _uiState.update { it.copy(syncState = "同期中...", syncSamples = emptyList()) }
        viewModelScope.launch {
            runCatching {
                val results = withContext(Dispatchers.IO) {
                    workerClient.performClockSync(baseUrl, samples)
                }
                val best = results.minByOrNull { it.delayMs } ?: error("同期サンプルが取得できませんでした")
                _uiState.update {
                    it.copy(
                        syncState = "同期完了",
                        syncSamples = results,
                        clockOffsetMs = best.offsetMs,
                        clockDelayMs = best.delayMs,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(syncState = "同期失敗", errorMessage = throwable.message ?: "同期エラー")
                }
            }
        }
    }

    fun prepareRace() {
        cueJob?.cancel()
        val nowMs = System.currentTimeMillis()
        val judgeNowMs = nowMs + _uiState.value.clockOffsetMs
        val gunTime = ((judgeNowMs + 10_000L) / 1000L) * 1000L
        val setLead = Random.nextLong(1_100L, 1_601L)
        val setTime = gunTime - setLead
        val schedule = RaceSchedule(
            raceId = "race-${gunTime}-${Random.nextInt(1000, 9999)}",
            gunTimeMs = gunTime,
            setLeadMs = setLead,
            setTimeMs = setTime
        )
        _uiState.update {
            it.copy(
                plannedRace = schedule,
                latestJudgeResult = null,
                latestLineScanBitmap = null,
                lineScanPreviewPath = null,
                errorMessage = null
            )
        }
        scheduleStarterCues(schedule)
    }

    fun postRaceSchedule() {
        val schedule = _uiState.value.plannedRace ?: return
        val baseUrl = _uiState.value.workerBaseUrl
        _uiState.update { it.copy(connectionState = "送信中...") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    workerClient.postRaceSchedule(baseUrl, schedule)
                }
            }.onSuccess {
                _uiState.update { it.copy(connectionState = "送信完了", errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(connectionState = "送信失敗", errorMessage = throwable.message ?: "送信失敗")
                }
            }
        }
    }

    fun selectJudgeFrame(frameIndex: Int) {
        val result = _uiState.value.latestJudgeResult ?: return
        val timestamps = result.frameTimestampsMs
        if (frameIndex !in timestamps.indices) return
        val frameTimeMs = timestamps[frameIndex]
        val finishTimeMs = frameTimeMs - result.gunTimeMs
        _uiState.update {
            it.copy(
                latestJudgeResult = result.copy(
                    selectedFrameIndex = frameIndex,
                    finishTimeMs = finishTimeMs
                )
            )
        }
    }

    override fun onCleared() {
        cueJob?.cancel()
        starterSoundPlayer.release()
        super.onCleared()
        judgeCameraController.close()
    }

    private fun scheduleStarterCues(schedule: RaceSchedule) {
        cueJob = viewModelScope.launch {
            val callDelayMs = schedule.setTimeMs - System.currentTimeMillis()
            if (callDelayMs > 0) {
                delay(callDelayMs)
            }
            runCatching { starterSoundPlayer.playCall() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "Set 音の再生に失敗しました") }
                }

            val gunDelayMs = schedule.gunTimeMs - System.currentTimeMillis()
            if (gunDelayMs > 0) {
                delay(gunDelayMs)
            }
            runCatching { starterSoundPlayer.playGun() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "号砲音の再生に失敗しました") }
                }
        }
    }
}
