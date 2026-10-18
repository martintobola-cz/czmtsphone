package cz.mts.base.extensions

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
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
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import cz.mts.base.R
import cz.mts.base.helpers.*
import cz.mts.base.helpers.PhoneNumberHelper.isPhoneNumber
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.PhoneNumberHelper.normalizeNumberE164
import cz.mts.base.helpers.PhoneNumberHelper.toE164OrNull
import cz.mts.base.models.BlockedNumber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.getSharedPrefs() = getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

val Context.isRTLLayout: Boolean get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

val Context.areSystemAnimationsEnabled: Boolean get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) > 0f

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
        copyToClipboard(e.message.toString(), true)
        //showErrorToast(e)
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

//fun Context.isOrWasThankYouInstalled(allowPretend: Boolean = true): Boolean {
//    return true
//}

//fun Context.addLockedLabelIfNeeded(stringId: Int): String {
//    return if (isOrWasThankYouInstalled()) {
//        getString(stringId)
//    } else {
//        "${getString(stringId)} (${getString(R.string.feature_locked)})"
//    }
//}

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
fun Context.isDefaultDialer(): Boolean =
    if (isQPlus()) isRoleHeld(RoleManager.ROLE_DIALER)
    else getSystemService(TelecomManager::class.java)?.defaultDialerPackage == packageName

val Context.roleManagerBase: RoleManager?
    get() = if (isQPlus()) getSystemService(RoleManager::class.java) else null

fun Context.isRoleHeld(role: String): Boolean =
    isQPlus() && roleManagerBase?.isRoleHeld(role) == true

fun Context.createRequestRoleIntent(role: String): Intent? =
    if (isQPlus()) {
        roleManagerBase
            ?.takeIf { it.isRoleAvailable(role) }
            ?.createRequestRoleIntent(role)
    } else {
        null
    }

fun Context.createSetDefaultDialerIntent(): Intent? =
    if (isQPlus()) {
        createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
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
            return@getContactsHasMap
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
            // U masky (*, ?, !) je "srovnatelné číslo" přímo text masky, aby nikdy náhodou nevyšlo jako reálné
            // číslo (normalizeDigitsOnly by z "+420?" udělalo "+420"); masce se nehledá jméno v kontaktech.
            val isMask = number.isAnyBlockedNumberMask()
            val comparableNumber = if (isMask) number else normalizeDigitsOnly(normalizedNumber)

            val contactName = if (isMask) null else contacts[comparableNumber]
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
        // U masky (*, ?, !) je "srovnatelné číslo" přímo text masky (viz getBlockedNumbersWithContact)
        val comparableNumber = if (number.isAnyBlockedNumberMask()) number else normalizeDigitsOnly(normalizedNumber)
        val blockedNumber = BlockedNumber(id, number, normalizedNumber, comparableNumber)
        blockedNumbers.add(blockedNumber)
    }

    return blockedNumbers
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.addBlockedNumber(number: String): Boolean {
    val isPattern = number.isAnyBlockedNumberMask()
    return addBlockedNumber(number, isPattern)
}

@TargetApi(Build.VERSION_CODES.N)
fun Context.addBlockedNumber(number: String, isPattern: Boolean): Boolean {
    val value = number.trim()
    if (value.isEmpty()) return false

    // maska (*, ?, nebo negativní !...) se nikdy nesmí uložit jako "přesné číslo", ani když volající pošle isPattern=false
    val storeAsPattern = isPattern || value.isAnyBlockedNumberMask()

    ContentValues().apply {
        if (storeAsPattern) {
            // Masku musíme uložit přesně. normalizeDigitsOnly() by odstranilo ? / ! a u "+" na začátku bez * i to.
            put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, value)
        } else {
            val normalizedOriginal = normalizeDigitsOnly(value)
            if (normalizedOriginal.isEmpty()) return false

            put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, normalizedOriginal)
            if (isPhoneNumber(value)) {
                put(BlockedNumbers.COLUMN_E164_NUMBER, normalizeNumberE164(value, null, false))
            }
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
    // bez role výchozího dialeru provider přístup odmítne -> není co mazat (stejné chování jako dřív)
    if (!isNougatPlus() || !isDefaultDialer()) return true

    // Maže se přímo podle uloženého textu. Dřívější předkontrola přes isNumberBlocked() u masek nefunguje
    // (maska "!+420*" ani "+420?" není "zablokované číslo"), smazání by se tiše přeskočilo.
    val selection = "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
    val deletedRowCount = try {
        contentResolver.delete(BlockedNumbers.CONTENT_URI, selection, arrayOf(number))
    } catch (e: Exception) {
        showErrorToast(e)
        return false
    }
    if (deletedRowCount > 0) return true

    // nic se nesmazalo: v pořádku jen tehdy, když záznam v seznamu už není
    return getBlockedNumbers().none { it.number == number }
}

/**
 * @param region ISO kód země SIM, přes kterou hovor přichází (viz PhoneNumberHelper.resolveRegion);
 *               null = region z jazyka telefonu (nespolehlivé, jen záchrana).
 * @param includeNegativeRules false = negativní pravidla ("!+420*") se přeskočí (např. u odchozích hovorů).
 */
