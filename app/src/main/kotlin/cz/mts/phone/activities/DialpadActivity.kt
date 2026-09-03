package cz.mts.phone.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getColoredDrawableWithColor
import cz.mts.base.extensions.getColorStateList
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.isDefaultDialer
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.normalizeString
import cz.mts.base.extensions.onTextChangeListener
import cz.mts.base.extensions.performHapticFeedback
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.value
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.isOreoPlus
import cz.mts.base.helpers.KEY_PHONE
import cz.mts.base.helpers.KeypadHelper
import cz.mts.base.helpers.LOWER_ALPHA_INT
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.databinding.ActivityDialpadBinding
import cz.mts.phone.extensions.addCharacter
import cz.mts.phone.extensions.boundingBox
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.disableKeyboard
import cz.mts.phone.extensions.getKeyEvent
import cz.mts.phone.extensions.setupWithContacts
import cz.mts.base.helpers.DIALPAD_TONE_LENGTH_MS
import cz.mts.phone.helpers.ToneGeneratorHelper
import cz.mts.base.models.SpeedDial
import cz.mts.phone.R
import cz.mts.phone.extensions.startContactDetailsIntentID
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DialpadActivity : SimpleActivity() {
    override var customNavBarLightIcons: Boolean? = null

    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    private var allContacts = ArrayList<Contact>()
    private var bAllContactsLoaded = false
    private var speedDialValues = ArrayList<SpeedDial>()
    private var toneGeneratorHelper: ToneGeneratorHelper? = null

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var bLongClick = false
    private var bLastOutgoingNumber = false
    private var filterJob: Job? = null


    // ── Lokalizace ──────────────────────────────────────────────────────────

    private var hasRussianLocale = false
    private var hasUkrainianLocale = false

    /**
     * T9 mapa pro ruskou cyrilici.
     * Ruská abeceda: а–я + ё, ъ, ы, э (písmena chybějící v ukrajinštině).
     */
    private val russianCharsMap by lazy {
        hashMapOf(
            'а' to 2, 'б' to 2, 'в' to 2, 'г' to 2,
            'д' to 3, 'е' to 3, 'ё' to 3, 'ж' to 3, 'з' to 3,
            'и' to 4, 'й' to 4, 'к' to 4, 'л' to 4,
            'м' to 5, 'н' to 5, 'о' to 5, 'п' to 5,
            'р' to 6, 'с' to 6, 'т' to 6, 'у' to 6,
            'ф' to 7, 'х' to 7, 'ц' to 7, 'ч' to 7,
            'ш' to 8, 'щ' to 8, 'ъ' to 8, 'ы' to 8,
            'ь' to 9, 'э' to 9, 'ю' to 9, 'я' to 9
        )
    }

    /**
     * T9 mapa pro ukrajinskou cyrilici.
     *
     * Oproti ruštině:
     *  - CHYBÍ: ё, ъ, ы, э
     *  - PŘIBÝVÁ: є (varianta е), і (samostatné і), ї (і s přehláskou), ґ (tvrdé г)
     *
     * Mapování:
     *  2 → а б в г ґ
     *  3 → д е є ж з
     *  4 → и і ї й к л
     *  5 → м н о п
     *  6 → р с т у
     *  7 → ф х ц ч
     *  8 → ш щ ь ю
     *  9 → я
     */
    private val ukrainianCharsMap by lazy {
        hashMapOf(
            'а' to 2, 'б' to 2, 'в' to 2, 'г' to 2, 'ґ' to 2,
            'д' to 3, 'е' to 3, 'є' to 3, 'ж' to 3, 'з' to 3,
            'и' to 4, 'і' to 4, 'ї' to 4, 'й' to 4, 'к' to 4, 'л' to 4,
            'м' to 5, 'н' to 5, 'о' to 5, 'п' to 5,
            'р' to 6, 'с' to 6, 'т' to 6, 'у' to 6,
            'ф' to 7, 'х' to 7, 'ц' to 7, 'ч' to 7,
            'ш' to 8, 'щ' to 8, 'ь' to 8, 'ю' to 8,
            'я' to 9
        )
    }

    // ────────────────────────────────────────────────────────────────────────

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        hasRussianLocale = Locale.getDefault().language == "ru"
        hasUkrainianLocale = Locale.getDefault().language == "uk"

        binding.apply {
            setupEdgeToEdge(padBottomImeAndSystem = listOf(dialpadList, dialpadHolder))
            setupMaterialScrollListener(dialpadList, dialpadAppbar)
        }

        setupDialpadBackground()
        setupCyrillicLabels()
        setupCharClickListeners()
        setupOptionsMenu()

        speedDialValues = config.getSpeedDialValues()
        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput(); true }
            dialpadCallButton.setOnClickListener { initCall(dialpadInput.value) }
            dialpadCallButton.setOnLongClickListener { initCallWithSimSelector() }
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
            dialpadInput.requestFocus()
            dialpadInput.disableKeyboard()
        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts
            bAllContactsLoaded = true
            runOnUiThread {
                val prefilledNumber = checkDialIntent()
                dialpadValueChanged(prefilledNumber.orEmpty())
            }
        }

        val primaryColor = getProperPrimaryColor()
        binding.apply {
            val callIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = primaryColor.getContrastColor()
            )
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(primaryColor)

            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = primaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = primaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = primaryColor.getColorStateList()
        }
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        updateTextColors(binding.dialpadHolder)
        binding.dialpadClearChar.applyColorFilter(getProperTextColor())
        setupTopAppBar(binding.dialpadAppbar, NavigationIcon.Arrow)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Uvolnění zdrojů – předejde memory leaku
        toneGeneratorHelper?.stopTone()
        toneGeneratorHelper = null
        longPressHandler.removeCallbacksAndMessages(null)
        filterJob?.cancel()
    }

    // ── Setup helpers ────────────────────────────────────────────────────────

    private fun setupDialpadBackground() {
        binding.dialpadWrapper.apply {
            if (config.hideDialpadNumbers) {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder,
                dialpad4Holder, dialpad5Holder, dialpad6Holder, dialpad7Holder,
                dialpad8Holder, dialpad9Holder, dialpadPlusHolder,
                dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }
        }
    }

    /**
     * Přidá písmena cyrilice pod číslice klávesnice.
     * Ruština a Ukrajinština mají odlišná písmena – viz komentáře u map výše.
     */
    private fun setupCyrillicLabels() {
        val (locale, labelsMap) = when {
            hasRussianLocale -> "ru" to mapOf(
                2 to "\nАБВГ",
                3 to "\nДЕЁЖЗ",
                4 to "\nИЙКЛ",
                5 to "\nМНОП",
                6 to "\nРСТУ",
                7 to "\nФХЦЧ",
                8 to "\nШЩЪЫ",
                9 to "\nЬЭЮЯ"
            )
            hasUkrainianLocale -> "uk" to mapOf(
                2 to "\nАБВГҐ",
                3 to "\nДЕЄЖЗ",
                4 to "\nИІЇЙКЛ",
                5 to "\nМНОП",
                6 to "\nРСТУ",
                7 to "\nФХЦЧ",
                8 to "\nШЩЬЮ",
                9 to "\nЯ"
            )
            else -> return  // Žádná cyrilice – nic nepřidáváme
        }

        binding.dialpadWrapper.apply {
            val digitViews = mapOf(
                2 to dialpad2Letters, 3 to dialpad3Letters,
                4 to dialpad4Letters, 5 to dialpad5Letters,
                6 to dialpad6Letters, 7 to dialpad7Letters,
                8 to dialpad8Letters, 9 to dialpad9Letters
            )

            labelsMap.forEach { (digit, label) ->
                digitViews[digit]?.append(label)
            }

            // Ukrajinskych písmen na klávese 4 je více – zmenšíme font i pro ruštinu
            val fontSize = resources.getDimension(R.dimen.small_text_size)
            digitViews.values.forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
        }
    }

    private fun setupCharClickListeners() {
        binding.dialpadWrapper.apply {
            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    // ── Dialpad logika ───────────────────────────────────────────────────────

    private fun checkDialIntent(): String? {
        var cleanedNumber = ""

        val numberMTS = intent.getStringExtra("EXTRA_PHONE_NUMBER")
        if (numberMTS.isNullOrEmpty()) {
            if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
                && intent.data != null
                && intent.dataString?.contains("tel:") == true
            ) {
                cleanedNumber = Uri.decode(intent.dataString).substringAfter("tel:")
            }
        } else {
            cleanedNumber = normalizeDigitsOnly(
                numberMTS
                    .replace("tel:", "")
                    .replace(" ", "")
                    .replace("%20", "")   // mezera
                    .replace("%2B", "+")  // +
                    .replace("%3A", "")   // :
            )
        }

        return if (cleanedNumber.isNotBlank()) {
            binding.dialpadInput.setText(cleanedNumber)
            binding.dialpadInput.setSelection(cleanedNumber.length)
            cleanedNumber
        } else {
            null
        }
    }

    private fun addNumberToContact() {
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, binding.dialpadInput.value)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            launchActivityIntent(this)
        }
    }

    private fun dialpadValueChangedOLD(text: String) {
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            handleSecretCode(text.substring(4, text.length - 4))
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered: List<Contact> = if (config.enableT9dialpad) {
            filterContactsT9(text)
        } else {
            filterContactsNumeric(text)
        }

        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        ContactsAdapter(
            activity = this,
            contacts = ArrayList(filtered),
            recyclerView = binding.dialpadList,
            highlightText = text,
            itemClick = {
                mtsGlobalAll.showNumberPickerDialog(this, it as Contact, -2, onDismiss = { finish() })
                clearInputWithDelay()
            },
            profileIconClick = {
                val contact = it as Contact
                startContactDetailsIntentID(contact.rawId.toLong(), contact.source)
            }
        ).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
        bLongClick = false
    }

    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            handleSecretCode(text.substring(4, text.length - 4))
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        // Zrušit předchozí běžící filtraci – řeší race condition při rychlém psaní
        filterJob?.cancel()
        filterJob = lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                if (config.enableT9dialpad) {
                    filterContactsT9(text)
                } else {
                    filterContactsNumeric(text)
                }
            }

            // ensureActive() není nutné, withContext už propaguje CancellationException,
            // ale pojistka pro čitelnost kódu:
            ensureActive()

            applyFilteredContacts(filtered, text)
        }
    }

    private fun applyFilteredContacts(filtered: List<Contact>, text: String) {
        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        val existingAdapter = binding.dialpadList.adapter as? ContactsAdapter
        if (existingAdapter != null) {
            existingAdapter.updateItems(filtered, text)
        } else {
            ContactsAdapter(
                activity = this,
                contacts = ArrayList(filtered),
                recyclerView = binding.dialpadList,
                highlightText = text,
                itemClick = {
                    mtsGlobalAll.showNumberPickerDialog(this, it as Contact, -2, onDismiss = { finish() })
                    clearInputWithDelay()
                },
                profileIconClick = {
                    val contact = it as Contact
                    startContactDetailsIntentID(contact.rawId.toLong(), contact.source)
                }
            ).apply {
                binding.dialpadList.adapter = this
            }
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
        bLongClick = false
    }

    /**
     * T9 filtrování – podporuje ruskou i ukrajinskou cyrilici.
     * Každá lokalizace má vlastní mapu znaků → číslic.
     */
    private fun filterContactsT9(text: String): List<Contact> {
        return allContacts.filter { contact ->
            var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                contact.getNameToDisplay().normalizeString()
            ).filterNot { it.isWhitespace() }

            when {
                hasRussianLocale -> {
                    convertedName = convertedName.lowercase(Locale.getDefault())
                        .map { char -> russianCharsMap.getOrElse(char) { char } }
                        .joinToString("")
                }
                hasUkrainianLocale -> {
                    convertedName = convertedName.lowercase(Locale.getDefault())
                        .map { char -> ukrainianCharsMap.getOrElse(char) { char } }
                        .joinToString("")
                }
            }

            contact.doesContainPhoneNumber(text) || convertedName.contains(text, ignoreCase = true)
        }.sortedWith(compareBy { !it.doesContainPhoneNumber(text) })
    }

    /** Numerické filtrování – pouze shoda číslic bez T9. */
    private fun filterContactsNumeric(text: String): List<Contact> {
        val normalizedInput = text.filter { it.isDigit() }
        return allContacts.filter { contact ->
            contact.phoneNumbers.any { phone ->
                phone.normalizedNumber.filter { it.isDigit() }.contains(normalizedInput)
            }
        }
    }

    private fun handleSecretCode(secretCode: String) {
        if (isOreoPlus()) {
            if (isDefaultDialer()) {
                getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
            } else {
                launchSetDefaultDialerIntent()
            }
        } else {
            sendBroadcast(
                Intent("android.provider.Telephony.SECRET_CODE").apply {
                    data = "android_secret_code://$secretCode".toUri()
                }
            )
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadInput.setText("")
    }

    private fun clearInputWithDelay() {
        lifecycleScope.launch {
            delay(1000)
            clearInput()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(binding.dialpadInput.value)
        }
    }

    @Suppress("DEPRECATION") // Vibrator API 31+ = VibratorManager; ponecháno pro jednoduchost
    private fun zavibruj() {
        val v = getSystemService(Vibrator::class.java)
        if (v?.hasVibrator() == true) {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        if (!bLongClick) zavibruj()

        val sNumberForCall = config.lastOutgoingCallNumber

        if (number.isEmpty() && sNumberForCall.isEmpty()) return

        if (number.isEmpty() && sNumberForCall.isNotEmpty()) {
            bLastOutgoingNumber = true
            binding.dialpadInput.setText(sNumberForCall)
            binding.dialpadInput.setSelection(sNumberForCall.length)
            return
        }

        when {
            number == "*" || number == "#" -> {
                bLastOutgoingNumber = true
                mtsGlobalAll.mtsSwhoDetailDialer(this, sNumberForCall, bLongClick, config.lastOutgoingCallNumberSim, onDismiss = { finish() })
                clearInputWithDelay()
            }
            number == "*#06#" -> {
                mtsGlobalAll.showIMEIDialog(this)
                return
            }
            else -> {
                val iSimSlot = if (number == sNumberForCall) config.lastOutgoingCallNumberSim
                               else 0
                mtsGlobalAll.mtsSwhoDetailDialer(this, number, bLongClick, iSimSlot, onDismiss = { finish() })
                clearInputWithDelay()
            }
        }
    }

    private fun initCallWithSimSelector(): Boolean {
        zavibruj()
        val number = binding.dialpadInput.value
        if (number.isEmpty()) return false
        bLongClick = true
        return false
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, speedDial.getName(this))
                return true
            }
        }
        return false
    }

    // ── Tóny a haptika ───────────────────────────────────────────────────────

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val handled = speedDial(char.digitToInt())
            if (handled) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed(
                            { performLongClick(view, char) },
                            longPressTimeout
                        )
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) longPressHandler.removeCallbacksAndMessages(null)
                }
                MotionEvent.ACTION_MOVE -> {
                    val inBounds = if (event.rawX.isNaN() || event.rawY.isNaN()) false
                    else view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())

                    if (!inBounds) {
                        stopDialpadTone(char)
                        if (longClickable) longPressHandler.removeCallbacksAndMessages(null)
                    }
                }
            }
            false
        }
    }
}
