package cz.mts.base.extensions

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ShortcutManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.ContactsContract.CommonDataKinds.BaseTypes
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.OpenableColumns
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import cz.mts.base.R
import cz.mts.base.helpers.AppLockManager
import cz.mts.base.helpers.BaseConfig
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.DAY_SECONDS
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.base.helpers.HOUR_SECONDS
import cz.mts.base.helpers.KEY_MAILTO
import cz.mts.base.helpers.MINUTE_SECONDS
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.base.helpers.PERMISSION_ACCESS_COARSE_LOCATION
import cz.mts.base.helpers.PERMISSION_ACCESS_FINE_LOCATION
import cz.mts.base.helpers.PERMISSION_CALL_PHONE
import cz.mts.base.helpers.PERMISSION_CAMERA
import cz.mts.base.helpers.PERMISSION_GET_ACCOUNTS
import cz.mts.base.helpers.PERMISSION_MEDIA_LOCATION
import cz.mts.base.helpers.PERMISSION_POST_NOTIFICATIONS
import cz.mts.base.helpers.PERMISSION_READ_CALENDAR
import cz.mts.base.helpers.PERMISSION_READ_CALL_LOG
import cz.mts.base.helpers.PERMISSION_READ_CONTACTS
import cz.mts.base.helpers.PERMISSION_READ_MEDIA_AUDIO
import cz.mts.base.helpers.PERMISSION_READ_MEDIA_IMAGES
import cz.mts.base.helpers.PERMISSION_READ_MEDIA_VIDEO
import cz.mts.base.helpers.PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED
import cz.mts.base.helpers.PERMISSION_READ_PHONE_STATE
import cz.mts.base.helpers.PERMISSION_READ_SMS
import cz.mts.base.helpers.PERMISSION_READ_STORAGE
import cz.mts.base.helpers.PERMISSION_READ_SYNC_SETTINGS
import cz.mts.base.helpers.PERMISSION_RECORD_AUDIO
import cz.mts.base.helpers.PERMISSION_SEND_SMS
import cz.mts.base.helpers.PERMISSION_WRITE_CALENDAR
import cz.mts.base.helpers.PERMISSION_WRITE_CALL_LOG
import cz.mts.base.helpers.PERMISSION_WRITE_CONTACTS
import cz.mts.base.helpers.PERMISSION_WRITE_STORAGE
import cz.mts.base.helpers.PREFS_KEY
import cz.mts.base.helpers.PhoneNumberHelper.isPhoneNumber
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.PhoneNumberHelper.normalizeNumberE164
import cz.mts.base.helpers.TIME_FORMAT_12
import cz.mts.base.helpers.TIME_FORMAT_24
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.helpers.isNougatPlus
import cz.mts.base.helpers.isOnMainThread
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isUpsideDownCakePlus
import cz.mts.base.models.BlockedNumber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.getSharedPrefs() = getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

val Context.isRTLLayout: Boolean get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

val Context.areSystemAnimationsEnabled: Boolean get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) > 0f

val Context.appLockManager
    get() = AppLockManager.getInstance(applicationContext as Application)

fun Context.sendEmailIntent(recipient: String) {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.fromParts(KEY_MAILTO, recipient, null)
        launchActivityIntent(this)
    }
}

fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(getString(id), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            doToast(this, msg, length)
        } else {
            Handler(Looper.getMainLooper()).post {
                doToast(this, msg, length)
            }
        }
    } catch (_: Exception) {
    }
}

private fun doToast(context: Context, message: String, length: Int) {
    if (context is Activity) {
        if (!context.isFinishing && !context.isDestroyed) {
            Toast.makeText(context, message, length).show()
        }
    } else {
        Toast.makeText(context, message, length).show()
    }
}

fun Context.showErrorToast(msg: String, length: Int = Toast.LENGTH_LONG) {
    toast(String.format(getString(R.string.error), msg), length)
}

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    showErrorToast(exception.toString(), length)
}

val Context.baseConfig: BaseConfig get() = BaseConfig.newInstance(this)
val Context.sdCardPath: String get() = baseConfig.sdCardPath
val Context.internalStoragePath: String get() = baseConfig.internalStoragePath
val Context.otgPath: String get() = baseConfig.OTGPath

