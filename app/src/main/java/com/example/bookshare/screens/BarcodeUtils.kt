package com.example.bookshare.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

object BarcodeUtils {

    // Scan common book barcodes: EAN-13 (ISBN-13), EAN-8, UPC, QR if needed
    suspend fun scanBarcodes(context: Context, uri: Uri): List<Barcode> {
        val image = InputImage.fromFilePath(context, uri)
        val opts = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE
            ).build()
        val scanner = BarcodeScanning.getClient(opts)
        return scanner.process(image).await()
    }

    // If it looks like ISBN-13 (EAN-13 with 978/979)
    fun eanToIsbn13(ean: String): String? {
        val digits = ean.filter { it.isDigit() }
        if (digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979"))) return digits
        return null
    }

    // Optional: convert ISBN-13 → ISBN-10 when prefix is 978
    fun isbn13to10(isbn13: String): String? {
        val d = isbn13.filter { it.isDigit() }
        if (d.length != 13 || !d.startsWith("978")) return null
        val core = d.substring(3, 12) // 9 digits
        var sum = 0
        for (i in core.indices) sum += (10 - i) * (core[i] - '0')
        var check = 11 - (sum % 11)
        val checkChar = when (check) {
            10 -> 'X'
            11 -> '0'
            else -> ('0' + check)
        }
        return core + checkChar
    }
}
