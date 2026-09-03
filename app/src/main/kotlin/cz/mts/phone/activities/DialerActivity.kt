package cz.mts.phone.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import cz.mts.base.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import cz.mts.phone.R
import cz.mts.phone.extensions.getHandleToUse
import cz.mts.base.extensions.getBlockedNumbers
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.hideKeyboard
import cz.mts.base.extensions.isDefaultDialer
import cz.mts.base.extensions.isNumberBlocked
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.telecomManager
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.phone.extensions.clipboardManager

class DialerActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null
    private var callNumber: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_CALL && intent.data != null)
        {
            callNumber = intent.data
            val sIntent =  callNumber.toString() + "\n" + buildString {
                try {
                    intent.extras?.keySet()?.forEach { key ->
                        @Suppress("DEPRECATION")
                        val value = intent.extras?.get(key)
                        append("$key = $value\n")
                    }
                } catch (_: Exception) { }
            }

            if (mtsGlobalAll.iSaveDebugMode == 1) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Intent", sIntent))
            }


            if (!isDefaultDialer()) {
                launchSetDefaultDialerIntent()
            } else {
                initOutgoingCall()
            }
        } else {
            toast(R.string.unknown_error_occurred)
            finish()
        }
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
    }

    @SuppressLint("MissingPermission")
    private fun initOutgoingCall() {
        try {
            if (isNumberBlocked(callNumber.toString().replace("tel:", ""), getBlockedNumbers())) {
                toast(R.string.calling_blocked_number)
                finish()
                return
            }

            getHandleToUse(intent, callNumber.toString()) { handle ->
                if (handle != null) {
                    Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                        telecomManager.placeCall(callNumber, this)
                    }
                }
                finish()
            }
        } catch (e: Exception) {
            mtsGlobalAll.showMyAlertDialog(this, e.message.toString(), "Error", false, 0)
           // showErrorToast(e)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            if (!isDefaultDialer()) {
                try {
                    hideKeyboard()

                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", MY_APP_NAME_GOOGLE_ID, null)
                    }
                    startActivity(intent)
                    toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
                } catch (_: Exception) {
                }
                finish()
            } else {
                initOutgoingCall()
            }
        }
    }

}
