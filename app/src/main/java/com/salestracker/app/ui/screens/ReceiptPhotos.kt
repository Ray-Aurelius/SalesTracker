package com.salestracker.app.ui.screens

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import com.salestracker.app.R
import com.salestracker.app.data.ReceiptStore
import com.salestracker.app.security.AppAuth
import com.salestracker.app.security.findActivity
import com.salestracker.app.ui.AppViewModel
import java.time.LocalDate

/** Tag for the "Take photo" button in the expense form. */
const val RECEIPT_CAMERA_TAG = "receiptCamera"
/** Tag for the "Choose photo" button in the expense form. */
const val RECEIPT_PICK_TAG = "receiptPick"

/**
 * Runs an action only after the owner proves it's them with the phone's lock (PIN, password, pattern,
 * fingerprint or face), unless they turned "Lock receipt photos" off. With no screen lock on the phone
 * there is nothing to check against, so it explains how to set one instead of opening the photo.
 */
@Composable
fun rememberReceiptUnlock(vm: AppViewModel): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    val needsLock = remember { mutableStateOf(false) }
    if (needsLock.value) {
        AlertDialog(
            onDismissRequest = { needsLock.value = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.receipt_needs_lock_title)) },
            text = { Text(stringResource(R.string.receipt_needs_lock_body)) },
            confirmButton = { TextButton(onClick = { needsLock.value = false }) { Text(stringResource(R.string.done)) } },
        )
    }
    val lock = vm.lockReceipts
    return remember(lock, context) {
        { action ->
            val activity = context.findActivity()
            when {
                !lock -> action()
                activity == null -> {}
                !AppAuth.isAvailable(context) -> { needsLock.value = true }
                else -> AppAuth.authenticate(
                    activity,
                    onSuccess = action,
                    onStart = vm::beginAuth,
                    onEnd = vm::endAuth,
                    title = R.string.receipt_unlock_title,
                    subtitle = R.string.receipt_unlock_subtitle,
                )
            }
        }
    }
}

/**
 * The Receipts part of the expense form: take a photo with the phone's camera app or choose one in Android's
 * photo picker. No permission is needed for either, and the app only ever receives the one photo.
 * Photos show as locked rows, never thumbnails, so nothing is visible until the owner unlocks it.
 *
 * [receipts] is the form's list; [added] collects photos stored during this visit, so Cancel can remove them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReceiptSection(vm: AppViewModel, receipts: SnapshotStateList<Long>, added: SnapshotStateList<Long>, initial: List<Long>) {
    val context = LocalContext.current
    val unlock = rememberReceiptUnlock(vm)
    val ownerCheck = rememberOwnerCheck(vm)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }

    fun handle(id: Long?, result: AppViewModel.ReceiptResult) {
        busy = false
        if (id != null) {
            receipts.add(id); added.add(id); error = null
        } else {
            error = if (result == AppViewModel.ReceiptResult.NO_SECURE_STORAGE) R.string.receipt_no_secure_storage else R.string.receipt_unreadable
        }
    }

    val cameraFile = remember { ReceiptStore.cameraFile(context) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && cameraFile.length() > 0) {
            busy = true
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", cameraFile)
            vm.importReceipt(uri, deleteAfter = cameraFile, onDone = ::handle)
        } else {
            ReceiptStore.clearCamera(context)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            vm.importReceipt(uri, onDone = ::handle)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.receipts_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val full = receipts.size >= ReceiptStore.MAX_PER_EXPENSE
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                enabled = !busy && !full,
                onClick = {
                    ReceiptStore.clearCamera(context)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", cameraFile)
                    try {
                        camera.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.receipt_no_camera, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.testTag(RECEIPT_CAMERA_TAG),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.receipt_take_photo))
            }
            OutlinedButton(
                enabled = !busy && !full,
                onClick = {
                    try {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: ActivityNotFoundException) {
                        error = R.string.receipt_unreadable
                    }
                },
                modifier = Modifier.testTag(RECEIPT_PICK_TAG),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.receipt_choose_photo))
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.receipt_saving), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        receipts.forEachIndexed { i, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (vm.lockReceipts) Icons.Filled.Lock else Icons.Filled.Image, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.receipt_n, i + 1), modifier = Modifier.weight(1f))
                TextButton(onClick = { unlock { viewing = i } }) { Text(stringResource(R.string.receipt_view)) }
                IconButton(onClick = {
                    // A photo that was already saved with the expense is a record: removing it needs the owner.
                    if (id in initial) ownerCheck { receipts.remove(id) } else { receipts.remove(id); added.remove(id); vm.discardReceipts(listOf(id)) }
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.receipt_cd_remove, i + 1))
                }
            }
        }
        if (full) Text(stringResource(R.string.receipt_limit, ReceiptStore.MAX_PER_EXPENSE), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.receipt_privacy_note) + if (vm.lockReceipts) " " + stringResource(R.string.receipt_lock_note) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    viewing?.let { start -> ReceiptViewer(vm, receipts.toList(), start, onDismiss = { viewing = null }) }
}

/** The receipt button on an expense in the log: opens its photos (after the owner unlocks them). */
@Composable
internal fun ReceiptBadgeButton(vm: AppViewModel, receipts: List<Long>) {
    val unlock = rememberReceiptUnlock(vm)
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.receipt_cd_view, receipts.size)
    IconButton(onClick = { unlock { open = true } }) {
        BadgedBox(badge = { if (receipts.size > 1) Badge { Text("${receipts.size}") } }) {
            Icon(Icons.Filled.Image, contentDescription = label)
        }
    }
    if (open) ReceiptViewer(vm, receipts, 0, onDismiss = { open = false })
}

/**
 * Full-screen receipt viewer. The photo is decrypted into memory only; nothing readable is written to storage
 * unless the user chooses Save a copy and picks where it goes. Screenshots and screen recording are blocked here.
 * Pinch to zoom, drag to move, double-tap to reset.
 */
@Composable
internal fun ReceiptViewer(vm: AppViewModel, ids: List<Long>, start: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(start.coerceIn(0, (ids.size - 1).coerceAtLeast(0))) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val id = ids.getOrNull(index)

    LaunchedEffect(id) {
        loading = true; bitmap = null; jpeg = null; scale = 1f; offset = Offset.Zero
        if (id == null) { loading = false; return@LaunchedEffect }
        vm.loadReceipt(id) { b, bytes -> bitmap = b; jpeg = bytes; loading = false }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val bytes = jpeg
        if (uri != null && bytes != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
            } catch (e: Exception) {
                false
            }
            Toast.makeText(context, if (ok) R.string.receipt_copy_saved else R.string.receipt_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bitmap
            when {
                b != null -> Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = stringResource(R.string.receipt_of, index + 1, ids.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(id) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                else -> Text(
                    stringResource(R.string.receipt_missing),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().background(Color.Black.copy(alpha = 0.55f)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.receipt_close), tint = Color.White) }
                Text(
                    stringResource(R.string.receipt_of, index + 1, ids.size),
                    color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                if (jpeg != null) {
                    IconButton(onClick = { saver.launch("Receipt-${LocalDate.now()}.jpg") }) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.receipt_save_copy), tint = Color.White)
                    }
                }
            }
            if (ids.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IconButton(enabled = index > 0, onClick = { index-- }, modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.extraLarge)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.receipt_previous), tint = Color.White)
                    }
                    IconButton(enabled = index < ids.size - 1, onClick = { index++ }, modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.extraLarge)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.receipt_next), tint = Color.White)
                    }
                }
            }
        }
    }
}
