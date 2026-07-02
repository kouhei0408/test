package com.sprintmark.tracktime.judge

import android.graphics.Bitmap
import android.media.Image
import com.sprintmark.tracktime.model.JudgeRaceResult
import java.nio.ByteBuffer

class LineScanCaptureEngine(
    private val onLineScanReady: (JudgeRaceResult, Bitmap) -> Unit
) {
    private val frameTimestamps = mutableListOf<Long>()
    private var scanBitmap: Bitmap? = null
    private var scanColumns = 0
    private var frameHeight = 0
    private var raceId: String = "pending"
    private var gunTimeMs: Long = 0L
    private var setLeadMs: Long = 0L
    private var setTimeMs: Long = 0L

    fun begin(
        raceId: String,
        gunTimeMs: Long,
        setLeadMs: Long,
        setTimeMs: Long
    ) {
        scanBitmap?.recycle()
        this.raceId = raceId
        this.gunTimeMs = gunTimeMs
        this.setLeadMs = setLeadMs
        this.setTimeMs = setTimeMs
        scanBitmap = null
        scanColumns = 0
        frameTimestamps.clear()
    }

    fun process(image: Image) {
        val yPlane = image.planes.firstOrNull() ?: return
        val width = image.width
        val height = image.height
        if (scanBitmap == null) {
            frameHeight = height
            scanBitmap = Bitmap.createBitmap(1, frameHeight, Bitmap.Config.ARGB_8888)
        }

        val column = extractCenterColumn(
            buffer = yPlane.buffer,
            rowStride = yPlane.rowStride,
            width = width,
            height = height,
            pixelStride = yPlane.pixelStride
        )

        val previous = scanBitmap!!
        val expanded = Bitmap.createBitmap(scanColumns + 1, frameHeight, Bitmap.Config.ARGB_8888)
        if (scanColumns > 0) {
            val previousPixels = IntArray(scanColumns * frameHeight)
            previous.getPixels(previousPixels, 0, scanColumns, 0, 0, scanColumns, frameHeight)
            expanded.setPixels(previousPixels, 0, scanColumns, 0, 0, scanColumns, frameHeight)
        }
        expanded.setPixels(column, 0, 1, scanColumns, 0, 1, frameHeight)
        scanBitmap = expanded
        scanColumns += 1
        frameTimestamps += image.timestamp / 1_000_000L

        onLineScanReady(buildResult(), expanded)
    }

    private fun buildResult(): JudgeRaceResult {
        val timestamps = frameTimestamps.toList()
        val finishTimeMs = timestamps.lastOrNull()?.minus(gunTimeMs)
        return JudgeRaceResult(
            raceId = raceId,
            gunTimeMs = gunTimeMs,
            setLeadMs = setLeadMs,
            setTimeMs = setTimeMs,
            captureStartMs = timestamps.firstOrNull() ?: 0L,
            frameTimestampsMs = timestamps,
            selectedFrameIndex = null,
            finishTimeMs = finishTimeMs,
            lineScanPath = null
        )
    }

    private fun extractCenterColumn(
        buffer: ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
        pixelStride: Int
    ): IntArray {
        val centerX = width / 2
        val pixels = IntArray(height)
        for (y in 0 until height) {
            val index = y * rowStride + centerX * pixelStride
            val luminance = buffer.get(index).toInt() and 0xFF
            pixels[y] = -0x1000000 or (luminance shl 16) or (luminance shl 8) or luminance
        }
        return pixels
    }

    fun close() {
        scanBitmap?.recycle()
        scanBitmap = null
    }
}
