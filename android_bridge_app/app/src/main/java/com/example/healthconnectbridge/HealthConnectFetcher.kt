package com.example.healthconnectbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.SleepSessionRecord
import com.google.gson.Gson
import java.time.Instant
import java.time.ZonedDateTime

class HealthConnectFetcher(private val context: Context) {

    suspend fun fetchSleepData(): String {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val sevenDaysAgo = now.minus(java.time.Duration.ofDays(7))

            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(
                        sevenDaysAgo,
                        now
                    )
                )
            )

            val sleepData = records.records.map { record ->
                mapOf(
                    "startTime" to record.startTime.toString(),
                    "endTime" to record.endTime.toString(),
                    "durationMinutes" to ((record.endTime.toEpochMilli() - record.startTime.toEpochMilli()) / 60000),
                    "stages" to record.stages.map { stage ->
                        mapOf(
                            "type" to stage.stage.toString(),
                            "startTime" to stage.startTime.toString(),
                            "endTime" to stage.endTime.toString()
                        )
                    }
                )
            }

            Gson().toJson(sleepData)
        } catch (e: Exception) {
            Gson().toJson(mapOf("error" to e.message))
        }
    }

    /**
     * 起床検知用。直近の睡眠セッションのうち、最も新しい終了時刻だけを軽量に取得する。
     * 過去1日分だけを見れば十分なので、7日分を取得するfetchSleepDataより軽い。
     */
    suspend fun fetchLatestSleepEndTime(): Instant? {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val oneDayAgo = now.minus(java.time.Duration.ofDays(1))

            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(
                        oneDayAgo,
                        now
                    )
                )
            )

            records.records.maxByOrNull { it.endTime }?.endTime
        } catch (e: Exception) {
            null
        }
    }
}
