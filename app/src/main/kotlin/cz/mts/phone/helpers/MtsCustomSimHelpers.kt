package cz.mts.phone.helpers

import android.widget.Toast
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.phone.R
import cz.mts.base.extensions.baseConfig as config

    fun checkCustomSimMts(activity: BaseSimpleActivity, sNumber: String): Int {

       val iRet = checkCustomSimSharedPref(activity, sNumber)
       if (iRet != -4) return iRet

       return -4

   //    try {
   //           //původní metoda, která ukládá PhoneHandle
   //           val PhoneAccountHandleID = activity.MainAppConfig.getCustomSIM(sNumber)
   //           //zatím není nic přiřazeno
  //            if (PhoneAccountHandleID == null) return -4
  //            if (PhoneAccountHandleID.id.indexOf(sActiveSimSlotID[0]) >= 0) return 0
  //            if (PhoneAccountHandleID.id.indexOf(sActiveSimSlotID[1]) >= 0) return 1
  //     } catch (e: Exception) {}
//
 //      return -4
    }

    fun checkCustomSimSharedPref(activity: BaseSimpleActivity, sNumber: String): Int {

    try {
        val sPhoneNumber = normalizeDigitsOnly(sNumber)

        return activity.config.getCustomSim(sPhoneNumber)
    } catch (_: Exception)
    {}
        return -4
    }

    fun removeCustomSimMts(activity: BaseSimpleActivity, sNumber: String,  bToastOn : Boolean = true) {

 //       val PhoneAccountHandleID = activity.MainAppConfig.getCustomSIM(sNumber.normalizePhoneForCompareMTs())
 //       //zatím není nic přiřazeno
 //       if (PhoneAccountHandleID != null) {
 //           activity.MainAppConfig.removeCustomSIM(sNumber.normalizePhoneForCompareMTs())
 //           Toast.makeText(activity, activity.getString(R.string.delete_custom_sim1_mts) + " " + sNumber + " " + activity.getString(R.string.delete_custom_sim2_mts), Toast.LENGTH_SHORT).show()
 //           return
 //       }

        val sPhoneNumber = normalizeDigitsOnly(sNumber)
        activity.config.removeCustomSim(sPhoneNumber)
        if (bToastOn) Toast.makeText(activity, activity.getString(R.string.delete_custom_sim1_mts) + " " + sPhoneNumber + " " + activity.getString(R.string.delete_custom_sim2_mts), Toast.LENGTH_SHORT).show()
    }

    fun saveCustomSimMts(activity: BaseSimpleActivity, sNumber: String, sSimSlot : Int) {

        val sPhoneNumber = normalizeDigitsOnly(sNumber)
        activity.config.saveCustomSim(sPhoneNumber, sSimSlot)
        Toast.makeText(activity, "SIM " + (sSimSlot+1).toString() + " "
            + activity.getString(R.string.save_custom_sim1_mts) + " " + sPhoneNumber + " "
            + activity.getString(R.string.save_custom_sim2_mts), Toast.LENGTH_SHORT).show()

    }
