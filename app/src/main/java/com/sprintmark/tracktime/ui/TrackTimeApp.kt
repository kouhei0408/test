package com.sprintmark.tracktime.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import com.sprintmark.tracktime.model.AppMode
import com.sprintmark.tracktime.model.SyncSample
import kotlin.math.roundToInt

@Composable
fun TrackTimeApp(
    uiState: TrackTimeUiState,
    viewModel: TrackTimeViewModel
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.mode) {
        hasCameraPermission = context.checkSelfPermissionCompat(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (uiState.mode == AppMode.Judge && !hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when (uiState.mode) {
                AppMode.Launcher -> LauncherScreen(uiState = uiState, viewModel = viewModel)
                AppMode.Starter -> StarterScreen(uiState = uiState, viewModel = viewModel)
                AppMode.Judge -> JudgeScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission
                )
            }
        }
    }
}

@Composable
private fun LauncherScreen(
    uiState: TrackTimeUiState,
    viewModel: TrackTimeViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "TrackTime",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Starter / Judge を切り替えて使う写真判定タイマー",
            style = MaterialTheme.typography.bodyLarge
        )
        uiState.errorMessage?.let { message ->
            ErrorPanel(message = message)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.setMode(AppMode.Starter) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("スターター機")
            }
            Button(
                onClick = { viewModel.setMode(AppMode.Judge) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("判定機")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Worker URL")
                OutlinedTextField(
                    value = uiState.workerBaseUrl,
                    onValueChange = viewModel::setWorkerBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Worker URL") }
                )
            }
        }
    }
}

@Composable
private fun StarterScreen(
    uiState: TrackTimeUiState,
    viewModel: TrackTimeViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("スターター機", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { viewModel.setMode(AppMode.Launcher) }, modifier = Modifier.fillMaxWidth()) {
            Text("モード選択に戻る")
        }
        StatusCard(
            title = "同期状態",
            value = uiState.syncState
        )
        StatusCard(
            title = "接続状態",
            value = uiState.connectionState
        )
        StatusCard(
            title = "時計差",
            value = "offset=${uiState.clockOffsetMs} ms / delay=${uiState.clockDelayMs} ms"
        )
        Button(onClick = { viewModel.runClockSync() }, modifier = Modifier.fillMaxWidth()) {
            Text("時計同期")
        }
        Button(onClick = { viewModel.prepareRace() }, modifier = Modifier.fillMaxWidth()) {
            Text("スタート予定時刻を作成")
        }
        Button(onClick = { viewModel.postRaceSchedule() }, modifier = Modifier.fillMaxWidth()) {
            Text("判定機へ送信")
        }
        val planned = uiState.plannedRace
        if (planned != null) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("レースID: ${planned.raceId}")
                    Text("号砲時刻: ${formatEpoch(planned.gunTimeMs)}")
                    Text("Set→号砲: ${planned.setLeadMs} ms")
                    Text("Set 時刻: ${formatEpoch(planned.setTimeMs)}")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.syncSamples) { sample ->
                SyncSampleRow(sample = sample)
            }
        }
    }
}

