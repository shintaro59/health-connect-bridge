package com.example.cardwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

data class ScanResult(val text: String, val format: BarcodeFormat)

/**
 * カード番号から固定バーコードのビットマップを作る／カードの写真からバーコードを読み取る。
 * 図書館カード・楽天カードのような「値が変わらない」番号だけを対象とする。
 */
object BarcodeUtil {

    fun encode(
        content: String,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        width: Int = 600,
        height: Int = 200,
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 4)
            val matrix: BitMatrix = MultiFormatWriter().encode(content, format, width, height, hints)
            matrixToBitmap(matrix)
        } catch (e: Exception) {
            null
        }
    }

    /** ギャラリーから選んだ画像のUriから、写っているバーコードの内容と形式(Code128/Code39等)を読み取る */
    fun decodeFromUri(context: Context, uri: Uri): ScanResult? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return decodeFromBitmap(bitmap)
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeFromBitmap(bitmap: Bitmap): ScanResult? {
        val reader = MultiFormatReader()
        reader.setHints(mapOf(DecodeHintType.TRY_HARDER to true))

        // 通常の向きでまず試し、ダメなら90度刻みで回転させて再トライ（スマホ写真はよく傾いているため）
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val candidate = if (rotation == 0) bitmap else rotateBitmap(bitmap, rotation)
            val width = candidate.width
            val height = candidate.height
            val pixels = IntArray(width * height)
            candidate.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decode(binaryBitmap)
                return ScanResult(result.text, result.barcodeFormat)
            } catch (e: Exception) {
                // 次の回転角で再トライ
            }
        }
        return null
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) {
            for (y in 0 until h) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }
}
