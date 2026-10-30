package org.stypox.dicio.skills.checkin

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today's steps from Health Connect, which Fit, Fitbit and the phone itself write to. */
object HealthSteps {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** Steps, plus reading them in the background for the walk reminders where the phone allows it. */
    fun permissionsToRequest(context: Context): Set<String> {
        val client = HealthConnectClient.getOrCreate(context)
        val background = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (background) PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        else PERMISSIONS
    }

    suspend fun today(context: Context): Section<Long> {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return Section.Unavailable
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
                return Section.NeedsAccess
            }
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val result = client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, Instant.now()))
            )
            Section.Ready(result[StepsRecord.COUNT_TOTAL] ?: 0L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read steps", e)
            Section.Unavailable
        }
    }

    private val TAG = HealthSteps::class.simpleName
}