fun Context.hasPermission(permId: Int) = ContextCompat.checkSelfPermission(this, getPermissionString(permId)) == PERMISSION_GRANTED

fun Context.getPermissionString(id: Int) = when (id) {
    PERMISSION_READ_STORAGE -> Manifest.permission.READ_EXTERNAL_STORAGE
    PERMISSION_WRITE_STORAGE -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    PERMISSION_CAMERA -> Manifest.permission.CAMERA
    PERMISSION_RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
    PERMISSION_READ_CONTACTS -> Manifest.permission.READ_CONTACTS
    PERMISSION_WRITE_CONTACTS -> Manifest.permission.WRITE_CONTACTS
    PERMISSION_READ_CALENDAR -> Manifest.permission.READ_CALENDAR
    PERMISSION_WRITE_CALENDAR -> Manifest.permission.WRITE_CALENDAR
    PERMISSION_CALL_PHONE -> Manifest.permission.CALL_PHONE
    PERMISSION_READ_CALL_LOG -> Manifest.permission.READ_CALL_LOG
    PERMISSION_WRITE_CALL_LOG -> Manifest.permission.WRITE_CALL_LOG
    PERMISSION_GET_ACCOUNTS -> Manifest.permission.GET_ACCOUNTS
    PERMISSION_READ_SMS -> Manifest.permission.READ_SMS
    PERMISSION_SEND_SMS -> Manifest.permission.SEND_SMS
    PERMISSION_READ_PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
    PERMISSION_MEDIA_LOCATION -> if (isQPlus()) Manifest.permission.ACCESS_MEDIA_LOCATION else ""
    PERMISSION_POST_NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
    PERMISSION_READ_MEDIA_IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
    PERMISSION_READ_MEDIA_VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
    PERMISSION_READ_MEDIA_AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
    PERMISSION_ACCESS_COARSE_LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
    PERMISSION_ACCESS_FINE_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
    PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    PERMISSION_READ_SYNC_SETTINGS -> Manifest.permission.READ_SYNC_SETTINGS
    else -> ""
}

fun Context.launchActivityIntent(intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(R.string.no_app_found)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit
) {
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) {
            showErrorToast(e)
        }
    }
}


fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>,
    queryArgs: Bundle,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit
) {
    try {
        val cursor = contentResolver.query(uri, projection, queryArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) {
            showErrorToast(e)
        }
    }
}


fun Context.getSizeFromContentUri(uri: Uri): Long {
    val projection = arrayOf(OpenableColumns.SIZE)
    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getLongValue(OpenableColumns.SIZE)
            }
        }
    } catch (e: Exception) {
    }
    return 0L
}

fun Context.getCurrentFormattedDateTime(): String {
    val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return simpleDateFormat.format(Date(System.currentTimeMillis()))
}

fun Context.updateSDCardPath() {
    ensureBackgroundThread {
        val oldPath = baseConfig.sdCardPath
        baseConfig.sdCardPath = getSDCardPath()
        if (oldPath != baseConfig.sdCardPath) {
            baseConfig.sdTreeUri = ""
        }
    }
}


fun Context.isOrWasThankYouInstalled(allowPretend: Boolean = true): Boolean {
    return true
}


fun Context.addLockedLabelIfNeeded(stringId: Int): String {
    return if (isOrWasThankYouInstalled()) {
        getString(stringId)
    } else {
        "${getString(stringId)} (${getString(R.string.feature_locked)})"
    }
}


fun Context.formatSecondsToShortTimeString(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""

    val units = arrayOf(
        DAY_SECONDS to R.string.days_letter,
        HOUR_SECONDS to R.string.hours_letter,
        MINUTE_SECONDS to R.string.minutes_letter,
        1 to R.string.seconds_letter
    )

    var remaining = totalSeconds
    val parts = ArrayList<String>(4)

    for ((unitSeconds, resId) in units) {
        val value = remaining / unitSeconds
        remaining %= unitSeconds

        if (value > 0) {
            parts.add(
                String.format(resources.getString(resId), value)
            )
        }
    }

    return parts.joinToString(" ")
}

fun Context.getTimeFormat() = if (baseConfig.use24HourFormat) TIME_FORMAT_24 else TIME_FORMAT_12



