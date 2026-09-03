package cz.mts.base.activities

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.core.view.size
import cz.mts.base.R
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getAppIconColors
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getPermissionString
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperStatusBarColor
import cz.mts.base.extensions.getThemeId
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.hideKeyboard
import cz.mts.base.extensions.openDeviceSettings
import cz.mts.base.extensions.showErrorToast
import cz.mts.base.helpers.APP_ICON_IDS
import cz.mts.base.helpers.APP_LAUNCHER_NAME
import cz.mts.base.helpers.MEDIUM_ALPHA
import cz.mts.base.helpers.MyContextWrapper
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.isQPlus
import cz.mts.base.helpers.isTiramisuPlus
import cz.mts.base.views.MyAppBarLayout

abstract class BaseSimpleActivity : EdgeToEdgeActivity() {

    var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var onDefaultDialerResult: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false

    private lateinit var backCallback: OnBackPressedCallback


    private val setDefaultDialerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val granted = result.resultCode == RESULT_OK
            onDefaultDialerResult?.invoke(granted)
            onDefaultDialerResult = null
        }

    private val setDefaultCallerIdLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // výsledek záměrně ignorován – systémová role, stav se čte přes RoleManager
        }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    abstract fun getAppIconIDs(): ArrayList<Int>
    abstract fun getAppLauncherName(): String
    abstract fun getRepositoryName(): String?

    /** Vrátí true pokud bylo stisknutí Back spotřebováno. */
    protected open fun onBackPressedCompat(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getThemeId(showTransparentTop = true))
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        registerBackPressedCallback()
    }

    override fun onResume() {
        super.onResume()
        setTheme(getThemeId(showTransparentTop = true))
        updateBackgroundColor(getProperBackgroundColor())
        updateRecentsAppIcon()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDefaultDialerResult = null
        actionOnPermission = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            hideKeyboard()
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun attachBaseContext(newBase: Context) {
        if (newBase.baseConfig.useEnglish && !isTiramisuPlus()) {
            super.attachBaseContext(MyContextWrapper(newBase).wrap(newBase, "en"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private fun registerBackPressedCallback() {
        backCallback = onBackPressedDispatcher.addCallback(this) {
            if (onBackPressedCompat()) return@addCallback
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    fun updateBackgroundColor(color: Int = baseConfig.backgroundColor) {
        window.decorView.setBackgroundColor(color)
    }

    fun setupTopAppBar(
        topAppBar: MyAppBarLayout,
        navigationIcon: NavigationIcon = NavigationIcon.None,
        topBarColor: Int = getRequiredTopBarColor(),
        searchMenuItem: MenuItem? = null,
    ) {
        val contrastColor = topBarColor.getContrastColor()
        if (navigationIcon != NavigationIcon.None) {
            val drawableId = if (navigationIcon == NavigationIcon.Cross) {
                R.drawable.ic_cross_vector
            } else {
                R.drawable.ic_arrow_left_vector
            }
            topAppBar.toolbar?.navigationIcon =
                resources.getColoredDrawableWithColor(drawableId, contrastColor)
            topAppBar.toolbar?.setNavigationContentDescription(navigationIcon.accessibilityResId)
        }

        topAppBar.toolbar?.setNavigationOnClickListener {
            hideKeyboard()
            finish()
        }

        updateTopBarColors(topAppBar, topBarColor)

        if (!isSearchBarEnabled) {
            val searchView = searchMenuItem?.actionView

            searchView
                ?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                ?.applyColorFilter(contrastColor)

            searchView
                ?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                ?.apply {
                    setTextColor(contrastColor)
                    setHintTextColor(contrastColor.adjustAlpha(MEDIUM_ALPHA))
                    hint = "${getString(R.string.search)}…"
                    if (isQPlus()) textCursorDrawable = null
                }

            val background = searchView
                ?.findViewById<View>(androidx.appcompat.R.id.search_plate)
                ?.background

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                background?.colorFilter =
                    BlendModeColorFilter(contrastColor, BlendMode.MULTIPLY)
            } else {
                @Suppress("DEPRECATION")
                background?.setColorFilter(contrastColor, PorterDuff.Mode.MULTIPLY)
            }


        }
    }


    fun updateRecentsAppIcon() {
        if (!baseConfig.isUsingModifiedAppIcon) return
        val appIconIDs = getAppIconIDs()
        val currentAppIconColorIndex = getCurrentAppIconColorIndex()
        if (appIconIDs.size - 1 < currentAppIconColorIndex) return

        val recentsIcon =
            BitmapFactory.decodeResource(resources, appIconIDs[currentAppIconColorIndex])
        val title = getAppLauncherName()
        val color = baseConfig.primaryColor

        setTaskDescription(ActivityManager.TaskDescription(title, recentsIcon, color))
    }

    fun updateMenuItemColors(
        menu: Menu?,
        baseColor: Int = getProperStatusBarColor(),
        forceWhiteIcons: Boolean = false,
    ) {
        if (menu == null) return
        val color = if (forceWhiteIcons) Color.WHITE else baseColor.getContrastColor()
        for (i in 0 until menu.size) {
            try {
                menu[i].icon?.setTint(color)
            } catch (_: Exception) {
            }
        }
    }

    private fun getCurrentAppIconColorIndex(): Int {
        val appIconColor = baseConfig.appIconColor
        getAppIconColors().forEachIndexed { index, color ->
            if (color == appIconColor) return index
        }
        return 0
    }

    // ─── SAF helpers ─────────────────────────────────────────────────────────

    fun handleSAFDialog(path: String, callback: (success: Boolean) -> Unit): Boolean {
        hideKeyboard()
        callback(true)
        return false
    }

    fun handleSAFDialogSdk30(
        path: String,
        showRationale: Boolean = true,
        callback: (success: Boolean) -> Unit,
    ): Boolean {
        hideKeyboard()
        callback(true)
        return false
    }

    fun handleSAFCreateDocumentDialogSdk30(
        path: String,
        callback: (success: Boolean) -> Unit,
    ): Boolean {
        hideKeyboard()
        callback(true)
        return false
    }

    fun handleAndroidSAFDialog(
        path: String,
        openInSystemAppAllowed: Boolean = false,
        callback: (success: Boolean) -> Unit,
    ): Boolean {
        hideKeyboard()
        callback(true)
        return false
    }


    fun handlePermission(permissionId: Int, callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (hasPermission(permissionId)) {
            callback(true)
        } else {
            isAskingPermissions = true
            actionOnPermission = callback
            ActivityCompat.requestPermissions(
                this,
                arrayOf(getPermissionString(permissionId)),
                100,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }

    fun startCustomizationActivity() {
        Intent(applicationContext, CustomizationActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            startActivity(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun launchChangeAppLanguageIntent() {
        try {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                startActivity(this)
            }
        } catch (_: Exception) {
            openDeviceSettings()
        }
    }

    // ─── Default dialer / caller ID ───────────────────────────────────────────
    fun launchSetDefaultDialerIntent(onResult: ((granted: Boolean) -> Unit)? = null) {
        onDefaultDialerResult = onResult

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) ?: return
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return
            if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                onResult?.invoke(true)
                return
            }
            setDefaultDialerLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            )
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            try {
                setDefaultDialerLauncher.launch(intent)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    /**
     * Požádá o roli CALL_SCREENING (caller ID).
     * Vrací true pokud ji aplikace již drží, false pokud byl spuštěn dialog
     * nebo zařízení roli nepodporuje.
     */
    fun setDefaultCallerIdApp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return true

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            setDefaultCallerIdLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            )
        }
        return false
    }

    open fun onSelectionModeChanged(isActive: Boolean) {}
}
