package com.sprintmark.tracktime.network

import com.sprintmark.tracktime.model.RaceSchedule
import com.sprintmark.tracktime.model.SyncSample
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

class WorkerClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun performClockSync(baseUrl: String, sampleCount: Int): List<SyncSample> {
        val results = mutableListOf<SyncSample>()
        repeat(sampleCount) {
            val t1 = System.currentTimeMillis()
            val requestJson = JSONObject()
                .put("t1ClientSendMs", t1)
                .put("requestId", "sync-$t1-$it")
                .toString()
            val body = requestJson.toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/sync")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val errorBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw IOException(buildHttpErrorMessage("同期失敗", response.code, errorBody))
                }
                val t4 = System.currentTimeMillis()
                val payload = JSONObject(errorBody.orEmpty())
                val t2 = payload.getLong("t2JudgeReceiveMs")
                val t3 = payload.getLong("t3JudgeSendMs")
                val requestId = payload.getString("requestId")
                val offset = ((t2 - t1) + (t3 - t4)) / 2L
                val delay = max(0L, (t4 - t1) - (t3 - t2))
                results += SyncSample(
                    requestId = requestId,
                    t1ClientSendMs = t1,
                    t2JudgeReceiveMs = t2,
                    t3JudgeSendMs = t3,
                    t4ClientReceiveMs = t4,
                    offsetMs = offset,
                    delayMs = delay
                )
            }
        }
        return results
    }

    fun postRaceSchedule(baseUrl: String, schedule: RaceSchedule) {
        val bodyJson = JSONObject()
            .put("raceId", schedule.raceId)
            .put("gunTimeMs", schedule.gunTimeMs)
            .put("setLeadMs", schedule.setLeadMs)
            .put("setTimeMs", schedule.setTimeMs)
            .toString()
        val body = bodyJson.toRequestBody(jsonType)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/start")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val errorBody = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException(buildHttpErrorMessage("スタート時刻送信失敗", response.code, errorBody))
            }
        }
    }

    private fun buildHttpErrorMessage(prefix: String, code: Int, body: String?): String {
        val trimmedBody = body?.trim().orEmpty()
        return if (trimmedBody.isNotEmpty()) {
            "$prefix: HTTP $code / $trimmedBody"
        } else {
            "$prefix: HTTP $code"
        }
    }
}
