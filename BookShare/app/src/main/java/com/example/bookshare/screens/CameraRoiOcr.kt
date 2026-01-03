package com.example.bookshare.ocr

import android.Manifest
import android.graphics.*
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CameraRoiOcrDialog(
    title: String,
    onDismiss: () -> Unit,
    onTextExtracted: (String) -> Unit,
    roiWidthRatio: Float = 0.85f,
    roiHeightRatio: Float = 0.25f
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera permission
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasCameraPermission = granted
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // ImageCapture kept in remember so it survives recomposition
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetResolution(Size(1280, 720))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(
                    enabled = hasCameraPermission,
                    onClick = {
                        val pv = previewView ?: return@Button
                        val photoFile = File.createTempFile("capture_", ".jpg", context.cacheDir)
                        val outputOptions =
                            ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val rawBmp = BitmapFactory.decodeFile(photoFile.absolutePath)
                                    if (rawBmp == null) {
                                        onDismiss()
                                        return
                                    }

                                    // Rotate according to EXIF to get upright bitmap
                                    val exif = ExifInterface(photoFile.absolutePath)
                                    val orientation = exif.getAttributeInt(
                                        ExifInterface.TAG_ORIENTATION,
                                        ExifInterface.ORIENTATION_NORMAL
                                    )
                                    val fullBmp = when (orientation) {
                                        ExifInterface.ORIENTATION_ROTATE_90 ->
                                            rawBmp.rotate(90f)
                                        ExifInterface.ORIENTATION_ROTATE_180 ->
                                            rawBmp.rotate(180f)
                                        ExifInterface.ORIENTATION_ROTATE_270 ->
                                            rawBmp.rotate(270f)
                                        ExifInterface.ORIENTATION_TRANSPOSE ->
                                            rawBmp.rotate(90f, mirror = true)
                                        ExifInterface.ORIENTATION_TRANSVERSE ->
                                            rawBmp.rotate(270f, mirror = true)
                                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                                            rawBmp.rotate(0f, mirror = true)
                                        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                                            rawBmp.rotate(180f, mirror = true)
                                        else -> rawBmp
                                    }

                                    // VIEW → IMAGE mapping (PreviewView uses center-crop FILL_CENTER)
                                    val viewW = pv.width.toFloat().coerceAtLeast(1f)
                                    val viewH = pv.height.toFloat().coerceAtLeast(1f)
                                    val roiView =
                                        rectInView(viewW, viewH, roiWidthRatio, roiHeightRatio)

                                    val roiImage = mapViewRectToImageRectCenterCrop(
                                        viewW,
                                        viewH,
                                        fullBmp.width.toFloat(),
                                        fullBmp.height.toFloat(),
                                        roiView
                                    )

                                    // Run OCR on full image, filter words inside roiImage
                                    lifecycleOwner.lifecycleScope.launch {
                                        val text = withContext(Dispatchers.Default) {
                                            extractWordsInsideRoi(fullBmp, roiImage)
                                        }
                                        onTextExtracted(text)
                                        delay(120)
                                        onDismiss()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onDismiss()
                                }
                            }
                        )
                    }
                ) { Text("Capture") }
            }
        },
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!hasCameraPermission) {
                    Text("Camera permission is required.")
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val pv = PreviewView(ctx).apply {
                                // center-crop
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            previewView = pv

                            val cameraProvider = cameraProviderFuture.get()
                            val displayRotation =
                                pv.display?.rotation ?: android.view.Surface.ROTATION_0

                            val preview = Preview.Builder()
                                .setTargetRotation(displayRotation)
                                .build().also {
                                    it.setSurfaceProvider(pv.surfaceProvider)
                                }

                            imageCapture.targetRotation = displayRotation

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            pv
                        }
                    )

                    RoiOverlay(roiWidthRatio, roiHeightRatio)
                }
            }
        }
    )
}

/** Dim overlay with a transparent ROI box + white border. */
@Composable
private fun RoiOverlay(roiWidthRatio: Float, roiHeightRatio: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rw = w * roiWidthRatio
            val rh = h * roiHeightRatio
            val left = (w - rw) / 2f
            val top = (h - rh) / 2f

            drawRect(color = Color(0x99000000), size = size)
            drawRect(
                color = Color.Transparent,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(rw, rh),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(rw, rh),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
        Text(
            "Align the text inside the box",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0x66000000))
                .padding(6.dp)
        )
    }
}

