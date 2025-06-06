package jinproject.aideo.app.update

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import jinproject.aideo.app.update.InAppUpdateManager

class InAppUpdateService: Service() {
    lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        inAppUpdateManager = InAppUpdateManager(
            context = this
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if(::inAppUpdateManager.isInitialized)
            inAppUpdateManager.completeUpdate()
        else
            Toast.makeText(this, "앱 업데이트를 진행할 수 없습니다.", Toast.LENGTH_SHORT).show()

        stopSelf()

        return START_NOT_STICKY
    }

}