fun Context.getFontSizeText() = getString(
    when (baseConfig.fontSize) {
        FONT_SIZE_SMALL -> R.string.small
        FONT_SIZE_MEDIUM -> R.string.medium
        FONT_SIZE_LARGE -> R.string.large
        else -> R.string.extra_large
    }
)

fun Context.getTextSize() = when (baseConfig.fontSize) {
    FONT_SIZE_SMALL -> resources.getDimension(R.dimen.smaller_text_size)
    FONT_SIZE_MEDIUM -> resources.getDimension(R.dimen.bigger_text_size)
    FONT_SIZE_LARGE -> resources.getDimension(R.dimen.big_text_size)
    else -> resources.getDimension(R.dimen.extra_big_text_size)
}

val Context.telecomManager: TelecomManager get() = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
val Context.windowManager: WindowManager get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager
val Context.notificationManager: NotificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
val Context.shortcutManager: ShortcutManager get() = getSystemService(ShortcutManager::class.java) as ShortcutManager


fun Context.isDefaultDialer(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.defaultDialerPackage == packageName
    }
}

fun Context.getContactsHasMap(withComparableNumbers: Boolean, callback: (HashMap<String, String>) -> Unit) {
    ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contactList ->
        val privateContacts: HashMap<String, String> = HashMap()
        for (contact in contactList) {
            for (phoneNumber in contact.phoneNumbers) {
                val number = normalizeDigitsOnly(phoneNumber.value)
                privateContacts[number] = contact.getNameToDisplay()
            }
        }
        callback(privateContacts)
    }
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.getBlockedNumbersWithContact(callback: (ArrayList<BlockedNumber>) -> Unit) {
    getContactsHasMap(true) { contacts ->
        val blockedNumbers = ArrayList<BlockedNumber>()
        if (!isNougatPlus() || !isDefaultDialer()) {
            callback(blockedNumbers)
        }

        val uri = BlockedNumbers.CONTENT_URI
        val projection = arrayOf(
            BlockedNumbers.COLUMN_ID,
            BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            BlockedNumbers.COLUMN_E164_NUMBER,
        )

        queryCursor(uri, projection) { cursor ->
            val id = cursor.getLongValue(BlockedNumbers.COLUMN_ID)
            val number = cursor.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER) ?: ""
            val normalizedNumber = cursor.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER) ?: number
            val comparableNumber = normalizeDigitsOnly(normalizedNumber)

            val contactName = contacts[comparableNumber]
            val blockedNumber = BlockedNumber(id, number, normalizedNumber, comparableNumber, contactName)
            blockedNumbers.add(blockedNumber)
        }

        val blockedNumbersPair = blockedNumbers.partition { it.contactName != null }
        val blockedNumbersWithNameSorted = blockedNumbersPair.first.sortedBy { it.contactName }
        val blockedNumbersNoNameSorted = blockedNumbersPair.second.sortedBy { it.number }

        callback(ArrayList(blockedNumbersWithNameSorted + blockedNumbersNoNameSorted))
    }
}

fun Context.hasAnyBlockedNumber(): Boolean {
    return getBlockedNumbers().isNotEmpty()
}
@TargetApi(Build.VERSION_CODES.N)
fun Context.getBlockedNumbers(): ArrayList<BlockedNumber> {
    val blockedNumbers = ArrayList<BlockedNumber>()
    if (!isNougatPlus() || !isDefaultDialer()) {
        return blockedNumbers
    }

    val uri = BlockedNumbers.CONTENT_URI
    val projection = arrayOf(
        BlockedNumbers.COLUMN_ID,
        BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        BlockedNumbers.COLUMN_E164_NUMBER
    )

    queryCursor(uri, projection) { cursor ->
        val id = cursor.getLongValue(BlockedNumbers.COLUMN_ID)
        val number = cursor.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER) ?: ""
        val normalizedNumber = cursor.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER) ?: number
        val comparableNumber = normalizeDigitsOnly(normalizedNumber)
        val blockedNumber = BlockedNumber(id, number, normalizedNumber, comparableNumber)
        blockedNumbers.add(blockedNumber)
    }

    return blockedNumbers
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.addBlockedNumber(number: String): Boolean {
    ContentValues().apply {
        put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, normalizeDigitsOnly(number))
        if (isPhoneNumber(number)) {
            put(BlockedNumbers.COLUMN_E164_NUMBER, normalizeNumberE164(number, null, false))
        }
        try {
            contentResolver.insert(BlockedNumbers.CONTENT_URI, this)
        } catch (e: Exception) {
            showErrorToast(e)
            return false
        }
    }
    return true
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.deleteBlockedNumber(number: String): Boolean {
    val selection = "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
    val selectionArgs = arrayOf(number)

    return if (isNumberBlocked(number)) {
        val deletedRowCount = contentResolver.delete(BlockedNumbers.CONTENT_URI, selection, selectionArgs)

        deletedRowCount > 0
    } else {
        true
    }
}

