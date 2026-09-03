package cz.mts.phone.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.dialogs.ChangeViewTypeDialog
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.extensions.adjustColor
import cz.mts.base.extensions.appLaunched
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beGoneIf
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.canUseFullScreenIntent
import cz.mts.base.extensions.convertToBitmap
import cz.mts.base.extensions.copyToClipboard
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.hideKeyboard
import cz.mts.base.extensions.isAppInstalled
import cz.mts.base.extensions.isDefaultDialer
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.onTabSelectionChanged
import cz.mts.base.extensions.openFullScreenIntentSettings
import cz.mts.base.extensions.openNotificationSettings
import cz.mts.base.extensions.shortcutManager
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.updateBottomTabItemColors
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.CONTACTS_GRID_MAX_COLUMNS_COUNT
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.base.helpers.PERMISSION_READ_CONTACTS
import cz.mts.base.helpers.REQUEST_CODE_SET_DEFAULT_CALLER_ID
import cz.mts.base.helpers.TAB_CALL_HISTORY
import cz.mts.base.helpers.TAB_CONTACTS
import cz.mts.base.helpers.TAB_FAVORITES
import cz.mts.base.helpers.TAB_LAST_USED
import cz.mts.base.helpers.VIEW_TYPE_GRID
import cz.mts.base.helpers.isNougatMR1Plus
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isTiramisuPlus
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.RadioItem
import cz.mts.phone.adapters.ViewPagerAdapter
import cz.mts.phone.databinding.ActivityMainBinding
import cz.mts.phone.dialogs.FilterContactSourceDialogMTs
import cz.mts.phone.dialogs.ChangeSortingDialog
import cz.mts.phone.extensions.clearMissedCalls
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.launchAccountsConfiguration
import cz.mts.phone.extensions.launchCreateNewContactIntent
import cz.mts.phone.fragments.ContactsFragment
import cz.mts.phone.fragments.FavoritesFragment
import cz.mts.phone.fragments.MyViewPagerFragment
import cz.mts.phone.fragments.RecentsFragment
import cz.mts.phone.helpers.MissedCallManager
import cz.mts.base.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import cz.mts.phone.helpers.RecentsHelper
import cz.mts.base.helpers.tabsList
import cz.mts.phone.R
import cz.mts.phone.extensions.appOpsManager
import cz.mts.phone.extensions.appVersionCode
import cz.mts.base.extensions.areColorsTooSimilarInt
import cz.mts.base.extensions.getContrastingColor
import cz.mts.phone.extensions.isDefaultCallScreeningApp
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.helpers.FONT_SIZE_LARGE
import cz.mts.base.helpers.FONT_SIZE_MEDIUM
import cz.mts.base.helpers.FONT_SIZE_SMALL
import cz.mts.phone.dialogs.ContactCallHistoryDialog
import cz.mts.phone.helpers.AppUpdateNotificationManager
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.helpers.RecentsQueryLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grantland.widget.AutofitHelper
import java.util.Locale

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true
    override var customNavBarLightIcons: Boolean? = null
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var loadingIconToggle = true
    private var launchedDialer = false
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedSorting = 0
    private var storedStartNameWithSurname = false
    private var storedGroupingCalls = false
    private var storedFormatPhoneNumbers = false
    private var storedShowOnlyContactsWithNumbers = false
    private var baseConfigbackgroundColor = 0
    private var currentTabType: Int = -1
    private var openedFromMissedCallNotification = false //kód zatím nepoužívá
    private var openedFromMissedPermNotification = false
    private var openedFromNewsNotification = false
    private var bFilterOnlyContactsWithoutNumbers = false
    private var iAppRunCount = 0
    private var bPermissionNotificationOn = false
    private var bSnackBarOn = false
    private var bCheckContactDuplicityIsRunning = false


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartIntent(intent)

        if (openedFromMissedCallNotification || intent.hasExtra("start_tab")) {
            Handler(Looper.getMainLooper()).post {
                selectInitialTabFromIntent()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //super.onCreate(null)  //řeší pády po update
        handleStartIntent(intent)
        setContentView(binding.root)

        if (config.easterEggMode) {
            mtsGlobalAll.iSaveDebugMode = 2
        }
        storedSorting = config.sorting
        storedFontSize = config.fontSize
        storedGroupingCalls = config.groupSubsequentCalls
        storedStartNameWithSurname = config.startNameWithSurname
        storedFormatPhoneNumbers = config.formatPhoneNumbers
        storedShowOnlyContactsWithNumbers = config.showOnlyContactsWithNumbers

        setNavbar()

        appLaunched(MY_APP_NAME_GOOGLE_ID)

        setupOptionsMenu()
        refreshMenuItems()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))

        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        bSnackBarOn = false
        if (isDefaultDialer()) {
            checkContactPermissions()
            checkPerm() //bSnackBarOn true pokud bude něco hlavního chybět
        } else {
            launchSetDefaultDialerIntent { _ ->
                // výsledek nás nezajímá — aplikace funguje i bez role výchozího dialeru
                checkContactPermissions()
            }
        }

        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

       MissedCallManager.clear(this)

        setupTabs()

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        Contact.sorting = config.sorting

        iAppRunCount = config.appRunCount
        if (iAppRunCount < 5) config.appRunCount = iAppRunCount + 1

    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        if (!bCheckContactDuplicityIsRunning && !CacheContacts.bAnimateRunnig)
            binding.progressIndicatorMain.beGone()
        else
            binding.progressIndicatorMain.beVisible()

        CacheContacts.bSpamChecking = isAppInstalled( "callfilter.app") ||
                                      isAppInstalled( "cz.mts.callfilter")

        bPermissionNotificationOn = false
        val versionCode = appVersionCode.toInt()
        if (!bSnackBarOn) {
        //notifikace oprávnění mají přednost
        if (!checkPerm(true, true)) {
            bPermissionNotificationOn = true
            AppUpdateNotificationManager(this).showSpecialNotification(
                clickAction = AppUpdateNotificationManager.ClickAction.OPEN_APP_MISSED_PERM
            )
        } else {
            if ((config.showNews < versionCode) || (mtsGlobalAll.iSaveDebugMode == 1)) {
                    mtsGlobalAll.newsnotify(this)
                config.showNews = versionCode
            }
        }
        }

        CacheContacts.bInvalidateCache = true

        setNavbar()

        getRecentsFragment()?.refreshTodayCode()

        updateMenuColors()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)
        binding.progressIndicatorMain.setIndicatorColor(properPrimaryColor)

        updateTextColors(binding.mainHolder)
        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        var invalidateRecents = config.themeChanged
        if (invalidateRecents)  config.themeChanged = false

        bFilterOnlyContactsWithoutNumbers = false
        val showOnlyContactsWithNumbers = config.showOnlyContactsWithNumbers
        if (storedShowOnlyContactsWithNumbers != config.showOnlyContactsWithNumbers) {
            storedShowOnlyContactsWithNumbers = showOnlyContactsWithNumbers
            //setupOptionsMenu()
            refreshMenuItems()
        }

        val startNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != startNameWithSurname) {
            storedStartNameWithSurname = startNameWithSurname
            invalidateRecents = true
        }

        val formatPhoneNumbers = config.formatPhoneNumbers
        if (storedFormatPhoneNumbers != config.formatPhoneNumbers) {
            storedFormatPhoneNumbers = formatPhoneNumbers
            invalidateRecents = true
        }

        val groupingCalls = config.groupSubsequentCalls
        if (storedGroupingCalls != groupingCalls) {
            storedGroupingCalls = groupingCalls
            invalidateRecents = true
        }

        val fontSize = config.fontSize
        if (storedFontSize != fontSize) {
            storedFontSize = fontSize
            setupTabs(true)
            getAllFragments().forEach { fragment ->
                fragment?.fontSizeChanged()
            }
        }

        MissedCallManager.clear(this)

        checkShortcuts()

        mtsGlobalAll.numberOfReadySim(this)

        if (invalidateRecents) {
            RecentsQueryLimits.setRefreshState(true)
            //clearRecetCallsCache()  //zbytečné protože CacheContacts.bInvalidateCache = true
        }

        refreshItems(true)

        if (openedFromMissedPermNotification) {
            checkPerm(true, false)
        }


        openedFromMissedCallNotification = false
        openedFromMissedPermNotification = false



    }

    override fun onPause() {
        super.onPause()
        storedSorting = config.sorting
        storedShowTabs = config.showTabs
        storedGroupingCalls = config.groupSubsequentCalls
        storedStartNameWithSurname = config.startNameWithSurname
        storedFormatPhoneNumbers = config.formatPhoneNumbers
        storedShowOnlyContactsWithNumbers = config.showOnlyContactsWithNumbers
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        //if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
        //    checkContactPermissions()
        //} else
            if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            config.blockUnknownNumbers = false
            config.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            false
        }
    }


    override fun onDestroy() {
        super.onDestroy()
    }



 // zde možno definovat co se má stát    onSelectionModeChanged
 //   override fun onSelectionModeChanged(isActive: Boolean) {
 //       super.onSelectionModeChanged(isActive)
 //   }

    private fun updateChangeViewTypeIcon() {
        val iconRes = if (config.viewType == VIEW_TYPE_GRID)
            R.drawable.ic_list_vector
        else
            R.drawable.ic_grid_circles_vector

        val icon = resources.getColoredDrawableWithColor(iconRes, getProperTextColor())
        binding.mainMenu.requireToolbar().menu
            .findItem(R.id.change_view_type)
            ?.icon = icon
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {

            val isCallHistory = currentTabType == TAB_CALL_HISTORY
            val isContacts = currentTabType == TAB_CONTACTS
            val isFavorites = currentTabType == TAB_FAVORITES

            findItem(R.id.view_all_call_history).isVisible = isCallHistory
            findItem(R.id.clear_call_history).isVisible = isCallHistory
            findItem(R.id.sort).isVisible = !isCallHistory
            findItem(R.id.filter).isVisible = true
            findItem(R.id.filter_only_contacts_without_numbers).isVisible = isContacts && !config.showOnlyContactsWithNumbers

            findItem(R.id.filter_only_contacts_without_numbers)?.setTitle(
                if (bFilterOnlyContactsWithoutNumbers)
                    getString(R.string.filter_only_contacts_without_numbers)  + "  \u2713"
                else
                    getString(R.string.filter_only_contacts_without_numbers)  + " "
            )

            findItem(R.id.search_all_fields)?.setTitle(
                if (config.searchInAllContactFields)
                    getString(R.string.search_all_fields)  + "  \u2713"
                else
                    getString(R.string.search_all_fields)  + " "
            )
            findItem(R.id.search_all_fields).isVisible = isContacts

            findItem(R.id.create_new_contact).isVisible = isContacts
            findItem(R.id.change_view_type).isVisible = isFavorites
            findItem(R.id.column_count).isVisible = isFavorites && config.viewType == VIEW_TYPE_GRID
        }
        updateChangeViewTypeIcon()
    }


    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
            setupMenu()

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
            }

            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search_all_fields -> searchAllFieldsInContactOnOff()
                    R.id.filter_only_contacts_without_numbers -> filterOnlyContactsWithoutNumbers()
                    R.id.view_all_call_history -> refreshRecetCalls()
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.settings -> launchSettings()
                    R.id.settings2 -> launchAccountsConfiguration()
                    R.id.settings3 -> manageSpeedDial()
                    R.id.settingsBlocked -> mtsGlobalAll.launchBlockedManagement(this@MainActivity)
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.permissions -> checkPerm(true, false, true)
                    R.id.about -> mtsGlobalAll.launchAbout(this@MainActivity)
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun filterOnlyContactsWithoutNumbers() {
        if (config.showOnlyContactsWithNumbers) return

        bFilterOnlyContactsWithoutNumbers = !bFilterOnlyContactsWithoutNumbers

        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.filter_only_contacts_without_numbers)?.setTitle(
                if (bFilterOnlyContactsWithoutNumbers)
                    getString(R.string.filter_only_contacts_without_numbers)  + "  \u2713"
                else
                    getString(R.string.filter_only_contacts_without_numbers)  + " "
            )
        }

        if (bFilterOnlyContactsWithoutNumbers) refreshSearch(true)
        else refreshSearch()
    }

    private fun searchAllFieldsInContactOnOff() {
        val bSearchAllFields = config.searchInAllContactFields
        config.searchInAllContactFields = !bSearchAllFields

        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.search_all_fields)?.setTitle(
                if (config.searchInAllContactFields)
                    getString(R.string.search_all_fields)  + "  \u2713"
                else
                    getString(R.string.search_all_fields)  + " "
            )
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            getFavoritesFragment()?.refreshItems()
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }


    private fun refreshRecetCalls() {
        CacheContacts.bInvalidateCache = true
        loadingIconToggle = true
        runOnUiThread {
            getRecentsFragment()?.refreshItems(true) {
                refreshSearch()
                toggleViewAllHistoryLoadingIcon(true)
            }
        }
    }


    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(true) { //tvrdý refresh a pak search pokud je aktivní...
                        refreshSearch()
                    }
                }
            }
        }
    }

    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)

        val drawable: LayerDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Nové API 29+
            val d = resources.getDrawable(R.drawable.shortcut_dialpad, theme) as LayerDrawable
            d.findDrawableByLayerId(R.id.shortcut_dialpad_background)?.colorFilter =
                BlendModeColorFilter(appIconColor, BlendMode.SRC_ATOP)
            d
        } else {
            // Starší API
            val d = resources.getDrawable(R.drawable.shortcut_dialpad) as LayerDrawable
            d.findDrawableByLayerId(R.id.shortcut_dialpad_background)?.setColorFilter(appIconColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            d
        }

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW


        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }


    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem], baseConfigbackgroundColor)

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index], baseConfigbackgroundColor)
        }

        //val bottomBarColor = baseConfig.navBarColor //getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(baseConfigbackgroundColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_outline_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_outline_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_vector)
        }

        return icons
    }

    private fun selectInitialTabFromIntent() {
        if (binding.mainTabsHolder.tabCount == 0) return

        var wantedTab = getDefaultTab()

        // 1) explicitní parametr → nejvyšší priorita
        val paramTab = intent?.getIntExtra("start_tab", -1) ?: -1
        if (paramTab != -1) {
            val index = getIndexForTabType(paramTab)
            if (index >= 0) {
                wantedTab = index
            }

            // spotřebuj intent parametr, aby se nepoužil znovu
            intent.removeExtra("start_tab")
        }

        // 2) fallback – ACTION_VIEW
        if (intent?.action == Intent.ACTION_VIEW &&
            config.showTabs and TAB_CALL_HISTORY > 0
        ) {
            wantedTab = binding.mainTabsHolder.tabCount - 1
        }

        binding.mainTabsHolder.getTabAt(wantedTab)?.select()
        currentTabType = getTabTypeForIndex(wantedTab)
        refreshMenuItems()
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                getRecentsFragment()?.refreshTodayCode()
                refreshMenuItems()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler(Looper.getMainLooper()).postDelayed({
                selectInitialTabFromIntent()
            }, 100L)
        }


      //  binding.mainDialpadButton.setOnClickListener {
    //  launchDialpad()
     //   }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs(fontSizeOnly: Boolean = false) {
        if (!fontSizeOnly) {
            binding.viewPager.adapter = null
            binding.mainTabsHolder.removeAllTabs()
        }
        // iterace přes existující taby pro update fontu
        if (fontSizeOnly) {
            for (i in 0 until binding.mainTabsHolder.tabCount) {
                binding.mainTabsHolder.getTabAt(i)?.customView
                    ?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        val tabTextSize = when (config.fontSize) {
                            FONT_SIZE_SMALL  -> resources.getDimension(R.dimen.small_text_size)
                            FONT_SIZE_MEDIUM -> resources.getDimension(R.dimen.small2_text_size)
                            FONT_SIZE_LARGE  -> resources.getDimension(R.dimen.smaller_text_size)
                            else             -> resources.getDimension(R.dimen.normal_text_size)
                        }
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize)
                    }
            }
            return
        }

        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        val tabTextSize =  when (storedFontSize) {
                            FONT_SIZE_SMALL  -> (resources.getDimension(R.dimen.small_text_size))
                            FONT_SIZE_MEDIUM -> (resources.getDimension(R.dimen.small2_text_size))
                            FONT_SIZE_LARGE  -> (resources.getDimension(R.dimen.smaller_text_size))
                            else             -> (resources.getDimension(R.dimen.normal_text_size))
                        }
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position], baseConfigbackgroundColor)
            },
            tabSelectedAction = {
                currentTabType = getTabTypeForIndex(it.position)
                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position], baseConfigbackgroundColor)

                val lastPosition = binding.mainTabsHolder.tabCount - 1
                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
                    clearMissedCalls()
                }
                refreshMenuItems()
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabTypeForIndex(index: Int): Int {
        val showTabs = config.showTabs

        val list = mutableListOf<Int>()
        if (showTabs and TAB_CONTACTS != 0) list.add(TAB_CONTACTS)
        if (showTabs and TAB_FAVORITES != 0) list.add(TAB_FAVORITES)
        if (showTabs and TAB_CALL_HISTORY != 0) list.add(TAB_CALL_HISTORY)

        return list.getOrNull(index) ?: TAB_CONTACTS
    }

    // převede typ tabu na index
    private fun getIndexForTabType(tabType: Int): Int {
        val showTabs = config.showTabs
        val list = mutableListOf<Int>()
        if (showTabs and TAB_CONTACTS != 0) list.add(TAB_CONTACTS)
        if (showTabs and TAB_FAVORITES != 0) list.add(TAB_FAVORITES)
        if (showTabs and TAB_CALL_HISTORY != 0) list.add(TAB_CALL_HISTORY)

        return list.indexOf(tabType)
    }


    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.contacts_tab
            1 -> R.string.favorites_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }


        private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) return

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    private fun launchDialpad() {
       // Intent(applicationContext, DialpadActivity::class.java).apply {
            Intent(this, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }


    private fun showFilterDialog() {
        FilterContactSourceDialogMTs(this, false) {
            CacheContacts.bInvalidateCache = true
            refreshFragments()
        }
    }

    private fun refreshFragments() {
        getFavoritesFragment()?.refreshItems()
        getContactsFragment()?.refreshItems{ refreshSearch() }
        getRecentsFragment()?.refreshItems{ refreshSearch() }
    }

    private fun refreshSearch(bFilterOnlyContactsWithoutNumbers : Boolean = false) {
        if (bFilterOnlyContactsWithoutNumbers) {
            runOnUiThread {
                getCurrentFragment()?.onSearchQueryChanged("MTS_bFilterOnlyContactsWithoutNumbers")
            }
        } else {
            runOnUiThread {
                if (binding.mainMenu.isSearchOpen)
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
            }
        }
    }


    fun viewContactInRecents(contactName: String) {
        if (config.showTabs and TAB_CALL_HISTORY == 0) return
        val recentsIndex = getIndexForTabType(TAB_CALL_HISTORY)
        if (recentsIndex < 0) return

        binding.mainTabsHolder.getTabAt(recentsIndex)?.select()
        binding.mainMenu.focusView()
        binding.mainMenu.binding.topToolbarSearch.setText(contactName)
    }

    fun showContactCallHistory(contactName: String) {
        if (config.showTabs and TAB_CALL_HISTORY == 0) return
        val fragment = getRecentsFragment() ?: return
        ContactCallHistoryDialog(this, fragment, contactName)
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? {
        val fragment = findViewById<RecentsFragment>(R.id.recents_fragment)
        fragment?.let { wireRecentsProgressCallback(it) }
        return fragment
    }

    private fun wireRecentsProgressCallback(fragment: RecentsFragment) {
        if (fragment.onChunkProgress == null) {
            fragment.onChunkProgress = { toggleViewAllHistoryLoadingIcon() }
        }
    }

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < binding.mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CALL_HISTORY > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            CacheContacts.bInvalidateCache = true
            getFavoritesFragment()?.refreshItems {refreshSearch()}
            getContactsFragment()?.refreshItems  {refreshSearch()}
        }
    }

    private fun manageSpeedDial() {
    Intent(this, ManageSpeedDialActivity::class.java).apply {
        startActivity(this)
    }
    }

    private fun setNavbar() {
        val itheme = config.themeIdSaved
    baseConfigbackgroundColor = when (itheme) {
        5 -> config.navBarColor  //custom chceme vlastní navbar
        8 -> Color.parseColor("#D62D73") //T-mobile
        9 -> Color.parseColor("#FED900") //Kaktus
        else -> config.backgroundColor //jinak bereme barvu pozadí a nijak ji neupravujeme
    }

    //pojistka kdyby byla černá na černé, nebo bílá na bílé, tak ať je menu vidět
    if ((itheme != 5) && (areColorsTooSimilarInt(getProperTextColor(), baseConfigbackgroundColor)))
        baseConfigbackgroundColor = getContrastingColor(baseConfigbackgroundColor)

    //nyní problém pokud navbar a primarní barva jsou stejné, protože pak není vidět na čem stojíme :)
    //to ale musíme ošetřit až při samotném přepínání tabu... Context.updateBottomTabItemColors()
    customNavBarLightIcons = shouldUseLightIcons(baseConfigbackgroundColor)
    applyNavigationBar(
    color = baseConfigbackgroundColor,
    lightIcons = shouldUseLightIcons(baseConfigbackgroundColor) ,  //MTSX true/false na základě té barvy
    )
    }


    private fun toggleViewAllHistoryLoadingIcon(bBaseIcon : Boolean = false) {
        if (bBaseIcon) loadingIconToggle = false

        loadingIconToggle = !loadingIconToggle
        val iconRes = if (loadingIconToggle)
            R.drawable.ic_database_refresh
        else
            R.drawable.ic_database_refresh2

        val icon = resources.getColoredDrawableWithColor(iconRes, getProperTextColor())
        binding.mainMenu.requireToolbar().menu
            .findItem(R.id.view_all_call_history)
            ?.icon = icon
    }

    @Suppress("DEPRECATION")
    private fun isAutoRevokeEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }

        val appOps = appOpsManager ?: return false

        // OP string není dostupný ve starších API – definujeme ručně
        val OP_AUTO_REVOKE = "android:auto_revoke_permissions_if_unused"

        val mode = appOps.unsafeCheckOpNoThrow(
            OP_AUTO_REVOKE,
            android.os.Process.myUid(),
            context.packageName
        )

        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkPerm(bForce : Boolean = false, bInvisibleCheck : Boolean = false, bCheckContactDuplicity : Boolean = false) : Boolean {
    if (bCheckContactDuplicityIsRunning) return true
    var bAllOK = true
    if (((config.wasOverlaySnackbarConfirmed == false) || (bForce)) && (!Settings.canDrawOverlays(this)))
        {
            if (!bInvisibleCheck) showSnackBar(getString(R.string.allow_displaying_over_other_apps),  1, bAllOK)
            bAllOK = false
        }

    if (!canUseFullScreenIntent())
        {
            if (!bInvisibleCheck) showSnackBar(getString(R.string.allow_full_screen_notifications_incoming_calls), 2, bAllOK)
            bAllOK = false
        }

    if  (!isTiramisuPlus())
        {
           if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
               if (!bInvisibleCheck) showSnackBar(getString(R.string.allow_notifications_incoming_calls), 3, bAllOK)
               bAllOK = false
           }
        }
        else
        {
            val hasNotificationPermission =
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                 ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                if (!bInvisibleCheck) showSnackBar(getString(R.string.allow_notifications_incoming_calls), 4, bAllOK)
                bAllOK = false
            }
        }

        if (!bForce) return bAllOK

        if (!isDefaultDialer()) {
            if (!bInvisibleCheck) showSnackBar(getString(R.string.error_default_dialer_mts), 5, bAllOK)
            bAllOK = false
        }

