package cz.mts.phone.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.widget.Toast
import cz.mts.base.extensions.*
import cz.mts.base.helpers.DebugFlag.iSaveDebugMode
import cz.mts.phone.R
import cz.mts.phone.extensions.getHandleToUse
import cz.mts.phone.extensions.telecomManager2
import cz.mts.phone.helpers.CallHelper

class DialerActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null
    private var callNumber: Uri? = null

    // dekódované číslo (z "tel:*31%23123" udělá "*31#123")
    private val number: String
        get() = callNumber?.schemeSpecificPart.orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        val action = intent.action

        // DEBUG: celý intent do schránky hned na začátku, ještě před jakoukoli validací,
        // takže se zaznamená i intent, který se vzápětí odmítne
        if (iSaveDebugMode == 1) {
            dumpIntentToClipboard()
            finish()
            return
        }

        // manifest registruje jen android.intent.action.CALL
        if (action != Intent.ACTION_CALL || data == null ||
            data.scheme?.equals("tel", ignoreCase = true) != true
        ) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        callNumber = data

        // 1) TÍSŇOVÉ ČÍSLO – vždy první, bez blokace, bez dialpadu, bez výchozího dialeru
        if (CallHelper.isEmergency(this, number)) {
            placeEmergencyCall(data)
            finish()
            return
        }

        // 2) INTENT Z JINÉ APLIKACE – otevři vlastní číselník s předvyplněným číslem,
        //    SIM zvolí uživatel
        val isInternalCall = intent.getBooleanExtra(CallHelper.EXTRA_INTERNAL_CALL, false)
        if (!isInternalCall) {
            openDialpad(number)
            finish()
            return
        }

        // 3) VLASTNÍ HOVOR (z launchCallIntentNew), SIM už je vybraná
        if (isDefaultDialer()) {
            initOutgoingCall()
        } else {
            requestDefaultDialerThenCall()
        }
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
    }

    // ------------------------------------------------------------------
    // Výchozí dialer
    // ------------------------------------------------------------------

    /**
     * Aplikace není výchozí dialer, takže její InCallService by hovor nedostal.
     * Požádáme o roli a výsledek přijde do callbacku (BaseSimpleActivity používá
     * Activity Result API, takže onActivityResult se tu nevolá).
     * Callback přijde vždy, i když role není dostupná, takže aktivita nezůstane viset.
     */
    private fun requestDefaultDialerThenCall() {
        launchSetDefaultDialerIntent { granted ->
            if (granted || isDefaultDialer()) {
                initOutgoingCall()
            } else {
                openDefaultAppsSettings()
                finish()
            }
        }
    }

    private fun openDefaultAppsSettings() {
        try {
            hideKeyboard()
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Vytáčení
    // ------------------------------------------------------------------

    private fun openDialpad(number: String) {
        try {
            startActivity(
                Intent(this, DialpadActivity::class.java)
                    .putExtra(CallHelper.EXTRA_PHONE_NUMBER, number)
            )
        } catch (e: Exception) {
            toast("Error")
            copyToClipboard(e.message.toString(), false)
        }
    }

    /**
     * Tísňové volání z DialerActivity. Záložní cesta je záměrně ACTION_DIAL a ne ACTION_CALL:
     * ACTION_CALL by se mohl vrátit do téhle aktivity a zacyklit se.
     */
    @SuppressLint("MissingPermission")
    private fun placeEmergencyCall(uri: Uri) {
        try {
            telecomManager2.placeCall(uri, Bundle())
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, uri))
            } catch (_: Exception) {
                toast("Cannot place emergency call")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initOutgoingCall() {
        try {
            // MMI kódy se neblokují
            if (!CallHelper.isMmiCode(number) && isNumberBlocked(number, getBlockedNumbers())) {
                toast(R.string.calling_blocked_number)
                finish()
                return
            }

            getHandleToUse(intent, number) { handle ->
                try {
                    // handle == null: getHandleToUse už otevřel číselník k výběru SIM
                    if (handle != null) {
                        val extras = Bundle().apply {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                            // klíč očekává int (VideoProfile.STATE_*), ne boolean
                            putInt(
                                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                                VideoProfile.STATE_AUDIO_ONLY
                            )
                            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                        }
                        telecomManager2.placeCall(callNumber, extras)
                    }
                } catch (e: Exception) {
                    toast("Error")
                    copyToClipboard(e.message.toString(), false)
                }
                finish()
            }
        } catch (e: Exception) {
            toast("Error")
            copyToClipboard(e.message.toString(), false)
            finish()
        }
    }

    // ------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------

    /** Zkopíruje do schránky všechno podstatné z příchozího intentu. */
    @Suppress("DEPRECATION")
    private fun dumpIntentToClipboard() {
        val sb = StringBuilder()
        try {
            sb.appendLine("action = ${intent.action}")
            sb.appendLine("data = ${intent.data}")
            sb.appendLine("decoded = ${intent.data?.schemeSpecificPart}")
            sb.appendLine("type = ${intent.type}")
            sb.appendLine("categories = ${intent.categories}")
            sb.appendLine("component = ${intent.component}")
            sb.appendLine("package = ${intent.`package`}")
            sb.appendLine("flags = 0x${Integer.toHexString(intent.flags)}")
            sb.appendLine("referrer = $referrer")
            sb.appendLine("isDefaultDialer = ${isDefaultDialer()}")
            sb.appendLine("sdk = ${Build.VERSION.SDK_INT}")
            sb.appendLine("--- extras ---")

            val extras = intent.extras
            if (extras == null) {
                sb.appendLine("(none)")
            } else {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    sb.appendLine("$key = $value (${value?.javaClass?.simpleName})")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("dump error: ${e.message}")
        }

        try {
            copyToClipboard(sb.toString(), false)
        } catch (_: Exception) {
        }
    }
}
