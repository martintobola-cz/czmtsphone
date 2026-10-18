package cz.mts.phone.extensions

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.os.Vibrator
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.inputmethod.InputMethodManager
import cz.mts.base.extensions.baseConfig
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isSPlus
import cz.mts.base.helpers.isUpsideDownCakePlus
import cz.mts.phone.models.SIMAccount
import java.util.Locale

//val Context.config: Config
//    get() = Config.newInstance(applicationContext)

fun Context.canUseFullScreenIntent(): Boolean {
    return !isUpsideDownCakePlus() || notificationManager.canUseFullScreenIntent()
}


val Context.telephonyService: TelephonyManager
    get() = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

val Context.telecomManager2: TelecomManager
    get() = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

val Context.vibrator: Vibrator?
    get() = getSystemService(Vibrator::class.java)

val Context.smsManager: SmsManager?
    get() = if (isSPlus()) getSystemService(SmsManager::class.java)
    else @Suppress("DEPRECATION") SmsManager.getDefault()

val Context.clipboardManager: ClipboardManager
    get() = getSystemService(ClipboardManager::class.java)

val Context.shortcutManager: ShortcutManager
    get() = getSystemService(ShortcutManager::class.java)

val Context.audioManager: AudioManager
    get() = getSystemService(AudioManager::class.java)

val Context.notificationManager: NotificationManager
    get() = getSystemService(NotificationManager::class.java)

val Context.powerManager: PowerManager
    get() = getSystemService(PowerManager::class.java)

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(KeyguardManager::class.java)

val Context.appOpsManager: AppOpsManager
    get() = getSystemService(AppOpsManager::class.java)

val Context.telephonyManager: TelephonyManager?
    get() = getSystemService(TelephonyManager::class.java)

val Context.telecomManager: TelecomManager?
    get() = getSystemService(TelecomManager::class.java)

val Context.subscriptionManager: SubscriptionManager?
    get() = getSystemService(SubscriptionManager::class.java)

val Context.roleManager: RoleManager?
    get() = if (isQPlus()) getSystemService(RoleManager::class.java) else null

val Context.inputMethodManager: InputMethodManager?
    get() = getSystemService(InputMethodManager::class.java)

val Context.isDefaultCallScreeningApp: Boolean
    get() = if (isQPlus()) roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
            else false

// Vrací true, pokud je režim Nerušit (DND) aktivní
val Context.isDndActive: Boolean
    get() = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

val Context.appVersionCode: Long
    get() = packageManager.getPackageInfo(packageName, 0).longVersionCode

val Context.appVersionName: String?
    get() = packageManager.getPackageInfo(packageName, 0).versionName

private fun resolveSimColor(index: Int, context: Context, phoneAccount: PhoneAccount, subInfo: SubscriptionInfo?): Int {
    // Vlastní barvy simek z custom tématu
    if (context.baseConfig.useCustomSimColor && context.baseConfig.themeIdSaved == 5) {
        return if (index == 0) context.baseConfig.customSIM1Color
        else context.baseConfig.customSIM2Color
    }

    // Systémové barvy (mnoho vendorů nevrací — proto custom možnost výše)
    if (phoneAccount.highlightColor != 0) return phoneAccount.highlightColor
    if (subInfo?.iconTint != null && subInfo.iconTint != 0) return subInfo.iconTint

    // Fallback — deterministická barva podle subscriptionId
    val seed = subInfo?.subscriptionId ?: phoneAccount.hashCode()
    val palette = listOf(
        0xFFE53935.toInt(),
        0xFF1E88E5.toInt(),
        0xFF43A047.toInt(),
        0xFFFDD835.toInt(),
        0xFF8E24AA.toInt(),
        0xFFFB8C00.toInt()
    )
    return palette[kotlin.math.abs(seed) % palette.size]
}

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val simAccounts = mutableListOf<SIMAccount>()
    try {
        val activeSubs = subscriptionManager?.activeSubscriptionInfoList

        telecomManager?.callCapablePhoneAccounts?.forEachIndexed { index, account ->
            val phoneAccount = telecomManager?.getPhoneAccount(account) ?: return@forEachIndexed
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()

            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            val type = if (phoneAccount.extras?.getBoolean("isEsim", false) == true) 1 else 0
            val subInfo = activeSubs?.getOrNull(index)
            val countryISO = subInfo?.countryIso?.uppercase(Locale.US).orEmpty()

            simAccounts.add(
                SIMAccount(
                    subscriptionId = subInfo?.subscriptionId ?: -123456789,
                    indexid = index + 1,
                    handle = phoneAccount.accountHandle,
                    label = label,
                    phoneNumber = address.substringAfter("tel:"),
                    color = resolveSimColor(index, this, phoneAccount, subInfo),
                    type = type,
                    countryISO = countryISO
                )
            )
        }
    } catch (_: Exception) {
    }
    return simAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        (telecomManager?.callCapablePhoneAccounts?.size ?: 0) > 1
    } catch (_: Exception) {
        false
    }
}

@SuppressLint("MissingPermission")
fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        try {
            telecomManager?.cancelMissedCallsNotification()
        } catch (_: Exception) {
        }
    }
}

fun Context.canLaunchAccountsConfiguration(): Boolean {
    return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
        .resolveActivity(packageManager) != null
}

fun Context.launchAccountsConfiguration() {
    val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    }
}