//výchozí aplikace k identifikaci volajícího a spamu
        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) //|| hasAnyBlockedNumber())) EE to nás nezajímá, postará se jiná výchozí spamID aplikace
        {
            val iIsDefaultForBlocking = isDefaultCallScreeningApp
            if (!iIsDefaultForBlocking) {
                if (!bInvisibleCheck) showSnackBar(getString(R.string.error_default_app_mts), 6, bAllOK)
                bAllOK = false
            }
        }

        if (isAutoRevokeEnabled(this))
        {
            if (!bInvisibleCheck) showSnackBar(getString(R.string.error_autoremoveperm_mts), 7, bAllOK)
            bAllOK = false
        }

        val hasContactPermission =
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactPermission)
        {
            if (!bInvisibleCheck) showSnackBar(getString(R.string.could_not_access_contacts), 8, bAllOK)
            bAllOK = false
        }


        if (config.swhowDeclineAndSMSbutton) {
            val hasSMSPermission =
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            if (!hasSMSPermission)
            {
                if (!bInvisibleCheck) showSnackBar(getString(R.string.could_not_send_sms), 10, bAllOK)
                bAllOK = false
            }
        }


        if (bAllOK && bCheckContactDuplicity) {
            bCheckContactDuplicityIsRunning = true
            binding.progressIndicatorMain.beVisible()

            lifecycleScope.launch {
                val log = StringBuilder()
                val hasDuplicates = withContext(Dispatchers.IO) {
                    CacheContacts.hasCrossSourceDuplicates(true, log)
                }
                // zpět na main thread
                binding.progressIndicatorMain.beGone()

                bCheckContactDuplicityIsRunning = false

                if (hasDuplicates) {
                    copyToClipboard(log.toString())
                    showSnackBar(getString(R.string.contacts_duplicity), 9, bAllOK)
                }
                else showSnackBar("👍 " + getString(R.string.error_allok_mts), 99, bAllOK)
            }
        }


        if ((!bCheckContactDuplicityIsRunning) && (bAllOK) && (!bInvisibleCheck)) showSnackBar("👍 " + getString(R.string.error_allok_mts), 99, bAllOK)

        return bAllOK
    }

    private fun isCzechOrSlovakLocale(): Boolean {
        val locale = Locale.getDefault()
        return locale.language in listOf("cs", "sk")
    }

    private fun showSnackBar(sMessage : String, iType : Int , bAllOK : Boolean) {

         if (!bAllOK) return

         val snackbar = Snackbar.make(binding.mainHolder, sMessage, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {

             //Overlay
            if (iType == 1) {
                bSnackBarOn = false
                config.wasOverlaySnackbarConfirmed = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
            //fulscreen API 34+
            else if (iType == 2) {
                bSnackBarOn = false
                this.openFullScreenIntentSettings(MY_APP_NAME_GOOGLE_ID)
            }
            //API x-32
            else if (iType == 3) {
                bSnackBarOn = false
                openNotificationSettings()
            }
            else if (iType == 4) {
                bSnackBarOn = false
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            //není výchozí dialer
            else if (iType == 5) {
                bSnackBarOn = false
                launchSetDefaultDialerIntent()
            }
            else if (iType == 6) {
                bSnackBarOn = false
                setDefaultCallerIdApp()
            }
            else if (iType == 7) {
                bSnackBarOn = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", MY_APP_NAME_GOOGLE_ID, null)
                }
                startActivity(intent)
            }
            else if (iType == 8) {
                bSnackBarOn = false
                checkContactPermissions()
            }
            else if (iType == 9) {
                bSnackBarOn = false
                showFilterDialog()
            }
            else if (iType == 10) {
                bSnackBarOn = false
                launchSettings() //tam je check na to a když tak vyvolá oprávnění
            }
            else {bSnackBarOn = false} //99 = allOK

         } //OK action end

        val snackbarText = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarText.maxLines = 4
        snackbarText.ellipsize = null // aby se text nekrátil
        snackbarText.textAlignment = View.TEXT_ALIGNMENT_TEXT_START // optional, aby byl text zarovnán vlevo
        snackbar.setBackgroundTint(getProperBackgroundColor().adjustColor())
        snackbar.setTextColor(getProperTextColor())
        snackbar.setActionTextColor(getProperTextColor())
        snackbar.show()
        if (bPermissionNotificationOn) AppUpdateNotificationManager(this).cancelNotification()
        bSnackBarOn = true

    }


    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        }


    private fun handleStartIntent(intent: Intent) {
        setIntent(intent)
        if (intent.getBooleanExtra("from_missed_call_notification", false)) {
            openedFromMissedCallNotification = true
            intent.removeExtra("from_missed_call_notification")
            return
        }
        if (intent.getBooleanExtra("from_missed_perm_notification", false)) {
            openedFromMissedPermNotification = true
            intent.removeExtra("from_missed_perm_notification")
            return
        }
        if (intent.getBooleanExtra("from_news_notification", false)) {
            openedFromNewsNotification = true
            intent.removeExtra("from_news_notification")
            return
        }

        if (intent.action == Intent.ACTION_VIEW &&
            intent.data?.authority == "call_log"
        ) {
            openedFromMissedCallNotification = true
            // nastav start_tab ručně
            intent.putExtra("start_tab", TAB_CALL_HISTORY)
        }
    }

}
