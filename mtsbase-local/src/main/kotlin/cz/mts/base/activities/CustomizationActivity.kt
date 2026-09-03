package cz.mts.base.activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import cz.mts.base.R
import cz.mts.base.databinding.ActivityCustomizationBinding
import cz.mts.base.dialogs.ColorPickerDialog
import cz.mts.base.dialogs.ConfirmationAdvancedDialog
import cz.mts.base.dialogs.ConfirmationDialog
import cz.mts.base.dialogs.LineColorPickerDialog
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.extensions.adjustColor
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.checkAppIconColor
import cz.mts.base.extensions.getColoredMaterialStatusBarColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.getThemeId
import cz.mts.base.extensions.isDynamicTheme
import cz.mts.base.extensions.isSystemInDarkMode
import cz.mts.base.extensions.setFillWithStroke
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.APP_ICON_IDS
import cz.mts.base.helpers.APP_LAUNCHER_NAME
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import cz.mts.base.helpers.isSPlus
import cz.mts.base.models.MyTheme
import cz.mts.base.models.RadioItem
import kotlin.math.abs

class CustomizationActivity : BaseSimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    companion object {
        private const val THEME_LIGHT = 0
        private const val THEME_DARK = 1
        private const val THEME_TMOBILE = 8
        private const val THEME_KAKTUS = 9
        private const val THEME_DARK_RED = 3
        private const val THEME_BLACK_WHITE = 4
        private const val THEME_CUSTOM = 5
        private const val THEME_WHITE = 6
        private const val THEME_SYSTEM = 7
    }

    private var curNavBarColor = 0
    private var curTextColor = 0
    private var curSIM1Color = 0
    private var curSIM2Color = 0
    private var curBackgroundColor = 0
    private var curPrimaryColor = 0
    private var curAccentColor = 0
    private var curAppIconColor = 0
    private var curUseCustomSimColor = false
    private var savedThemeId = 0
    private var notSavedThemeId = 0
    private var originalAppIconColor = 0
    private var lastSavePromptTS = 0L
    private var hasUnsavedChanges = false
    private val predefinedThemes = LinkedHashMap<Int, MyTheme>()
    private var curPrimaryLineColorPicker: LineColorPickerDialog? = null

    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    override fun getRepositoryName() = null

    private val binding by viewBinding(ActivityCustomizationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        savedThemeId = baseConfig.themeIdSaved
        notSavedThemeId = savedThemeId

        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomSystem = listOf(binding.customizationHolder))

        initColorVariables()
        setupThemes()

        binding.settingsThemeAndColorsLabel.beVisibleIf(false)

        originalAppIconColor = baseConfig.appIconColor
        updateLabelColors()
        updateHeaderColors()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setTheme(getThemeId(getCurrentPrimaryColor()))

        if (!isDynamicTheme()) {
            updateBackgroundColor(getCurrentBackgroundColor())
        }

        curPrimaryLineColorPicker?.getSpecificColor()?.apply {
            setTheme(getThemeId(this))
        }

        setupTopAppBar(
            topAppBar = binding.appBar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = getColoredMaterialStatusBarColor()
        )

    }

    private fun refreshMenuItems() {
        binding.customizationToolbar.menu.findItem(R.id.save).isVisible = hasUnsavedChanges
    }

    private fun setupOptionsMenu() {
        binding.customizationToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> {
                    saveChanges()
                    true
                }

                else -> false
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (hasUnsavedChanges && System.currentTimeMillis() - lastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL) {
            promptSaveDiscard()
            true
        } else {
            false
        }
    }

    private fun setupThemes() {
        predefinedThemes.apply {
            put(
                THEME_SYSTEM, //private const val THEME_SYSTEM = 7
                if (isSPlus()) {
                    MyTheme(
                        labelId = R.string.system_default,
                        textColorId = R.color.theme_dark_text_color,
                        backgroundColorId = R.color.theme_dark_background_color,
                        primaryColorId = R.color.color_primary,
                        appIconColorId = R.color.color_primary,
                        navBarColorId = R.color.theme_dark_background_color
                    )
                }
                else {
                    val isDarkTheme = isSystemInDarkMode()
                    val textColor = if (isDarkTheme) R.color.theme_dark_text_color
                    else R.color.theme_light_text_color

                    val backgroundColor = if (isDarkTheme) R.color.theme_dark_background_color
                    else R.color.theme_light_background_color

                    MyTheme(
                        labelId = R.string.auto_light_dark_theme,
                        textColorId = textColor,
                        backgroundColorId = backgroundColor,
                        primaryColorId = R.color.color_primary,
                        appIconColorId = R.color.color_primary,
                        navBarColorId = backgroundColor
                    )
                }
            )

            put(
                THEME_LIGHT,
                MyTheme(
                    labelId = R.string.light_theme, //private const val THEME_LIGHT = 0
                    textColorId = R.color.theme_light_text_color,
                    backgroundColorId = R.color.theme_light_background_color,
                    primaryColorId = R.color.color_primary,
                    appIconColorId = R.color.color_primary,
                    navBarColorId = R.color.theme_light_background_color
                )
            )
            put(
                THEME_DARK,
                MyTheme(
                    labelId = R.string.dark_theme, //private const val THEME_DARK = 1
                    textColorId = R.color.theme_dark_text_color,
                    backgroundColorId = R.color.theme_dark_background_color,
                    primaryColorId = R.color.color_primary,
                    appIconColorId = R.color.color_primary,
                    navBarColorId = R.color.theme_dark_background_color
                )
            )
            put(
                THEME_DARK_RED,
                MyTheme(
                    labelId = R.string.dark_red, // private const val THEME_DARK_RED = 3
                    textColorId = R.color.theme_dark_text_color,
                    backgroundColorId = R.color.theme_dark_background_color,
                    primaryColorId = R.color.theme_dark_red_primary_color,
                    appIconColorId = R.color.md_red_700,
                    navBarColorId = R.color.theme_dark_background_color
                )
            )
            put(
                THEME_WHITE,
                MyTheme(
                    labelId = R.string.white, //private const val THEME_WHITE = 6
                    textColorId = R.color.dark_grey,
                    backgroundColorId = android.R.color.white,
                    primaryColorId = android.R.color.white,
                    appIconColorId = R.color.color_primary,
                    navBarColorId = android.R.color.white
                )
            )
            put(
                THEME_BLACK_WHITE,
                MyTheme(
                    labelId = R.string.black_white, //private const val THEME_BLACK_WHITE = 4
                    textColorId = android.R.color.white,
                    backgroundColorId = android.R.color.black,
                    primaryColorId = android.R.color.black,
                    appIconColorId = R.color.md_grey_black,
                    navBarColorId = android.R.color.black
                )
            )
            put(
                THEME_TMOBILE,
                MyTheme(
                    labelId = R.string.tmobile_theme,
                    textColorId = android.R.color.white,
                    backgroundColorId = android.R.color.black,
                    primaryColorId = R.color.md_pink_600,
                    appIconColorId = R.color.md_pink_600,
                    navBarColorId = R.color.md_pink_600
                )
            )
            put(
                THEME_KAKTUS,
                MyTheme(
                    labelId = R.string.kaktus_theme,
                    textColorId = android.R.color.black,
                    backgroundColorId = android.R.color.white,
                    primaryColorId = R.color.md_green_900,
                    appIconColorId = R.color.md_green_900,
                    navBarColorId = R.color.md_yellow_500_dark
                )
            )
            put(
                THEME_CUSTOM,
                MyTheme(
                    labelId = R.string.custom, //private const val THEME_CUSTOM = 5
                    textColorId = 0,
                    backgroundColorId = 0,
                    primaryColorId =0,
                    appIconColorId = R.color.color_primary,
                    navBarColorId = 0
                )
            )
        }

        setupThemePicker()
        setupColorsPickers()
    }

    private fun setDefaultCustomColors(forceAll: Boolean) {
        val defaultText = Color.parseColor("#000000")
        val defaultBackground = Color.parseColor("#FFF9EB")
        val defaultPrimary = Color.parseColor("#F57C00")
        val defaultNavBar = Color.parseColor("#FFF9EB")
        val defaultAppIcon = Color.parseColor("#2196F3")
        val defaultSIM1 = Color.parseColor("#F57C00")
        val defaultSIM2 = Color.parseColor("#FFF9EB")

        if (forceAll) {
            // Explicitní volba CUSTOM – nastav vše natvrdo
            baseConfig.customTextColor = defaultText
            baseConfig.customBackgroundColor = defaultBackground
            baseConfig.customPrimaryColor = defaultPrimary
            baseConfig.customNavBarColor = defaultNavBar
            baseConfig.customSIM1Color = defaultSIM1
            baseConfig.customSIM2Color = defaultSIM2
            baseConfig.customAccentColor = defaultPrimary
            baseConfig.customAppIconColor = defaultAppIcon
            baseConfig.customThemeInitialized = true
            baseConfig.useCustomSimColor = false
        } else {
            // CUSTOM byl vynucen – doplň jen chybějící hodnoty
            if (!baseConfig.customThemeInitialized) {
                baseConfig.customTextColor = curTextColor
                baseConfig.customBackgroundColor = curBackgroundColor
                baseConfig.customPrimaryColor = curPrimaryColor
                baseConfig.customAccentColor = curAccentColor
                baseConfig.customAppIconColor = curAppIconColor
                baseConfig.customNavBarColor = curNavBarColor
                baseConfig.customSIM1Color = curSIM1Color
                baseConfig.customSIM2Color = curSIM2Color
                baseConfig.useCustomSimColor = curUseCustomSimColor
                baseConfig.customThemeInitialized = true
            }

            if (baseConfig.customNavBarColor == 0)
                baseConfig.customNavBarColor = defaultNavBar

            if (baseConfig.customSIM1Color == 0)
                baseConfig.customSIM1Color = defaultSIM1

            if (baseConfig.customSIM2Color == 0)
                baseConfig.customSIM2Color = defaultSIM2
        }
    }

    private fun setupThemePicker() {
        binding.customizationTheme.text = getThemeText()
        updateAutoThemeFields()
        handleAccentColorLayout()
        updateNavBarVisibility()
        updateSIM12ColorVisibility()
        binding.customizationThemeHolder.setOnClickListener {
            if (baseConfig.wasAppIconCustomizationWarningShown) {
                themePickerClicked()
            } else {
                ConfirmationDialog(
                    activity = this,
                    message = "",
                    messageId = R.string.app_icon_color_warning,
                    positive = R.string.ok,
                    negative = 0
                ) {
                    baseConfig.wasAppIconCustomizationWarningShown = true
                    themePickerClicked()
                }
            }
        }
    }

    private fun themePickerClicked() {

        val items = arrayListOf<RadioItem>()
        for ((key, value) in predefinedThemes) {
            items.add(RadioItem(key, getString(value.labelId)))
        }

        RadioGroupDialog(this@CustomizationActivity, items, notSavedThemeId) {

            notSavedThemeId = it as Int

            if (it == THEME_CUSTOM) {
                setDefaultCustomColors(forceAll = true)
            }
            updateColorTheme( true)

            if (
                it != THEME_CUSTOM
                && it != THEME_SYSTEM
            //&& !baseConfig.wasCustomThemeSwitchDescriptionShown
            ) {
                //  baseConfig.wasCustomThemeSwitchDescriptionShown = true
                val snackbar = Snackbar.make(binding.customizationHolder, R.string.changing_color_description, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {}
                val snackbarText = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                snackbarText.maxLines = 4
                snackbarText.ellipsize = null // aby se text nekrátil
                snackbarText.textAlignment = View.TEXT_ALIGNMENT_TEXT_START // optional, aby byl text zarovnán vlevo

                snackbar.setBackgroundTint(getProperBackgroundColor().adjustColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()

                //  toast(R.string.changing_color_description)
            }

            updateMenuItemColors(binding.customizationToolbar.menu, getCurrentTopBarColor())
            setupTopAppBar(
                topAppBar = binding.appBar,
                navigationIcon = NavigationIcon.Arrow,
                topBarColor = getCurrentTopBarColor()
            )

        }
    }



    private fun updateColorTheme(useStored: Boolean = false) {
        binding.customizationTheme.text = getThemeText()

        if (notSavedThemeId == THEME_CUSTOM) {
            if (useStored) {
                curTextColor = baseConfig.customTextColor
                curBackgroundColor = baseConfig.customBackgroundColor
                curPrimaryColor = baseConfig.customPrimaryColor
                curAccentColor = baseConfig.customAccentColor
                curAppIconColor = baseConfig.customAppIconColor
                curNavBarColor = baseConfig.customNavBarColor
                curSIM1Color = baseConfig.customSIM1Color
                curSIM2Color = baseConfig.customSIM2Color
                curUseCustomSimColor = baseConfig.useCustomSimColor
                setTheme(getThemeId(curPrimaryColor))
                updateMenuItemColors(binding.customizationToolbar.menu, curPrimaryColor)
                setupTopAppBar(binding.appBar, NavigationIcon.Arrow, curPrimaryColor)
                setupColorsPickers()
            } else {
                baseConfig.customPrimaryColor = curPrimaryColor
                baseConfig.customAccentColor = curAccentColor
                baseConfig.customBackgroundColor = curBackgroundColor
                baseConfig.customTextColor = curTextColor
                baseConfig.customAppIconColor = curAppIconColor
                baseConfig.customNavBarColor = curNavBarColor
                baseConfig.customSIM1Color = curSIM1Color
                baseConfig.customSIM2Color = curSIM2Color
                baseConfig.useCustomSimColor = curUseCustomSimColor
            }
        } else {
            val theme = predefinedThemes[notSavedThemeId] ?: predefinedThemes[THEME_LIGHT]!!

            curTextColor = getColor(theme.textColorId)
            curBackgroundColor = getColor(theme.backgroundColorId)
            curNavBarColor = getColor(theme.navBarColorId)

            if (notSavedThemeId != THEME_SYSTEM) {
                curPrimaryColor = getColor(theme.primaryColorId)
                curAppIconColor = getColor(theme.appIconColorId)
                if (curAccentColor == 0) curAccentColor = getColor(R.color.color_primary)
            }

            setTheme(getThemeId(getCurrentPrimaryColor()))
            colorChanged()
            updateMenuItemColors(binding.customizationToolbar.menu, getCurrentTopBarColor())
            setupTopAppBar(
                topAppBar = binding.appBar,
                navigationIcon = NavigationIcon.Arrow,
                topBarColor = getCurrentTopBarColor()
            )

        }

        hasUnsavedChanges = true
        refreshMenuItems()
        updateLabelColors(getCurrentTextColor())
        updateHeaderColors(getCurrentAccentOrPrimaryColor())
        updateBackgroundColor(getCurrentBackgroundColor())
        updateAutoThemeFields()
        handleAccentColorLayout()
        updateNavBarVisibility()
        updateSIM12ColorVisibility()
    }



    private fun getThemeText(): String {
        val label = predefinedThemes[notSavedThemeId]?.labelId ?: R.string.custom
        return getString(label)
    }

    private fun updateAutoThemeFields() {
        arrayOf(
            binding.customizationTextColorHolder,
            binding.customizationBackgroundColorHolder
        ).forEach {
            it.beVisibleIf(notSavedThemeId != THEME_SYSTEM)
        }

        binding.customizationPrimaryColorHolder.beVisibleIf(
            beVisible = notSavedThemeId != THEME_SYSTEM || !isSPlus()
        )
    }

    private fun promptSaveDiscard() {
        lastSavePromptTS = System.currentTimeMillis()
        ConfirmationAdvancedDialog(
            activity = this,
            message = "",
            messageId = R.string.save_before_closing,
            positive = R.string.save,
            negative = R.string.discard
        ) {
            if (it) {
                saveChanges()
            } else {
                resetColors()
                finish()
            }
        }
    }

    private fun ensureCustomTheme() {
        if (notSavedThemeId != THEME_CUSTOM) {
            notSavedThemeId = THEME_CUSTOM
            setDefaultCustomColors(forceAll = false)
        }
    }
    private fun saveChanges() {

        baseConfig.apply {
            textColor = curTextColor
            backgroundColor = curBackgroundColor
            primaryColor = curPrimaryColor
            accentColor = curAccentColor
            appIconColor = curAppIconColor
            navBarColor = if (notSavedThemeId == THEME_CUSTOM) curNavBarColor
            else curBackgroundColor
            useCustomSimColor = curUseCustomSimColor
            themeIdSaved = notSavedThemeId
            themeChanged = true
        }

        savedThemeId = notSavedThemeId
        if (curAppIconColor != originalAppIconColor) checkAppIconColor()
        hasUnsavedChanges = false
        finish() //MTSX
    }

    private fun resetColors() {
        hasUnsavedChanges = false
        notSavedThemeId = savedThemeId
        initColorVariables()
        setupColorsPickers()
        updateBackgroundColor()
        // MTSX updateActionbarColor()
        refreshMenuItems()
        updateLabelColors(getCurrentTextColor())
        updateHeaderColors(getCurrentAccentOrPrimaryColor())
        updateNavBarVisibility()
        updateAutoThemeFields()
        updateSIM12ColorVisibility()
    }

    private fun initColorVariables() {
        curTextColor = baseConfig.textColor
        curBackgroundColor = baseConfig.backgroundColor
        curPrimaryColor = baseConfig.primaryColor
        curAccentColor = baseConfig.accentColor
        curAppIconColor = baseConfig.appIconColor
        curUseCustomSimColor = baseConfig.useCustomSimColor

        curNavBarColor = if (savedThemeId == THEME_CUSTOM) baseConfig.navBarColor
        else curBackgroundColor

        curSIM1Color = if (savedThemeId == THEME_CUSTOM) baseConfig.customSIM1Color
        else curPrimaryColor

        curSIM2Color = if (savedThemeId == THEME_CUSTOM) baseConfig.customSIM2Color
        else curPrimaryColor

        if (curAccentColor == 0) curAccentColor = curPrimaryColor
    }

    private fun setupColorsPickers() {
        val textColor = getCurrentTextColor()
        val backgroundColor = getCurrentBackgroundColor()
        val primaryColor = getCurrentPrimaryColor()
        binding.customizationTextColor.setFillWithStroke(textColor, backgroundColor)
        binding.customizationPrimaryColor.setFillWithStroke(primaryColor, backgroundColor)
        binding.customizationAccentColor.setFillWithStroke(curAccentColor, backgroundColor)
        binding.customizationBackgroundColor.setFillWithStroke(backgroundColor, backgroundColor)
        binding.customizationAppIconColor.setFillWithStroke(curAppIconColor, backgroundColor)
        binding.customizationNavbarColor.setFillWithStroke(curNavBarColor, backgroundColor)
        binding.customizationSim1Color.setFillWithStroke(curSIM1Color, backgroundColor)
        binding.customizationSim2Color.setFillWithStroke(curSIM2Color, backgroundColor)


        binding.customizationTextColorHolder.setOnClickListener { pickTextColor() }
        binding.customizationBackgroundColorHolder.setOnClickListener { pickBackgroundColor() }
        binding.customizationPrimaryColorHolder.setOnClickListener { pickPrimaryColor() }
        binding.customizationAccentColorHolder.setOnClickListener { pickAccentColor() }
        binding.customizationNavbarColorHolder.setOnClickListener { pickNavBarColor() }
        binding.customizationSim1ColorHolder.setOnClickListener { pickSIM1Color() }
        binding.customizationSim2ColorHolder.setOnClickListener { pickSIM2Color() }


        handleAccentColorLayout()
        binding.customizationAppIconColorHolder.setOnClickListener {
            pickAppIconColor()
        }
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun colorChanged() {
        hasUnsavedChanges = true
        setupColorsPickers()
        refreshMenuItems()
    }

    private fun setCurrentNavBarColor(color: Int) {
        curNavBarColor = color
    }

    private fun setCurrentTextColor(color: Int) {
        curTextColor = color
        updateLabelColors(color)
    }

    private fun setCurrentSIM1Color(color: Int) {
        curSIM1Color = color
    }

    private fun setCurrentSIM2Color(color: Int) {
        curSIM2Color = color
    }

    private fun setCurrentBackgroundColor(color: Int) {
        curBackgroundColor = color
        updateBackgroundColor(color)
    }

    private fun setCurrentPrimaryColor(color: Int) {
        curPrimaryColor = color
        updateHeaderColors(color)
    }

    private fun updateNavBarVisibility() {
        binding.customizationNavbarColorHolder.beVisibleIf(notSavedThemeId == THEME_CUSTOM)
        binding.customizationNavbarColorLabel.text = getString(R.string.customization_navbar_color_label)
    }

    private fun updateSIM12ColorVisibility() {
        binding.customizationSim1ColorHolder.beVisibleIf(notSavedThemeId == THEME_CUSTOM && curUseCustomSimColor)
        binding.customizationSim2ColorHolder.beVisibleIf(notSavedThemeId == THEME_CUSTOM && curUseCustomSimColor)
        binding.customizationSim1ColorLabel.text = getString(R.string.customization_sim1_color_label)
        binding.customizationSim2ColorLabel.text = getString(R.string.customization_sim2_color_label)
        setupCustomizeSimColors()
    }


    private fun handleAccentColorLayout() {
        binding.customizationAccentColorHolder.beVisibleIf(
            beVisible = notSavedThemeId == THEME_WHITE
                || notSavedThemeId == THEME_BLACK_WHITE
        )
        binding.customizationAccentColorLabel.text = getString(
            if (notSavedThemeId == THEME_WHITE || isCurrentWhiteTheme()) {
                R.string.accent_color_white
            } else {
                R.string.accent_color_black_and_white
            }
        )
    }

    private fun isCurrentWhiteTheme(): Boolean {
        return notSavedThemeId == THEME_WHITE
        //curTextColor == DARK_GREY
        // && curPrimaryColor == Color.WHITE
        // && curBackgroundColor == Color.WHITE
    }

    private fun isCurrentBlackAndWhiteTheme(): Boolean {
        return notSavedThemeId == THEME_BLACK_WHITE //curTextColor == Color.WHITE
        // && curPrimaryColor == Color.BLACK
        // && curBackgroundColor == Color.BLACK
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, curTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curTextColor, color)) {
                    ensureCustomTheme()
                    setCurrentTextColor(color)
                    colorChanged()
                    updateColorTheme()
                }
            }
        }
    }


    private fun pickSIM1Color() {
        ColorPickerDialog(this, curSIM1Color) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curSIM1Color, color)) {
                    ensureCustomTheme()
                    setCurrentSIM1Color(color)
                    colorChanged()
                    updateColorTheme()
                }
            }
        }
    }

    private fun pickSIM2Color() {
        ColorPickerDialog(this, curSIM2Color) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curSIM2Color, color)) {
                    ensureCustomTheme()
                    setCurrentSIM2Color(color)
                    colorChanged()
                    updateColorTheme()
                }            }
        }
    }

    private fun pickNavBarColor() {
        ColorPickerDialog(this, curNavBarColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curNavBarColor, color)) {
                    setCurrentNavBarColor(color)
                    colorChanged()
                    //updateColorTheme(getCurrentThemeId())  //navbar je pouze u custom theme !!!
                }
            }
        }
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, curBackgroundColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curBackgroundColor, color)) {
                    ensureCustomTheme()
                    setCurrentBackgroundColor(color)
                    colorChanged()
                    updateColorTheme()
                }
            }
        }
    }

    private fun pickPrimaryColor() {

        curPrimaryLineColorPicker = LineColorPickerDialog(
            activity = this,
            color = curPrimaryColor,
            isPrimaryColorPicker = true,
            appBar = binding.appBar,
        ) { wasPositivePressed, color ->
            curPrimaryLineColorPicker = null
            if (wasPositivePressed) {
                if (hasColorChanged(curPrimaryColor, color)) {
                    ensureCustomTheme()
                    setCurrentPrimaryColor(color)
                    colorChanged()
                    updateColorTheme()
                    setTheme(getThemeId(color))
                }
                updateMenuItemColors(binding.customizationToolbar.menu, color)
                setupTopAppBar(binding.appBar, NavigationIcon.Arrow, color)
            } else {
                setTheme(getThemeId(curPrimaryColor))
                updateMenuItemColors(binding.customizationToolbar.menu, curPrimaryColor)
                setupTopAppBar(binding.appBar, NavigationIcon.Arrow, curPrimaryColor)
                updateTopBarColors(binding.appBar, curPrimaryColor)
            }
        }
    }

    private fun pickAccentColor() {
        ColorPickerDialog(this, curAccentColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curAccentColor, color)) {
                    curAccentColor = color
                    colorChanged()
                    updateHeaderColors(curAccentColor)
                    updateTopBarColors(binding.appBar, getCurrentTopBarColor())
                }
            }
        }
    }

    private fun pickAppIconColor() {
        LineColorPickerDialog(
            activity = this,
            color = curAppIconColor,
            isPrimaryColorPicker = false,
            primaryColors = R.array.md_app_icon_colors,
            appIconIDs = getAppIconIDs()
        ) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                if (hasColorChanged(curAppIconColor, color)) {
                    curAppIconColor = color
                    colorChanged()
                    //updateColorTheme()
                }
            }
        }
    }

    private fun updateLabelColors(textColor: Int = getProperTextColor()) {
        arrayListOf(
            binding.customizationThemeLabel,
            binding.customizationTheme,
            binding.customizationTextColorLabel,
            binding.customizationBackgroundColorLabel,
            binding.customizationPrimaryColorLabel,
            binding.customizationAccentColorLabel,
            binding.customizationAppIconColorLabel,
            binding.customizationNavbarColorLabel,
            binding.customizationSim1ColorLabel,
            binding.customizationSim2ColorLabel,
            binding.settingsUseCustomSimColor
        ).forEach {
            it.setTextColor(textColor)
        }
    }

    private fun updateHeaderColors(primaryColor: Int = getProperPrimaryColor()) {
        arrayListOf(
            binding.settingsThemeAndColorsLabel
        ).forEach {
            it.setTextColor(primaryColor)
        }
    }

    private fun getCurrentTextColor() = when {
        (notSavedThemeId == THEME_SYSTEM && isSPlus()) -> getColor(R.color.you_neutral_text_color)
        else -> curTextColor
    }

    private fun getCurrentBackgroundColor() = when {
        (notSavedThemeId == THEME_SYSTEM && isSPlus()) -> getColor(R.color.you_background_color)
        else -> curBackgroundColor
    }

    private fun getCurrentPrimaryColor() = when {
        (notSavedThemeId == THEME_SYSTEM && isSPlus()) -> getColor(R.color.you_primary_color)
        else -> curPrimaryColor
    }

    private fun getCurrentTopBarColor() = when {
        (notSavedThemeId == THEME_SYSTEM && isSPlus()) ->  getColor(R.color.you_status_bar_color)
        (isCurrentWhiteTheme() || isCurrentBlackAndWhiteTheme()) ->  curAccentColor
        else -> curPrimaryColor
    }

    private fun getCurrentAccentOrPrimaryColor() = when {
        isCurrentWhiteTheme() || isCurrentBlackAndWhiteTheme() -> curAccentColor
        else -> getCurrentPrimaryColor()
    }

    private fun setupCustomizeSimColors() {

        val switch = binding.settingsUseCustomSimColor
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        //pozadí switche
        val trackColors = intArrayOf(
            curPrimaryColor,   // ON
            Color.GRAY     // OFF
        )
        switch.trackTintList = ColorStateList(trackStates, trackColors)
        //tečka
        val thumbColors = intArrayOf(
            Color.WHITE,   // ON
            Color.DKGRAY   // OFF
        )
        switch.thumbTintList = ColorStateList(trackStates, thumbColors)

        binding.apply {
            settingsUseCustomSimColorHolder.beVisibleIf(notSavedThemeId == THEME_CUSTOM)

            settingsUseCustomSimColor.isChecked = curUseCustomSimColor
            settingsUseCustomSimColorHolder.setOnClickListener {
                settingsUseCustomSimColor.toggle()
                curUseCustomSimColor = settingsUseCustomSimColor.isChecked
                hasUnsavedChanges = true
                refreshMenuItems()
                updateSIM12ColorVisibility()

            }
        }
    }

}
