package cz.mts.base.helpers

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import cz.mts.base.R
import cz.mts.base.extensions.getInternalStoragePath
import cz.mts.base.extensions.getSDCardPath
import cz.mts.base.extensions.getSharedPrefs
import cz.mts.base.extensions.sharedPreferencesCallback
import cz.mts.base.models.SpeedDial
//import android.telecom.PhoneAccountHandle
//import cz.mts.phone.extensions.getPhoneAccountHandleModel
//import cz.mts.phone.extensions.putPhoneAccountHandle
//import cz.mts.base.helpers.PhoneNumberHelper
import java.text.SimpleDateFormat
import java.util.LinkedList
import kotlin.reflect.KProperty0

open class BaseConfig(val context: Context) {
    protected val prefs = context.getSharedPrefs()

    companion object {
        fun newInstance(context: Context) = BaseConfig(context)
        private val gson = Gson()
    }

    var appRunCount: Int
        get() = prefs.getInt(APP_RUN_COUNT, 0)
        set(appRunCount) = prefs.edit().putInt(APP_RUN_COUNT, appRunCount).apply()

    var primaryAndroidDataTreeUri: String
        get() = prefs.getString(PRIMARY_ANDROID_DATA_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(PRIMARY_ANDROID_DATA_TREE_URI, uri).apply()

    var sdAndroidDataTreeUri: String
        get() = prefs.getString(SD_ANDROID_DATA_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(SD_ANDROID_DATA_TREE_URI, uri).apply()

    var lastUsedSmsTemplate: String
        get() = prefs.getString(LAST_USED_SMS_TEMPLATE, "")!!
        set(value) = prefs.edit().putString(LAST_USED_SMS_TEMPLATE, value).apply()

    var otgAndroidDataTreeUri: String
        get() = prefs.getString(OTG_ANDROID_DATA_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(OTG_ANDROID_DATA_TREE_URI, uri).apply()

    var primaryAndroidObbTreeUri: String
        get() = prefs.getString(PRIMARY_ANDROID_OBB_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(PRIMARY_ANDROID_OBB_TREE_URI, uri).apply()

    var sdAndroidObbTreeUri: String
        get() = prefs.getString(SD_ANDROID_OBB_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(SD_ANDROID_OBB_TREE_URI, uri).apply()

    var otgAndroidObbTreeUri: String
        get() = prefs.getString(OTG_ANDROID_OBB_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(OTG_ANDROID_OBB_TREE_URI, uri).apply()

    var sdTreeUri: String
        get() = prefs.getString(SD_TREE_URI, "")!!
        set(uri) = prefs.edit().putString(SD_TREE_URI, uri).apply()

    var OTGTreeUri: String
        get() = prefs.getString(OTG_TREE_URI, "")!!
        set(OTGTreeUri) = prefs.edit().putString(OTG_TREE_URI, OTGTreeUri).apply()

    var OTGPartition: String
        get() = prefs.getString(OTG_PARTITION, "")!!
        set(OTGPartition) = prefs.edit().putString(OTG_PARTITION, OTGPartition).apply()

    var OTGPath: String
        get() = prefs.getString(OTG_REAL_PATH, "")!!
        set(OTGPath) = prefs.edit().putString(OTG_REAL_PATH, OTGPath).apply()

    var sdCardPath: String
        get() = prefs.getString(SD_CARD_PATH, getDefaultSDCardPath())!!
        set(sdCardPath) = prefs.edit().putString(SD_CARD_PATH, sdCardPath).apply()

    private fun getDefaultSDCardPath() = if (prefs.contains(SD_CARD_PATH)) "" else context.getSDCardPath()

    var internalStoragePath: String
        get() = prefs.getString(INTERNAL_STORAGE_PATH, getDefaultInternalPath())!!
        set(internalStoragePath) = prefs.edit().putString(INTERNAL_STORAGE_PATH, internalStoragePath).apply()

    private fun getDefaultInternalPath() = if (prefs.contains(INTERNAL_STORAGE_PATH)) "" else context.getInternalStoragePath()

    var textColor: Int
        get() = prefs.getInt(TEXT_COLOR, ContextCompat.getColor(context, R.color.default_text_color))
        set(textColor) = prefs.edit().putInt(TEXT_COLOR, textColor).apply()

    var backgroundColor: Int
        get() = prefs.getInt(BACKGROUND_COLOR, ContextCompat.getColor(context, R.color.default_background_color))
        set(backgroundColor) = prefs.edit().putInt(BACKGROUND_COLOR, backgroundColor).apply()

    var primaryColor: Int
        get() = prefs.getInt(PRIMARY_COLOR, ContextCompat.getColor(context, R.color.default_primary_color))
        set(primaryColor) = prefs.edit().putInt(PRIMARY_COLOR, primaryColor).apply()

    var accentColor: Int
        get() = prefs.getInt(ACCENT_COLOR, ContextCompat.getColor(context, R.color.default_accent_color))
        set(accentColor) = prefs.edit().putInt(ACCENT_COLOR, accentColor).apply()

    var lastHandledShortcutColor: Int
        get() = prefs.getInt(LAST_HANDLED_SHORTCUT_COLOR, 1)
        set(lastHandledShortcutColor) = prefs.edit().putInt(LAST_HANDLED_SHORTCUT_COLOR, lastHandledShortcutColor).apply()

    var appIconColor: Int
        get() = prefs.getInt(APP_ICON_COLOR, ContextCompat.getColor(context, R.color.default_app_icon_color))
        set(appIconColor) {
            isUsingModifiedAppIcon = appIconColor != ContextCompat.getColor(context, R.color.color_primary)
            prefs.edit().putInt(APP_ICON_COLOR, appIconColor).apply()
        }

    var navBarColor: Int
        get() = prefs.getInt(APP_NAVBAR_COLOR, ContextCompat.getColor(context, R.color.default_app_icon_color))
        set(appNavBarColor) {
            prefs.edit().putInt(APP_NAVBAR_COLOR, appNavBarColor).apply()
        }

    var lastIconColor: Int
        get() = prefs.getInt(LAST_ICON_COLOR, ContextCompat.getColor(context, R.color.color_primary))
        set(lastIconColor) = prefs.edit().putInt(LAST_ICON_COLOR, lastIconColor).apply()

    var customThemeInitialized: Boolean
        get() = prefs.getBoolean(CUSTOM_THEME_INIT, false)
        set(customThemeInitialized) = prefs.edit().putBoolean(CUSTOM_THEME_INIT, customThemeInitialized).apply()

    var customTextColor: Int
        get() = prefs.getInt(CUSTOM_TEXT_COLOR, textColor)
        set(customTextColor) = prefs.edit().putInt(CUSTOM_TEXT_COLOR, customTextColor).apply()

    var customBackgroundColor: Int
        get() = prefs.getInt(CUSTOM_BACKGROUND_COLOR, backgroundColor)
        set(customBackgroundColor) = prefs.edit().putInt(CUSTOM_BACKGROUND_COLOR, customBackgroundColor).apply()

    var easterEggMode: Boolean
        get() = prefs.getBoolean("Easter_egg_mode", false)
        set(easterEggMode) {
            prefs.edit().putBoolean("Easter_egg_mode", easterEggMode).commit()
        }

    var showNews: Int
        get() = prefs.getInt("showNews_mts", 0)
        set(showNews) {
            prefs.edit().putInt("showNews_mts", showNews).apply()
        }

    var customPrimaryColor: Int
        get() = prefs.getInt(CUSTOM_PRIMARY_COLOR, primaryColor)
        set(customPrimaryColor) = prefs.edit().putInt(CUSTOM_PRIMARY_COLOR, customPrimaryColor).apply()

    var customAccentColor: Int
        get() = prefs.getInt(CUSTOM_ACCENT_COLOR, accentColor)
        set(customAccentColor) = prefs.edit().putInt(CUSTOM_ACCENT_COLOR, customAccentColor).apply()

    var customAppIconColor: Int
        get() = prefs.getInt(CUSTOM_APP_ICON_COLOR, appIconColor)
        set(customAppIconColor) = prefs.edit().putInt(CUSTOM_APP_ICON_COLOR, customAppIconColor).apply()

    var customNavBarColor: Int
        get() = prefs.getInt(CUSTOM_NAVBAR_COLOR, navBarColor)
        set(customNavBarColor) = prefs.edit().putInt(CUSTOM_NAVBAR_COLOR, customNavBarColor).apply()

    var customSIM1Color: Int
        get() = prefs.getInt(CUSTOM_SIM1_COLOR, primaryColor)
        set(customSIM1Color) = prefs.edit().putInt(CUSTOM_SIM1_COLOR, customSIM1Color).apply()

    var customSIM2Color: Int
        get() = prefs.getInt(CUSTOM_SIM2_COLOR, appIconColor)
        set(customSIM2Color) = prefs.edit().putInt(CUSTOM_SIM2_COLOR, customSIM2Color).apply()

    var themeIdSaved: Int
        get() = prefs.getInt(BASE_THEME, 1) //DARK THEME default
        set(themeIdSaved) = prefs.edit().putInt(BASE_THEME, themeIdSaved).apply()

    var themeChanged: Boolean
        get() = prefs.getBoolean(THEME_CHANGED, false)
        set(themeChanged) = prefs.edit().putBoolean(THEME_CHANGED, themeChanged).apply()

    var useCustomSimColor: Boolean
        get() = prefs.getBoolean(CUSTOM_SIM_ICON_COLOR, false)
        set(useCustomSimColor) = prefs.edit().putBoolean(CUSTOM_SIM_ICON_COLOR, useCustomSimColor).apply()

    var isAppPasswordProtectionOn: Boolean
        get() = prefs.getBoolean(APP_PASSWORD_PROTECTION, false)
        set(isAppPasswordProtectionOn) = prefs.edit().putBoolean(APP_PASSWORD_PROTECTION, isAppPasswordProtectionOn).apply()

    var lastUnlockTimestampMs: Long
        get() = prefs.getLong(LAST_UNLOCK_TIMESTAMP_MS, 0L)
        set(value) = prefs.edit().putLong(LAST_UNLOCK_TIMESTAMP_MS, value).apply()

    var unlockTimeoutDurationMs: Long
        get() = prefs.getLong(UNLOCK_TIMEOUT_DURATION_MS, DEFAULT_UNLOCK_TIMEOUT_DURATION)
        set(value) = prefs.edit().putLong(UNLOCK_TIMEOUT_DURATION_MS, value).apply()

    fun getFolderProtectionType(path: String) = prefs.getInt("$PROTECTED_FOLDER_TYPE$path", PROTECTION_NONE)

    var lastCopyPath: String
        get() = prefs.getString(LAST_COPY_PATH, "")!!
        set(lastCopyPath) = prefs.edit().putString(LAST_COPY_PATH, lastCopyPath).apply()

    var keepLastModified: Boolean
        get() = prefs.getBoolean(KEEP_LAST_MODIFIED, true)
        set(keepLastModified) = prefs.edit().putBoolean(KEEP_LAST_MODIFIED, keepLastModified).apply()

    var useEnglish: Boolean
        get() = prefs.getBoolean(USE_ENGLISH, false)
        set(useEnglish) {
            wasUseEnglishToggled = true
            prefs.edit().putBoolean(USE_ENGLISH, useEnglish).commit()
        }

    var wasUseEnglishToggled: Boolean
        get() = prefs.getBoolean(WAS_USE_ENGLISH_TOGGLED, false)
        set(wasUseEnglishToggled) = prefs.edit().putBoolean(WAS_USE_ENGLISH_TOGGLED, wasUseEnglishToggled).apply()

    var isGlobalThemeEnabled: Boolean
        get() = prefs.getBoolean(IS_GLOBAL_THEME_ENABLED, false)
        set(isGlobalThemeEnabled) = prefs.edit().putBoolean(IS_GLOBAL_THEME_ENABLED, isGlobalThemeEnabled).apply()

    //var isSystemThemeEnabled: Boolean
    //    get() = prefs.getBoolean(IS_SYSTEM_THEME_ENABLED, isSPlus())
    //    set(isSystemThemeEnabled) = prefs.edit().putBoolean(IS_SYSTEM_THEME_ENABLED, isSystemThemeEnabled).apply()

    var wasCustomThemeSwitchDescriptionShown: Boolean
        get() = prefs.getBoolean(WAS_CUSTOM_THEME_SWITCH_DESCRIPTION_SHOWN, true)
        set(wasCustomThemeSwitchDescriptionShown) = prefs.edit().putBoolean(WAS_CUSTOM_THEME_SWITCH_DESCRIPTION_SHOWN, true)
            .apply()

    var lastConflictApplyToAll: Boolean
        get() = prefs.getBoolean(LAST_CONFLICT_APPLY_TO_ALL, true)
        set(lastConflictApplyToAll) = prefs.edit().putBoolean(LAST_CONFLICT_APPLY_TO_ALL, lastConflictApplyToAll).apply()

    var lastConflictResolution: Int
        get() = prefs.getInt(LAST_CONFLICT_RESOLUTION, CONFLICT_SKIP)
        set(lastConflictResolution) = prefs.edit().putInt(LAST_CONFLICT_RESOLUTION, lastConflictResolution).apply()

    var sorting: Int
        get() = prefs.getInt(SORT_ORDER, context.resources.getInteger(R.integer.default_sorting))
        set(sorting) {prefs.edit().putInt(SORT_ORDER, sorting).commit()}

    var lastUsedViewPagerPage: Int
        get() = prefs.getInt(LAST_USED_VIEW_PAGER_PAGE, context.resources.getInteger(R.integer.default_viewpager_page))
        set(lastUsedViewPagerPage) = prefs.edit().putInt(LAST_USED_VIEW_PAGER_PAGE, lastUsedViewPagerPage).apply()

    var use24HourFormat: Boolean
        get() = prefs.getBoolean(USE_24_HOUR_FORMAT, DateFormat.is24HourFormat(context))
        set(use24HourFormat) = prefs.edit().putBoolean(USE_24_HOUR_FORMAT, use24HourFormat).apply()

    var useDayOfWeekInTimeFormat: Boolean
        get() = prefs.getBoolean(SHOW_DAY_OF_WEEK, true)
        set(useDayOfWeekInTimeFormat) = prefs.edit().putBoolean(SHOW_DAY_OF_WEEK, useDayOfWeekInTimeFormat).apply()

    var isUsingModifiedAppIcon: Boolean
        get() = prefs.getBoolean(IS_USING_MODIFIED_APP_ICON, false)
        set(isUsingModifiedAppIcon) = prefs.edit().putBoolean(IS_USING_MODIFIED_APP_ICON, isUsingModifiedAppIcon).apply()

    var appId: String
        get() = prefs.getString(APP_ID, "")!!
        set(appId) = prefs.edit().putString(APP_ID, appId).apply()

    var wasMtsBlueIconChecked: Boolean
        get() = prefs.getBoolean(WAS_ORANGE_ICON_CHECKED, false)
        set(wasMtsBlueIconChecked) = prefs.edit().putBoolean(WAS_ORANGE_ICON_CHECKED, wasMtsBlueIconChecked).apply()

    var wasAppIconCustomizationWarningShown: Boolean
        get() = prefs.getBoolean(WAS_APP_ICON_CUSTOMIZATION_WARNING_SHOWN, true)
        set(wasAppIconCustomizationWarningShown) = prefs.edit().putBoolean(WAS_APP_ICON_CUSTOMIZATION_WARNING_SHOWN, true)
            .apply()

    var dateFormat: String
        get() = prefs.getString(DATE_FORMAT, getDefaultDateFormat())!!
        set(dateFormat) = prefs.edit().putString(DATE_FORMAT, dateFormat).apply()

    private fun getDefaultDateFormat(): String {
        val format = DateFormat.getDateFormat(context)
        val pattern = (format as SimpleDateFormat).toLocalizedPattern()
        return when (pattern.lowercase().replace(" ", "")) {
            "d.M.y" -> DATE_FORMAT_ONE
            "dd/mm/y" -> DATE_FORMAT_TWO
            "mm/dd/y" -> DATE_FORMAT_THREE
            "y-mm-dd" -> DATE_FORMAT_FOUR
            "dmmmmy" -> DATE_FORMAT_FIVE
            "mmmmdy" -> DATE_FORMAT_SIX
            "mm-dd-y" -> DATE_FORMAT_SEVEN
            "dd-mm-y" -> DATE_FORMAT_EIGHT
            else -> DATE_FORMAT_FIVE
        }
    }

    var wasFolderLockingNoticeShown: Boolean
        get() = prefs.getBoolean(WAS_FOLDER_LOCKING_NOTICE_SHOWN, false)
        set(wasFolderLockingNoticeShown) = prefs.edit().putBoolean(WAS_FOLDER_LOCKING_NOTICE_SHOWN, wasFolderLockingNoticeShown).apply()

    var lastRenameUsed: Int
        get() = prefs.getInt(LAST_RENAME_USED, RENAME_SIMPLE)
        set(lastRenameUsed) = prefs.edit().putInt(LAST_RENAME_USED, lastRenameUsed).apply()

    var lastExportedSettingsFolder: String
        get() = prefs.getString(LAST_EXPORTED_SETTINGS_FOLDER, "")!!
        set(lastExportedSettingsFolder) = prefs.edit().putString(LAST_EXPORTED_SETTINGS_FOLDER, lastExportedSettingsFolder).apply()

    var lastBlockedNumbersExportPath: String
        get() = prefs.getString(LAST_BLOCKED_NUMBERS_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit().putString(LAST_BLOCKED_NUMBERS_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var blockUnknownNumbers: Boolean
        get() = prefs.getBoolean(BLOCK_UNKNOWN_NUMBERS, false)
        set(blockUnknownNumbers) = prefs.edit().putBoolean(BLOCK_UNKNOWN_NUMBERS, blockUnknownNumbers).apply()

    val isBlockingUnknownNumbers: Flow<Boolean> = ::blockUnknownNumbers.asFlowNonNull()

    var blockHiddenNumbers: Boolean
        get() = prefs.getBoolean(BLOCK_HIDDEN_NUMBERS, false)
        set(blockHiddenNumbers) = prefs.edit().putBoolean(BLOCK_HIDDEN_NUMBERS, blockHiddenNumbers).apply()

    val isBlockingHiddenNumbers: Flow<Boolean> = ::blockHiddenNumbers.asFlowNonNull()

    var fontSize: Int
        get() = prefs.getInt(FONT_SIZE, context.resources.getInteger(R.integer.default_font_size))
        set(size) = prefs.edit().putInt(FONT_SIZE, size).apply()

    var defaultTab: Int
        get() = prefs.getInt(DEFAULT_TAB, TAB_LAST_USED)
        set(defaultTab) = prefs.edit().putInt(DEFAULT_TAB, defaultTab).apply()

    var startNameWithSurname: Boolean
        get() = prefs.getBoolean(START_NAME_WITH_SURNAME, false)
        set(startNameWithSurname) {prefs.edit().putBoolean(START_NAME_WITH_SURNAME, startNameWithSurname).commit() }

    var favorites: MutableSet<String>
        get() = prefs.getStringSet(FAVORITES, HashSet())!!
        set(favorites) = prefs.edit().remove(FAVORITES).putStringSet(FAVORITES, favorites).apply()

    var showCallConfirmation: Boolean
        get() = prefs.getBoolean(SHOW_CALL_CONFIRMATION, true)
        set(showCallConfirmation) = prefs.edit().putBoolean(SHOW_CALL_CONFIRMATION, showCallConfirmation).apply()

    // color picker last used colors
    var colorPickerRecentColors: LinkedList<Int>
        get(): LinkedList<Int> {
            val defaultList = arrayListOf(
                ContextCompat.getColor(context, R.color.md_red_700),
                ContextCompat.getColor(context, R.color.md_blue_700),
                ContextCompat.getColor(context, R.color.md_green_700),
                ContextCompat.getColor(context, R.color.md_yellow_700),
                ContextCompat.getColor(context, R.color.md_orange_700)
            )
            return LinkedList(prefs.getString(COLOR_PICKER_RECENT_COLORS, null)?.lines()?.map { it.toInt() } ?: defaultList)
        }
        set(recentColors) = prefs.edit().putString(COLOR_PICKER_RECENT_COLORS, recentColors.joinToString(separator = "\n")).apply()

    var ignoredContactSources: HashSet<String>
        get() = prefs.getStringSet(IGNORED_CONTACT_SOURCES, hashSetOf(".")) as HashSet
        set(ignoreContactSources) = prefs.edit().remove(IGNORED_CONTACT_SOURCES).putStringSet(IGNORED_CONTACT_SOURCES, ignoreContactSources).apply()

    var formatPhoneNumbers: Boolean
        get() = prefs.getBoolean(FORMAT_PHONE_NUMBERS, true)
        set(formatPhoneNumbers) {prefs.edit().putBoolean(FORMAT_PHONE_NUMBERS, formatPhoneNumbers).commit() }

    var showOnlyContactsWithNumbers: Boolean
        get() = prefs.getBoolean(SHOW_ONLY_CONTACTS_WITH_NUMBERS, true)
        set(showOnlyContactsWithNumbers) {prefs.edit().putBoolean(SHOW_ONLY_CONTACTS_WITH_NUMBERS, showOnlyContactsWithNumbers).commit() }

    var lastUsedContactSource: String
        get() = prefs.getString(LAST_USED_CONTACT_SOURCE, "")!!
        set(lastUsedContactSource) = prefs.edit().putString(LAST_USED_CONTACT_SOURCE, lastUsedContactSource).apply()

    var wasLocalAccountInitialized: Boolean
        get() = prefs.getBoolean(WAS_LOCAL_ACCOUNT_INITIALIZED, false)
        set(wasLocalAccountInitialized) = prefs.edit().putBoolean(WAS_LOCAL_ACCOUNT_INITIALIZED, wasLocalAccountInitialized).apply()

    var lastExportPath: String
        get() = prefs.getString(LAST_EXPORT_PATH, "")!!
        set(lastExportPath) = prefs.edit().putString(LAST_EXPORT_PATH, lastExportPath).apply()

    var speedDial: String
        get() = prefs.getString(SPEED_DIAL, "")!!
        set(speedDial) = prefs.edit().putString(SPEED_DIAL, speedDial).apply()

    var showPrivateContacts: Boolean
        get() = prefs.getBoolean(SHOW_PRIVATE_CONTACTS, true)
        set(showPrivateContacts) = prefs.edit().putBoolean(SHOW_PRIVATE_CONTACTS, showPrivateContacts).apply()

    var mergeDuplicateContacts: Boolean
        get() = prefs.getBoolean(MERGE_DUPLICATE_CONTACTS, false)
        set(mergeDuplicateContacts) = prefs.edit().putBoolean(MERGE_DUPLICATE_CONTACTS, mergeDuplicateContacts).apply()

    var favoritesContactsOrder: String
        get() = prefs.getString(FAVORITES_CONTACTS_ORDER, "")!!
        set(order) = prefs.edit().putString(FAVORITES_CONTACTS_ORDER, order).apply()

    var isCustomOrderSelected: Boolean
        get() = prefs.getBoolean(FAVORITES_CUSTOM_ORDER_SELECTED, false)
        set(selected) = prefs.edit().putBoolean(FAVORITES_CUSTOM_ORDER_SELECTED, selected).apply()

    var viewType: Int
        get() = prefs.getInt(VIEW_TYPE, VIEW_TYPE_LIST)
        set(viewType) {prefs.edit().putInt(VIEW_TYPE, viewType).commit()}

    var contactsGridColumnCount: Int
        get() = prefs.getInt(CONTACTS_GRID_COLUMN_COUNT, getDefaultContactColumnsCount())
        set(contactsGridColumnCount) = prefs.edit().putInt(CONTACTS_GRID_COLUMN_COUNT, contactsGridColumnCount).apply()

    private fun getDefaultContactColumnsCount(): Int {
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            context.resources.getInteger(R.integer.contacts_grid_columns_count_portrait)
        } else {
            context.resources.getInteger(R.integer.contacts_grid_columns_count_landscape)
        }
    }

    // Accessibility
    var showCheckmarksOnSwitches: Boolean
        get() = prefs.getBoolean(SHOW_CHECKMARKS_ON_SWITCHES, false)
        set(showCheckmarksOnSwitches) = prefs.edit().putBoolean(SHOW_CHECKMARKS_ON_SWITCHES, showCheckmarksOnSwitches).apply()

    var showCheckmarksOnSwitchesFlow = ::showCheckmarksOnSwitches.asFlowNonNull()

    // ─── přesunuto z Config ──────────────────────────────────────────────────

    fun getSpeedDialValues(): ArrayList<SpeedDial> {
        val speedDialType = object : TypeToken<List<SpeedDial>>() {}.type
        val speedDialValues = gson.fromJson<ArrayList<SpeedDial>>(speedDial, speedDialType) ?: ArrayList(1)

        for (i in 1..9) {
            val speedDialItem = SpeedDial(i, "", "")
            if (speedDialValues.firstOrNull { it.id == i } == null) {
                speedDialValues.add(speedDialItem)
            }
        }

        return speedDialValues
    }

    /*
    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(
            key = getKeyForCustomSIM(number),
            parcelable = handle
        ).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val key = getKeyForCustomSIM(number)
        return prefs.getPhoneAccountHandleModel(key, null)?.toPhoneAccountHandle()
    }

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(getKeyForCustomSIM(number)).apply()
    }

    private fun getKeyForCustomSIM(number: String): String {
        return REMEMBER_SIM_PREFIX + PhoneNumberHelper.normalizeNumberE164(number, null, false)
    }
    */

    // ─── custom SIM per number ────────────────────────────────────────────────────

    fun getCustomSim(phoneNumber: String): Int =
        prefs.getInt("SIM_SAVE_$phoneNumber", -4)

    fun saveCustomSim(phoneNumber: String, simSlot: Int) =
        prefs.edit().putInt("SIM_SAVE_$phoneNumber", simSlot).apply()

    fun removeCustomSim(phoneNumber: String) =
        prefs.edit().remove("SIM_SAVE_$phoneNumber").apply()

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var groupSubsequentCalls: Boolean
        get() = prefs.getBoolean(GROUP_SUBSEQUENT_CALLS, true)
        set(groupSubsequentCalls) {prefs.edit().putBoolean(GROUP_SUBSEQUENT_CALLS, groupSubsequentCalls).commit()}

    var openDialPadAtLaunch: Boolean
        get() = prefs.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH, false)
        set(openDialPad) = prefs.edit().putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, openDialPad).apply()

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var disableSwipeToAnswer: Boolean
        get() = prefs.getBoolean(DISABLE_SWIPE_TO_ANSWER, false)
        set(disableSwipeToAnswer) = prefs.edit().putBoolean(DISABLE_SWIPE_TO_ANSWER, disableSwipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var enableT9dialpad: Boolean
        get() = prefs.getBoolean(ENABLE_DIALPAD_T9, false)
        set(enableT9dialpad) = prefs.edit().putBoolean(ENABLE_DIALPAD_T9, enableT9dialpad).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, true)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, true)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    var swhowDeclineAndSMSbutton: Boolean
        get() = prefs.getBoolean(SWOW_DECLINE_CALL_SMS, false)
        set(swhowDeclineAndSMSbutton) = prefs.edit().putBoolean(SWOW_DECLINE_CALL_SMS, swhowDeclineAndSMSbutton).apply()

    var shakeEffectConfirmingCall: Boolean
        get() = prefs.getBoolean(ALWAYS_SHAKE, true)
        set(shakeEffectConfirmingCall) = prefs.edit().putBoolean(ALWAYS_SHAKE, shakeEffectConfirmingCall).apply()

    var searchInAllContactFields: Boolean
        get() = prefs.getBoolean(SEARCH_IN_ALL_CONTACT_FIELDS, false)
        set(value) = prefs.edit().putBoolean(SEARCH_IN_ALL_CONTACT_FIELDS, value).apply()

    var sentSmsList: String
        get() = prefs.getString(KEY_SMS_LIST, null) ?: ""
        set(value) = prefs.edit().putString(KEY_SMS_LIST, value).apply()

    private val DEFAULT_SMS_TEMPLATES = listOf(
        "Teď nemohu mluvit, zavolám zpátky.",
        "Jsem na schůzce, ozvu se co nejdřív.",
        "Právě řídím, ozvu se co nejdřív.",
        "Jsem zaneprázdněný, zavolám ti zpět.",
        "Nemohu teď volat, co potřebuješ?",
        "Jsem v tichém režimu, napíšu brzy."
    ).joinToString(SMS_TEMPLATE_SEPARATOR)

    var smsTemplates: String
        get() = prefs.getString(SMS_TEMPLATES, DEFAULT_SMS_TEMPLATES) ?: DEFAULT_SMS_TEMPLATES
        set(value) = prefs.edit().putString(SMS_TEMPLATES, value).apply()

    var lastOutgoingCallNumber: String
        get() = prefs.getString(LAST_OUTGOING_CALL_NUMBER, "")!!
        set(value) = prefs.edit().putString(LAST_OUTGOING_CALL_NUMBER, value).apply()

    var lastOutgoingCallNumberSim: Int
        get() = prefs.getInt(LAST_OUTGOING_CALL_NUMBER_SIM, 0)!!
        set(value) = prefs.edit().putInt(LAST_OUTGOING_CALL_NUMBER_SIM, value).apply()

    var hadThankYouInstalled: Boolean
        get() = prefs.getBoolean(HAD_THANK_YOU_INSTALLED, true)
        set(value) = prefs.edit().putBoolean(HAD_THANK_YOU_INSTALLED, value).apply()

    // ─── helpers ─────────────────────────────────────────────────────────────

    protected fun <T> KProperty0<T>.asFlow(emitOnCollect: Boolean = false): Flow<T?> =
        prefs.run { sharedPreferencesCallback(sendOnCollect = emitOnCollect) { this@asFlow.get() } }

    protected fun <T> KProperty0<T>.asFlowNonNull(emitOnCollect: Boolean = false): Flow<T> = asFlow(emitOnCollect).filterNotNull()
}
