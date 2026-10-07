package com.nezzar.nfcattendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Uid
import java.util.concurrent.Executors

/**
 * The QR reader, inside the Scan tab's reader card.
 *
 * CameraX gives the preview and the frames; ZXing decodes them. Both run entirely on
 * the phone - there is no Play Services, no model download and no network involved,
 * and the frames are decoded in memory and dropped. Nothing is photographed, saved
 * or sent anywhere.
 *
 * Only the IMAGE_ANALYSIS use case is enabled: this app deliberately cannot take a
 * picture or record a video with the camera it borrows.
 */
@Composable
fun QrReader(state: AppState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var allowed by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
    }

    when {
        !hasCamera -> {
            Text(
                text = "This device has no camera, so a QR code cannot be read here. " +
                    "NFC still works.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        !allowed -> {
            Text(
                text = "Reading a QR code needs the camera. Nothing is photographed, kept or " +
                    "sent: the frames are decoded in memory and dropped, and the app still has " +
                    "no internet permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { ask.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("Allow the camera")
            }
        }

        else -> {
            // Analysis only: no image capture, no video, latest frame wins.
            val controller = remember(context) {
                LifecycleCameraController(context).apply {
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                }
            }
            val executor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(lifecycleOwner, controller) {
                val analyzer = QrAnalyzer { text -> state.onQrScan(text) }
                controller.bindToLifecycle(lifecycleOwner)
                controller.setImageAnalysisAnalyzer(executor, analyzer)
                onDispose {
                    // Leaving the tab, switching reader, or opening the confirm page all
                    // land here, so the camera is never left running behind a page.
                    controller.clearImageAnalysisAnalyzer()
                    controller.unbind()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hold the code inside the frame. The teacher confirms who it belongs to " +
                    "before anything is recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.qrNotice?.let { notice ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * One frame at a time, decoded with ZXing. Every failure is expected: a QR code is
 * only in the frame for part of every second, so NotFoundException is the normal
 * state of this loop, not an error to report.
 */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private var lastHitAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            // A QR code stays in frame for many frames; decoding four times a second is
            // plenty and keeps the phone cool.
            if (now - lastHitAt < 400L) return

            val luminance = luminanceOf(image) ?: return
            val source = PlanarYUVLuminanceSource(
                luminance,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
            val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            if (!text.isNullOrBlank()) {
                lastHitAt = now
                onText(text)
            }
        } catch (t: Throwable) {
            // NotFoundException per frame, plus the odd malformed frame. Both are normal.
        } finally {
            reader.reset()
            image.close()
        }
    }

    /**
     * The Y plane as a plain grey bitmap. Cameras pad each row to a stride, so an
     * image whose stride is wider than its width is copied row by row rather than
     * handed over skewed.
     */
    private fun luminanceOf(image: ImageProxy): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val buffer = plane.buffer
        val data = ByteArray(width * height)
        if (plane.pixelStride == 1 && plane.rowStride == width) {
            if (buffer.remaining() < data.size) return null
            buffer.get(data, 0, data.size)
            return data
        }
        val row = ByteArray(plane.rowStride)
        var written = 0
        for (y in 0 until height) {
            val toRead = minOf(plane.rowStride, buffer.remaining())
            if (toRead <= 0) break
            buffer.get(row, 0, toRead)
            System.arraycopy(row, 0, data, written, minOf(width, toRead))
            written += width
        }
        return data
    }
}

/**
 * The step the QR fallback exists for. The code on a school ID carries a student
 * number, which this app never reads or keeps, so the code cannot name anybody:
 * the names come from the roster, the teacher confirms one, and the card UID is put
 * in front of them to verify before a presence is recorded.
 */
@Composable
fun QrConfirmScreen(state: AppState, modifier: Modifier = Modifier) {
    val pending = state.qrPending
    val section = state.selectedSection
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf("") }

    if (pending == null) {
        // The page is only reachable with something to confirm; if the state was
        // cleared underneath it, show nothing rather than acting during composition.
        return
    }

    val chosen = pending.chosen
    val needle = filter.trim()
    val names = if (needle.isEmpty()) {
        pending.candidates
    } else {
        pending.candidates.filter { it.name.contains(needle, ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = if (chosen == null) "Who is this?" else "Confirm this student",
                subtitle = if (chosen == null) {
                    "The code on the ID is not one this app reads. Pick the student it belongs to."
                } else {
                    "Check the name against the card UID, then record the presence."
                },
                listState = listState,
            )
        }

        if (chosen == null) {
            if (pending.suggested) {
                item(key = "suggested") {
                    Note("The code carried this name. Confirm it below.")
                }
            }
            if (pending.candidates.size > 6) {
                item(key = "filter") {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Find by name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "count") {
                SectionLabel("Showing: " + names.size + " of " + pending.candidates.size)
            }
            if (names.isEmpty()) {
                item(key = "none") { Note("No name matches \"" + needle + "\".") }
            }
            items(names, key = { it.uid }) { student ->
                BrandCard(modifier = Modifier.clickable { state.qrChoose(student) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = student.uid + "  \u00B7  " + recordedNote(state, student.uid),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item(key = "cancel") {
                OutlinedButton(
                    onClick = { state.cancelQr() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Not now")
                }
            }
        } else {
            item(key = "verify") {
                BrandCard {
                    SectionLabel("Name")
                    Spacer(Modifier.height(6.dp))
                    Text(chosen.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Card UID")
                    Spacer(Modifier.height(6.dp))
                    Text(chosen.uid, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    KeyValueRow("Section", section?.name ?: "(none)")
                    KeyValueRow("In this session", recordedNote(state, chosen.uid))
                    KeyValueRow("Recorded as", "QR")
                    Spacer(Modifier.height(10.dp))
                    Note(
                        "The code itself is not kept: it was used to find this name and then " +
                            "dropped. Only the name, the card UID and the time are recorded."
                    )
                }
            }
            item(key = "record") {
                Button(
                    onClick = { state.confirmQrAttendance() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Record attendance")
                }
            }
            item(key = "back") {
                OutlinedButton(
                    onClick = { state.qrBackToNames() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Not this one")
                }
            }
        }
    }
}

/** What this session already knows about a student, for the verification step. */
private fun recordedNote(state: AppState, uid: String): String {
    val tap = state.session?.taps?.firstOrNull { Uid.normalize(it.uid) == Uid.normalize(uid) }
        ?: return "not recorded yet"
    return "already " + ReportBuilder.timeText(tap.atMillis) + " (" + tap.method.label + ")"
}
