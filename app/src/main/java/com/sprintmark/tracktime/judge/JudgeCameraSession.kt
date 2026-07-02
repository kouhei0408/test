package com.sprintmark.tracktime.judge

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.util.Range
import android.util.Size
import com.sprintmark.tracktime.model.JudgeRaceResult
import kotlin.math.max

class JudgeCameraSession(
    private val context: Context,
    private val onCaptureStateChanged: (String) -> Unit,
    private val onResult: (JudgeRaceResult, Bitmap) -> Unit
) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val backgroundThread = android.os.HandlerThread("tracktime-camera").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: android.hardware.camera2.CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var processor: LineScanCaptureEngine? = null
    private var scheduledStartRunnable: Runnable? = null

    fun startManual(raceId: String = "manual") {
        start(
            raceId = raceId,
            gunTimeMs = System.currentTimeMillis(),
            setLeadMs = 0L,
            setTimeMs = System.currentTimeMillis()
        )
    }

    fun start(raceId: String, gunTimeMs: Long, setLeadMs: Long, setTimeMs: Long) {
        onCaptureStateChanged("カメラ準備中")
        val cameraId = selectBackCameraId()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val size = selectCaptureSize(characteristics)
        val fpsRange = selectFpsRange(characteristics)

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            android.graphics.ImageFormat.YUV_420_888,
            4
        )
        processor = LineScanCaptureEngine(
            onLineScanReady = onResult
        ).apply {
            begin(raceId = raceId, gunTimeMs = gunTimeMs, setLeadMs = setLeadMs, setTimeMs = setTimeMs)
        }

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                processor?.process(image)
            } finally {
                image.close()
            }
        }, backgroundHandler)

        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession(fpsRange, gunTimeMs)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    close("カメラ切断")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    close("カメラエラー")
                }
            },
            backgroundHandler
        )
    }

    private fun createSession(fpsRange: Range<Int>, gunTimeMs: Long) {
        val camera = cameraDevice ?: return
        val readerSurface = imageReader?.surface ?: return
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(readerSurface)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        camera.createCaptureSession(
            listOf(readerSurface),
            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    captureSession = session
                    val delayMs = max(0L, gunTimeMs - System.currentTimeMillis())
                    onCaptureStateChanged("待機中")
                    scheduledStartRunnable?.let { backgroundHandler.removeCallbacks(it) }
                    scheduledStartRunnable = Runnable {
                        runCatching {
                            onCaptureStateChanged("撮影中")
                            session.setRepeatingRequest(request.build(), null, backgroundHandler)
                        }
                    }
                    if (delayMs <= 0L) {
                        backgroundHandler.post(scheduledStartRunnable!!)
                    } else {
                        backgroundHandler.postDelayed(scheduledStartRunnable!!, delayMs)
                    }
                }

                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                    close("撮影準備失敗")
                }
            },
            backgroundHandler
        )
    }

    private fun selectBackCameraId(): String {
        return cameraManager.cameraIdList.first { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun selectCaptureSize(characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(1280, 720)
        val choices = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
        return choices
            .filter { it.width >= 1280 && it.height >= 720 }
            .minByOrNull { it.width * it.height }
            ?: choices.maxByOrNull { it.width * it.height }
            ?: Size(1280, 720)
    }

    private fun selectFpsRange(characteristics: CameraCharacteristics): Range<Int> {
        val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: return Range(30, 30)
        return ranges
            .filter { it.upper >= 60 }
            .maxByOrNull { it.upper }
            ?: ranges.maxByOrNull { it.upper }
            ?: Range(30, 30)
    }

    fun close(finalState: String = "待機中") {
        scheduledStartRunnable?.let { backgroundHandler.removeCallbacks(it) }
        scheduledStartRunnable = null
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        processor?.close()
        processor = null
        backgroundThread.quitSafely()
        onCaptureStateChanged(finalState)
    }
}