@Composable
private fun JudgeScreen(
    uiState: TrackTimeUiState,
    viewModel: TrackTimeViewModel,
    hasCameraPermission: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("判定機", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { viewModel.setMode(AppMode.Launcher) }, modifier = Modifier.fillMaxWidth()) {
            Text("モード選択に戻る")
        }
        StatusCard(title = "接続状態", value = uiState.connectionState)
        StatusCard(title = "撮影状態", value = uiState.captureState)
        Button(onClick = { viewModel.connectJudge() }, modifier = Modifier.fillMaxWidth()) {
            Text("Worker に接続")
        }
        OutlinedButton(onClick = { viewModel.disconnectJudge() }, modifier = Modifier.fillMaxWidth()) {
            Text("切断")
        }
        Button(
            onClick = { viewModel.startManualJudgeCapture() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("手動計測スタート")
        }
        if (!hasCameraPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = "Camera 権限が必要です。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Text(
            text = "ラインスキャン画像は撮影後に表示されます。Camera2 の実装はこのベースから拡張できます。",
            style = MaterialTheme.typography.bodyMedium
        )
        uiState.errorMessage?.let { message ->
            ErrorPanel(message = message)
        }
        uiState.latestLineScanBitmap?.let { bitmap ->
            val result = uiState.latestJudgeResult
            var imageWidthPx by remember(bitmap) { mutableStateOf(1) }
            val selectedIndex = result?.selectedFrameIndex
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ラインスキャン", fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .onSizeChanged {
                                imageWidthPx = it.width
                            }
                            .pointerInput(bitmap, result) {
                                detectTapGestures { offset ->
                                    val current = result ?: return@detectTapGestures
                                    val frameCount = current.frameTimestampsMs.size
                                    if (frameCount <= 0 || imageWidthPx <= 0) return@detectTapGestures
                                    val x = offset.x.coerceIn(0f, imageWidthPx.toFloat())
                                    val frameIndex = ((x / imageWidthPx.toFloat()) * (frameCount - 1))
                                        .toInt()
                                        .coerceIn(0, frameCount - 1)
                                    viewModel.selectJudgeFrame(frameIndex)
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "line scan",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        if (selectedIndex != null && result != null && result.frameTimestampsMs.isNotEmpty()) {
                            val frameCount = result.frameTimestampsMs.size
                            val selectedX = if (frameCount <= 1) {
                                0f
                            } else {
                                (selectedIndex.toFloat() / (frameCount - 1).toFloat()) * imageWidthPx.toFloat()
                            }
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(selectedX.roundToInt(), 0) }
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                    if (result != null) {
                        val selectedLineTimeMs = result.selectedFrameIndex?.let { idx ->
                            result.frameTimestampsMs.getOrNull(idx)
                        }
                        Text("選択フレーム: ${result.selectedFrameIndex?.let { it.toString() } ?: "未選択"}")
                        Text(
                            "選択時刻: ${
                                selectedLineTimeMs?.let { formatEpoch(it) } ?: "未選択"
                            }"
                        )
                        Text(
                            "経過タイム: ${
                                result.finishTimeMs?.let { formatDuration(it) } ?: "未算出"
                            }"
                        )
                    }
                }
            }
        }
        uiState.latestJudgeResult?.let { result ->
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("判定結果", fontWeight = FontWeight.Bold)
                    Text("レースID: ${result.raceId}")
                    Text("Set→号砲: ${result.setLeadMs} ms")
                    Text("フレーム数: ${result.frameTimestampsMs.size}")
                    Text("選択フレーム: ${result.selectedFrameIndex?.let { it.toString() } ?: "未選択"}")
                    Text("タイム: ${result.finishTimeMs?.let { formatDuration(it) } ?: "未算出"}")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value)
        }
    }
}

@Composable
private fun SyncSampleRow(sample: SyncSample) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sample.requestId, fontWeight = FontWeight.Medium)
            Text("offset=${sample.offsetMs} ms")
            Text("delay=${sample.delayMs} ms")
        }
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("エラー", fontWeight = FontWeight.Bold)
            Text(message)
        }
    }
}

private fun Context.checkSelfPermissionCompat(permission: String): Int {
    return androidx.core.content.ContextCompat.checkSelfPermission(this, permission)
}

private fun formatDuration(milliseconds: Long): String {
    val sign = if (milliseconds < 0) "-" else ""
    val abs = kotlin.math.abs(milliseconds)
    val minutes = abs / 60_000
    val seconds = (abs % 60_000) / 1_000
    val millis = abs % 1_000
    return sign + "%02d:%02d.%03d".format(minutes, seconds, millis)
}

private fun formatEpoch(epochMs: Long): String {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = epochMs
    }
    return "%02d:%02d:%02d.%03d".format(
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
        calendar.get(java.util.Calendar.SECOND),
        calendar.get(java.util.Calendar.MILLISECOND)
    )
}
