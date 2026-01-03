package com.example.bookshare.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrUtils {

    /** OCR from gallery image URI */
    suspend fun mlkitText(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val res = recognizer.process(image).await()
        return res.text ?: ""
    }

    /** OCR from an in-memory bitmap (e.g., cropped ROI from camera) */
    suspend fun mlkitText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val res = recognizer.process(image).await()
        return res.text ?: ""
    }
}