fun Context.isNumberBlocked(
    number: String,
    blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers(),
    region: String? = null,
    includeNegativeRules: Boolean = true,
): Boolean {
    if (!isNougatPlus())  return false
    if (number.isBlank()) return false

    val numberToCompare = normalizeDigitsOnly(number)
    val normalizeE164 = normalizeNumberE164(number, region, false)

    // Přesné shody jen u přesných čísel. Masky (*, ?, !...) se vyhodnocují zvlášť v isNumberBlockedByPattern();
    // jejich uložený text ani e164 sloupec (provider ho může dopočítat i z "+420602123456?") se s číslem srovnávat nesmí.
    val exactMatch = blockedNumbers.any {
        !it.number.isAnyBlockedNumberMask() && (
            normalizeE164 == it.normalizedNumber ||
            numberToCompare == it.numberToCompare ||
            numberToCompare == it.number ||
            number == it.number
        )
    }

    return exactMatch || isNumberBlockedByPattern(number, blockedNumbers, region, includeNegativeRules)
}

/**
 * Masky (*, ?) blokují, když číslo na masku sedí. Negativní pravidla ("!" + maska, např. "!+420*")
 * fungují jako whitelist: číslo se zablokuje, když nesedí na ŽÁDNÉ negativní pravidlo.
 * Negativní pravidla se vyhodnocují jen u čísel, která jdou převést na E.164 (jinak se číslo pustí).
 * Skrytá čísla sem nikdy nepřijdou (řeší je jen vlastní přepínač).
 */
fun Context.isNumberBlockedByPattern(
    number: String,
    blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers(),
    region: String? = null,
    includeNegativeRules: Boolean = true,
): Boolean {
    if (number.isBlank()) return false

    // E.164 jen když je číslo platné pro daný region, jinak null ("nejde převést", nic se nedomýšlí)
    val e164 = toE164OrNull(number, region)

    // Zkusíme původní Caller ID i normalizované varianty. Maska s + tak funguje
    // proti E.164, maska bez + zase proti čistým číslicím.
    val candidates = LinkedHashSet<String>().apply {
        add(number)
        normalizeDigitsOnly(number).takeIf { it.isNotEmpty() }?.let(::add)
        e164?.let(::add)
    }

    val negativeRegexes = ArrayList<Regex>()

    for (blockedNumber in blockedNumbers) {
        val mask = blockedNumber.number
        when {
            mask.isNegativeBlockedNumberMask() -> {
                // maska za "!" (bez vykřičníku); prázdná = neplatné pravidlo, ignoruje se
                val body = mask.substring(1).trim()
                if (body.isNotEmpty()) negativeRegexes.add(blockedNumberMaskToRegex(body))
            }

            mask.isBlockedNumberPattern() || '*' in mask || '?' in mask -> {
                val regex = blockedNumberMaskToRegex(mask)
                if (candidates.any(regex::matches)) {
                    return true
                }
            }
        }
    }

    if (!includeNegativeRules || negativeRegexes.isEmpty()) return false

    // číslo nejde převést na E.164 (krátké / servisní / neplatné) -> negativní pravidla se na něj nevztahují
    if (e164 == null) return false

    // whitelist: zablokovat, pokud číslo nesedí na žádné negativní pravidlo
    return negativeRegexes.none { regex -> candidates.any(regex::matches) }
}
    private fun blockedNumberMaskToRegex(mask: String): Regex {
        val regex = buildString {
            append('^')
            mask.forEach { char ->
                when (char) {
                    '*' -> append(".*") // libovolný počet znaků
                    '?' -> append('.')  // právě jeden znak
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append('$')
        }
        return regex.toRegex()
    }


fun Context.copyToClipboard(text: String, bToast: Boolean = true, label: String = MY_APP_NAME_GOOGLE_ID) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    if (bToast) toast(getString(R.string.value_copied_to_clipboard_show))
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


/**
* Region (ISO 3166-1 alpha-2, velkými písmeny) pro vyložení národních čísel hovoru, jedno místo pro blokování
* i zobrazení (vlajka). Pořadí: SIM přijímající hovor -> síť této SIM -> výchozí SIM / síť telefonu
* (jen když konkrétní SIM nejde určit, např. handle == null) -> jazyk telefonu jako poslední záchrana.
*
* Kódy ze systému jsou malými písmeny, PhoneNumberHelper.resolveRegion() je převede a neznámé zahodí.
* Při chybě (např. SecurityException bez READ_PHONE_STATE) se spadne na jazyk telefonu.
*/

fun Context.getCallRegion(handle: PhoneAccountHandle? = null): String {
    return try {
        val telephony = getSystemService(TelephonyManager::class.java)
        val perSim = if (telephony != null && handle != null) { //&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephony.createForPhoneAccountHandle(handle)
        } else {
            null
        }

        PhoneNumberHelper.resolveRegion(
            perSim?.simCountryIso,
            perSim?.networkCountryIso,
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
        )
    } catch (_: Exception) {
        PhoneNumberHelper.resolveRegion()
    }
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
        if (isTiramisuPlus()) {
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
