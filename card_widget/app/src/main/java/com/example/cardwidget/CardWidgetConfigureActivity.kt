package com.example.cardwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat

/**
 * ウィジェットをホーム画面に置いた直後に自動で開く設定画面。
 * カード番号は直接入力するか、実物のカードのバーコードを撮った写真から読み取って自動入力できる。
 * 値・バーコード形式は端末内のSharedPreferencesにのみ保存する（ソースコードにも書かない、どこにも送信しない）。
 */
class CardWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var libraryFormat: BarcodeFormat = BarcodeFormat.CODE_128
    private var rakutenFormat: BarcodeFormat = BarcodeFormat.CODE_128

    private lateinit var editLibrary: EditText
    private lateinit var editRakuten: EditText

    private val pickLibraryImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedImage(uri, editLibrary) { libraryFormat = it }
    }
    private val pickRakutenImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedImage(uri, editRakuten) { rakutenFormat = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_configure)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        editLibrary = findViewById(R.id.edit_library)
        editRakuten = findViewById(R.id.edit_rakuten)

        val library = WidgetPrefs.getLibrary(this, appWidgetId)
        val rakuten = WidgetPrefs.getRakuten(this, appWidgetId)
        editLibrary.setText(library.value)
        libraryFormat = library.format
        editRakuten.setText(rakuten.value)
        rakutenFormat = rakuten.format

        findViewById<Button>(R.id.button_scan_library).setOnClickListener {
            pickLibraryImage.launch("image/*")
        }
        findViewById<Button>(R.id.button_scan_rakuten).setOnClickListener {
            pickRakutenImage.launch("image/*")
        }

        findViewById<Button>(R.id.button_save).setOnClickListener {
            WidgetPrefs.saveLibrary(this, appWidgetId, editLibrary.text.toString().trim(), libraryFormat)
            WidgetPrefs.saveRakuten(this, appWidgetId, editRakuten.text.toString().trim(), rakutenFormat)

            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateWidget(this, appWidgetManager, appWidgetId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private fun handlePickedImage(uri: Uri?, target: EditText, onFormat: (BarcodeFormat) -> Unit) {
        if (uri == null) return
        val result = BarcodeUtil.decodeFromUri(this, uri)
        if (result != null) {
            target.setText(result.text)
            onFormat(result.format)
            Toast.makeText(this, getString(R.string.scan_success, result.text), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show()
        }
    }
}