/** Rect of the ROI in VIEW coordinates (centered). */
private fun rectInView(
    viewW: Float,
    viewH: Float,
    wRatio: Float,
    hRatio: Float
): RectF {
    val rw = viewW * wRatio
    val rh = viewH * hRatio
    val left = (viewW - rw) / 2f
    val top = (viewH - rh) / 2f
    return RectF(left, top, left + rw, top + rh)
}

/**
 * Map a rect from VIEW space to IMAGE space for a center-crop transform:
 *  - image is scaled by s = max(viewW/imgW, viewH/imgH)
 *  - then centered; extra gets cropped off
 */
private fun mapViewRectToImageRectCenterCrop(
    viewW: Float,
    viewH: Float,
    imgW: Float,
    imgH: Float,
    viewRect: RectF
): Rect {
    val scale = max(viewW / imgW, viewH / imgH)
    val dispW = imgW * scale
    val dispH = imgH * scale
    val offsetX = (viewW - dispW) / 2f
    val offsetY = (viewH - dispH) / 2f

    // inverse transform: image = (view - offset) / scale
    val left = ((viewRect.left - offsetX) / scale).roundToInt()
    val top = ((viewRect.top - offsetY) / scale).roundToInt()
    val right = ((viewRect.right - offsetX) / scale).roundToInt()
    val bottom = ((viewRect.bottom - offsetY) / scale).roundToInt()

    val L = left.coerceIn(0, imgW.toInt() - 1)
    val T = top.coerceIn(0, imgH.toInt() - 1)
    val R = right.coerceIn(L + 1, imgW.toInt())
    val B = bottom.coerceIn(T + 1, imgH.toInt())
    return Rect(L, T, R, B)
}

/** Run ML Kit on the full bitmap, keep only words whose boxes are fully inside roiImage. */
private suspend fun extractWordsInsideRoi(
    fullBmp: Bitmap,
    roiImage: Rect
): String = withContext(Dispatchers.Default) {
    val image = InputImage.fromBitmap(fullBmp, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(image).await()

    data class Word(val text: String, val box: Rect)

    val words = mutableListOf<Word>()
    for (block in result.textBlocks) {
        for (line in block.lines) {
            for (element in line.elements) {
                val box = element.boundingBox ?: continue
                if (roiImage.contains(box)) {
                    words.add(Word(element.text, Rect(box)))
                }
            }
        }
    }

    if (words.isEmpty()) return@withContext ""

    // Sort words by row (top) then column (left)
    words.sortWith(
        compareBy<Word> { it.box.top / 20 } // bucket rows
            .thenBy { it.box.top }
            .thenBy { it.box.left }
    )

    // Group into lines based on Y distance
    val lines = mutableListOf<MutableList<Word>>()
    var current = mutableListOf<Word>()
    var currentY = words.first().box.centerY()

    fun startNewLine(w: Word) {
        if (current.isNotEmpty()) lines.add(current)
        current = mutableListOf(w)
        currentY = w.box.centerY()
    }

    for (w in words) {
        if (current.isEmpty()) {
            current.add(w)
            currentY = w.box.centerY()
        } else {
            val dy = abs(w.box.centerY() - currentY)
            if (dy > 24) { // tweak threshold if needed
                startNewLine(w)
            } else {
                current.add(w)
            }
        }
    }
    if (current.isNotEmpty()) lines.add(current)

    // For each line, sort left-to-right and join words
    val sb = StringBuilder()
    lines.forEachIndexed { index, line ->
        line.sortBy { it.box.left }
        if (index > 0) sb.append('\n')
        sb.append(line.joinToString(" ") { it.text })
    }
    sb.toString()
}

/** Rotate bitmap (optionally mirrored) by deg degrees. */
private fun Bitmap.rotate(deg: Float, mirror: Boolean = false): Bitmap {
    if (deg == 0f && !mirror) return this
    val m = Matrix().apply {
        if (mirror) postScale(-1f, 1f)
        postRotate(deg)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
