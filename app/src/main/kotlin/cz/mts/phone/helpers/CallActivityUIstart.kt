package cz.mts.phone.helpers

import android.content.Context
import android.content.Intent
import android.telecom.Call
import cz.mts.base.helpers.CALLUUID
import cz.mts.phone.activities.CallActivity

object CallActivityUIstart {

   fun getStartIntent(context: Context, putExtra : String, sUUIDcall : String? = null): Intent {

       val activityIntent = Intent(context, CallActivity::class.java).apply {
           addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
           addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//           addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) //u singleInstance nemá smysl, rozbíjí návratové chování jiných aktivit
//           addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) //u singleInstance nemá význam, CallActivity je vždy „front“
           putExtra(putExtra, true)
           if (!sUUIDcall.isNullOrEmpty())
               putExtra(CALLUUID,sUUIDcall)
       }
       return activityIntent
   }

    //start fullscreen activity je i v class CallNotificationManagerMTs2
    //jako builder.setFullScreenIntent(openAppPendingIntent, true)
    fun showInCallUI(context : Context, putExtra : String, call : Call?) : Boolean {
        var bRet = false
        var putExtraMy = putExtra
        var sUUIDcal = ""

        if (call != null) {
            val sUUIDcal2 = CallManager.getIdByCall(call) ?: ""
            if (sUUIDcal2.isNotEmpty()) {
                putExtraMy = CALLUUID
                sUUIDcal = sUUIDcal2
            }
        }

        if (CallManager.getAliveCallsCount() > 0 || CallActivityUI.isVisible() || CallActivityUI.isPaused()) {
            val activityIntent = getStartIntent(context, putExtraMy, sUUIDcal)
            try {
                //CallActivityUI.markLaunching()
                context.startActivity(activityIntent)
                bRet = true
            } catch (_: Exception) {
                CallActivityUI.markDestroyed()
            }
        }
        return bRet
    }
}