fun Context.isNumberBlocked(number: String, blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers()): Boolean {
    if (!isNougatPlus())  return false
    if (number.isBlank()) return false

    val numberToCompare = normalizeDigitsOnly(number)
    val normalizeE164 = normalizeNumberE164(number, null, false)

    return blockedNumbers.any {
            normalizeE164 == it.normalizedNumber ||
            numberToCompare == it.numberToCompare ||
            numberToCompare == it.number ||
            number == it.number
    } || isNumberBlockedByPattern(number, blockedNumbers)
}

fun Context.isNumberBlockedByPattern(number: String, blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers()): Boolean {
    for (blockedNumber in blockedNumbers) {
        val num = blockedNumber.number
        if (num.isBlockedNumberPattern()) {
            val pattern = num.replace("+", "\\+").replace("*", ".*")
            if (number.matches(pattern.toRegex())) {
                return true
            }
        }
    }
    return false
}

fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText(MY_APP_NAME_GOOGLE_ID, text)
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    val toastText = String.format(getString(R.string.value_copied_to_clipboard_show), text)
    toast(toastText)
}

fun Context.getPhoneNumberTypeText(type: Int, label: String): String {
    return if (type == BaseTypes.TYPE_CUSTOM) {
        label
    } else {
        getString(
            when (type) {
                Phone.TYPE_MOBILE -> R.string.mobile
                Phone.TYPE_HOME -> R.string.home
                Phone.TYPE_WORK -> R.string.work
                Phone.TYPE_MAIN -> R.string.main_number
                Phone.TYPE_FAX_WORK -> R.string.work_fax
                Phone.TYPE_FAX_HOME -> R.string.home_fax
                Phone.TYPE_PAGER -> R.string.pager
                else -> R.string.other
            }
        )
    }
}

fun Context.updateBottomTabItemColors(view: View?, isActive: Boolean, drawableId: Int? = null, navbarColor : Int) {
    var color = if (isActive) {
        getProperPrimaryColor()
    } else {
        getProperTextColor()
    }

    if (navbarColor == color){
        color = getProperBackgroundColor()
    }
    if (navbarColor == color){
        color = color.adjustColor(24)
    }

    if (drawableId != null) {
        val drawable = ResourcesCompat.getDrawable(resources, drawableId, theme)
        view?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(drawable)
    }

    view?.findViewById<ImageView>(R.id.tab_item_icon)?.applyColorFilter(color)
    view?.findViewById<TextView>(R.id.tab_item_label)?.setTextColor(color)
}

fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    startActivity(intent)
}

fun Context.getTempFile(folderName: String, filename: String): File? {
    val folder = File(cacheDir, folderName)
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            toast(R.string.unknown_error_occurred)
            return null
        }
    }

    return File(folder, filename)
}

fun Context.openDeviceSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }

    try {
        startActivity(intent)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.canUseFullScreenIntent(): Boolean {
    return !isUpsideDownCakePlus() || notificationManager.canUseFullScreenIntent()
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun Context.openFullScreenIntentSettings(appId: String) {
    if (isUpsideDownCakePlus()) {
        try {
            val uri = Uri.fromParts("package", appId, null)
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = uri
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // fallback – třeba otevřít notifikační nastavení
            openNotificationSettings()
        }
    }
}

fun Context.isAppInstalled(packageName: String): Boolean {
    val pm = this.packageManager
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
