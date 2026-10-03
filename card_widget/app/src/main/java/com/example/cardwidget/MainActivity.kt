package com.example.cardwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** ランチャーから開いた時の案内画面。ウィジェットはここからではなく、ホーム画面の長押しから追加する */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
