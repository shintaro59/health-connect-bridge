package com.example.healthconnectbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WakeLocation(val latitude: Double, val longitude: Double, val label: String?)

/**
 * 起床検知の瞬間の現在地をベストエフォートで取得する。
 * PCは自宅に固定でも、スマホ（＝ユーザー本人）は東京や海外にも移動するため、
 * 天気を確認する場所はRoutine側で決め打ちにせず、その都度スマホから渡す。
 *
 * 権限が無い・取得タイムアウト・エラー等の場合は null を返し、
 * 呼び出し側（WakeDetectionWorker）は位置情報なしのまま通知フローを続行できる。
 */
object LocationHelper {

    private fun hasBackgroundPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val background = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return coarse && background
    }

    fun currentLocation(context: Context): WakeLocation? {
        if (!hasBackgroundPermission(context)) return null

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            @Suppress("MissingPermission")
            val freshLocation = Tasks.await(
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setDurationMillis(10_000)
                        .build(),
                    cancellationTokenSource.token
                ),
                12, TimeUnit.SECONDS
            )

            @Suppress("MissingPermission")
            val location = freshLocation ?: Tasks.await(client.lastLocation, 5, TimeUnit.SECONDS)
            location ?: return null

            val label = reverseGeocode(context, location.latitude, location.longitude)
            WakeLocation(location.latitude, location.longitude, label)
        } catch (e: Exception) {
            null
        }
    }

    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        return try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.JAPAN)
                .getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { addr ->
                    listOfNotNull(addr.locality ?: addr.subAdminArea, addr.countryName)
                        .joinToString(", ")
                        .ifBlank { null }
                }
        } catch (e: Exception) {
            null
        }
    }
}
