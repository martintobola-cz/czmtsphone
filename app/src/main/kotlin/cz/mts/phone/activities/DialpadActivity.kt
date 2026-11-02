package cz.mts.phone.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cz.mts.base.extensions.*
import cz.mts.base.helpers.*
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.models.SpeedDial
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.R
import cz.mts.phone.adapters.ContactsAdapter
import cz.mts.phone.databinding.ActivityDialpadBinding
import cz.mts.phone.extensions.*
import cz.mts.phone.helpers.CallHelper
import cz.mts.phone.helpers.CallHelper.EXTRA_PHONE_NUMBER
import cz.mts.phone.helpers.ToneGeneratorHelper
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.roundToInt
import cz.mts.base.extensions.baseConfig as config

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

    /**
     * Cyrilické rozložení klávesnice. Jediný zdroj pravdy:
     *  - [letters] = písmena zobrazená pod číslicí (velká písmena),
     *  - [charsMap] = T9 mapa znak → číslice, odvozená automaticky z [letters].
     * Popisky kláves a T9 filtrování se tak nemohou rozjet.
     */
    private class CyrillicLayout(val letters: Map<Int, String>) {
        val charsMap: Map<Char, Int> = HashMap<Char, Int>().also { map ->
            letters.forEach { (digit, chars) ->
                chars.forEach { map[it.lowercaseChar()] = digit }
            }
        }
    }

    private val cyrillicLayouts = mapOf(
        "ru" to CyrillicLayout(
            mapOf(
                2 to "АБВГ",
                3 to "ДЕЁЖЗ",
                4 to "ИЙКЛ",
                5 to "МНОП",
                6 to "РСТУ",
                7 to "ФХЦЧ",
                8 to "ШЩЪЫ",
                9 to "ЬЭЮЯ"
            )
        ),
        "uk" to CyrillicLayout(
            mapOf(
                2 to "АБВГҐ",
                3 to "ДЕЄЖЗ",
                4 to "ИІЇЙКЛ",
                5 to "МНОП",
                6 to "РСТУ",
                7 to "ФХЦЧ",
                8 to "ШЩ",
                9 to "ЮЯЬ"
            )
        ),
        "sr" to CyrillicLayout(
            mapOf(
                2 to "АБЦЧЋ",
                3 to "ДЕФЂ",
                4 to "ГХИ",
                5 to "ЈКЛ",
                6 to "МНО",
                7 to "ПЉРСШ",
                8 to "ТУВ",
                9 to "ЊЏЗЖ"
            )
        )
    )

    /** Aktivní rozložení podle jazyka systému; null = žádná cyrilice. */
    private var cyrillicLayout: CyrillicLayout? = null

    private fun resolveCyrillicLayout(): CyrillicLayout? {
        val locale = Locale.getDefault()
        // Srbská latinka (sr-Latn) cyrilické popisky nepotřebuje
        if (locale.language == "sr" && locale.script == "Latn") return null
        return cyrillicLayouts[locale.language]
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        checkDialIntent(intent)
    }

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        cyrillicLayout = resolveCyrillicLayout()

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
     * Přidá písmena cyrilice pod číslice klávesnice podle aktivního rozložení.
     */
    private fun setupCyrillicLabels() {
        val layout = cyrillicLayout ?: return  // Žádná cyrilice – nic nepřidáváme

        binding.dialpadWrapper.apply {
            val digitViews = mapOf(
                2 to dialpad2Letters, 3 to dialpad3Letters,
                4 to dialpad4Letters, 5 to dialpad5Letters,
                6 to dialpad6Letters, 7 to dialpad7Letters,
                8 to dialpad8Letters, 9 to dialpad9Letters
            )

            layout.letters.forEach { (digit, letters) ->
                digitViews[digit]?.append("\n$letters")
            }

            // Cyrilických písmen na klávese je více – zmenšíme font
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
                R.id.send_message -> sendMessage()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    // ── Dialpad logika ───────────────────────────────────────────────────────

    private fun checkDialIntent(intent: Intent = this.intent): String? {
        var cleanedNumber = ""

        val numberMTS = intent.getStringExtra(EXTRA_PHONE_NUMBER)

        if (numberMTS.isNullOrEmpty()) {
            if (
                (intent.action == Intent.ACTION_DIAL ||
                    intent.action == Intent.ACTION_VIEW) &&
                intent.data != null &&
                intent.dataString?.contains("tel:") == true
            ) {
                cleanedNumber = Uri.decode(intent.dataString!!).substringAfter("tel:")
            }
        } else {
            cleanedNumber = normalizeDigitsOnly(
                numberMTS
                    .replace("tel:", "")
                    .replace(" ", "")
                    .replace("%20", "")
                    .replace("%2B", "+")
                    .replace("%3A", "")
            , true) // pokud *# tak s číslem nic nedělá a vrátí ho jak bylo!!!
        }

        return if (cleanedNumber.isNotBlank()) {
            binding.dialpadInput.setText(cleanedNumber)
            binding.dialpadInput.setSelection(cleanedNumber.length)
            cleanedNumber
        } else {
            null
        }
    }


    private fun sendMessage() {
        val number = binding.dialpadInput.value
        if (number.isBlank()) return
        launchSendSMSIntent(number)
    }
    private fun addNumberToContact() {
        val number = binding.dialpadInput.value
        if (number.isBlank()) return

        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, binding.dialpadInput.value)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            launchActivityIntent(this)
        }
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
     * T9 filtrování – podporuje cyrilici podle aktivního rozložení (ru / uk / sr).
     * Latinka se převádí přes KeypadHelper, cyrilické znaky přes mapu rozložení.
     */
    private fun filterContactsT9(text: String): List<Contact> {
        val layout = cyrillicLayout
        return allContacts.filter { contact ->
            var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                contact.getNameToDisplay().normalizeString()
            ).filterNot { it.isWhitespace() }

            if (layout != null) {
                convertedName = buildString {
                    convertedName.lowercase(Locale.getDefault()).forEach { char ->
                        val digit = layout.charsMap[char]
                        if (digit != null) append(digit) else append(char)
                    }
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
                telephonyManager?.sendDialerSpecialCode(secretCode)
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
        val v = vibrator
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
                CallHelper.showIMEIDialog(this)
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
