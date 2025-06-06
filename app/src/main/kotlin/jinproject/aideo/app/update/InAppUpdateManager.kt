package jinproject.aideo.app.update

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import jinproject.aideo.design.R

class InAppUpdateManager(
    private val context: Context,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    private var installUpdatedListener: InstallStateUpdatedListener? = null

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun checkUpdateIsAvailable(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                requestInAppUpdate(appUpdateInfo, launcher)
            }
        }
    }

    fun checkUpdateIsDownloaded() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                requireInstalling()
            }
        }
    }

    private fun requestInAppUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }

    fun inAppUpdatingLauncherResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            installUpdatedListener = InstallStateUpdatedListener { state ->
                if (state.installStatus() == InstallStatus.DOWNLOADED)
                    requireInstalling()
            }

            installUpdatedListener?.let { listener ->
                appUpdateManager.registerListener(listener)
            }
        }
    }

    private fun requireInstalling() {
        installUpdatedListener?.let { listener ->
            appUpdateManager.unregisterListener(listener)
            installUpdatedListener = null
        }

        notificationManager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }
            ?: makeInAppUpdateNotification()
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    internal fun makeInAppUpdateNotification() {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            "https://aideo_app/inAppUpdate".toUri(),
            context,
            Class.forName("jinproject.aideo.app.MainActivity")
        )

        val deepLinkPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val inAppUpdateIntent = Intent(context, InAppUpdateService::class.java)

        val inAppUpdatePendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_ID,
            inAppUpdateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bitMap =
            ContextCompat.getDrawable(context, R.drawable.img_add_alarm)
                ?.toBitmap()

        val builder = NotificationCompat.Builder(context, "TwomBossAlarm")
            .setContentTitle("인앱 업데이트 준비 완료")
            .setContentText("업데이트를 하시겠어요?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(deepLinkPendingIntent)
            .setAutoCancel(true)
            .setLargeIcon(bitMap)
            .addAction(
                R.drawable.img_add_alarm,
                "업데이트 하기",
                inAppUpdatePendingIntent
            )

        bitMap?.let {
            builder.setSmallIcon(IconCompat.createWithBitmap(it))
        }


        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}