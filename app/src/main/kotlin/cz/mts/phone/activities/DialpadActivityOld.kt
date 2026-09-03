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
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.isDefaultDialer
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.normalizeString
import cz.mts.base.extensions.onTextChangeListener
import cz.mts.base.extensions.performHapticFeedback
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


class DialpadActivityOld : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    private var allContacts = ArrayList<Contact>()
    private var bAllContactsLoaded = false
    private var speedDialValues = ArrayList<SpeedDial>()
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var bLongClick : Boolean = false

    private var hasRussianLocale = false
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


    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        hasRussianLocale = Locale.getDefault().language == "ru"

        binding.apply {
            setupEdgeToEdge(
                padBottomImeAndSystem = listOf(dialpadList, dialpadHolder)
            )
            setupMaterialScrollListener(binding.dialpadList, binding.dialpadAppbar)
        }


        // if (checkAppSideloading()) {
       //     return
       // }

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
                dialpad0Holder,
                dialpad1Holder,
                dialpad2Holder,
                dialpad3Holder,
                dialpad4Holder,
                dialpad5Holder,
                dialpad6Holder,
                dialpad7Holder,
                dialpad8Holder,
                dialpad9Holder,
                dialpadPlusHolder,
                dialpadAsteriskHolder,
                dialpadHashtagHolder
            ).forEach {
                it.background =
                    ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }
        }

        setupOptionsMenu()
        speedDialValues = config.getSpeedDialValues()

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.apply {
            if (hasRussianLocale) {
                dialpad2Letters.append("\nАБВГ")
                dialpad3Letters.append("\nДЕЁЖЗ")
                dialpad4Letters.append("\nИЙКЛ")
                dialpad5Letters.append("\nМНОП")
                dialpad6Letters.append("\nРСТУ")
                dialpad7Letters.append("\nФХЦЧ")
                dialpad8Letters.append("\nШЩЪЫ")
                dialpad9Letters.append("\nЬЭЮЯ")

                val fontSize = resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad2Letters,
                    dialpad3Letters,
                    dialpad4Letters,
                    dialpad5Letters,
                    dialpad6Letters,
                    dialpad7Letters,
                    dialpad8Letters,
                    dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }

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

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput(); true }
            dialpadCallButton.setOnClickListener { initCall(dialpadInput.value) }
            dialpadCallButton.setOnLongClickListener { initCallWithSimSelector() }
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
            dialpadInput.requestFocus()
            dialpadInput.disableKeyboard()
        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContactsX ->
            allContacts = allContactsX
            bAllContactsLoaded = true

            runOnUiThread {
                val sSearch = checkDialIntent()
                if (sSearch.isNullOrEmpty()) dialpadValueChanged("")
                else dialpadValueChanged(sSearch)
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.apply {
            val callIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = properPrimaryColor.getContrastColor()
            )
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(properPrimaryColor)

            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.dialpadHolder)
        binding.dialpadClearChar.applyColorFilter(getProperTextColor())
        setupTopAppBar(binding.dialpadAppbar, NavigationIcon.Arrow)

    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun checkDialIntent(): String? {

        var cleanedNumber = ""
        val numberMTS = intent.getStringExtra("EXTRA_PHONE_NUMBER")
        if (numberMTS.isNullOrEmpty()) {
            if (
                (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
                && intent.data != null && intent.dataString?.contains("tel:") == true
               ) { cleanedNumber = Uri.decode(intent.dataString).substringAfter("tel:") }
        } else {
            cleanedNumber = numberMTS
                .replace("tel:", "")
                .replace(" ", "")
                .replace("%20", "")// mezera
                .replace("%2B", "+") //+
                .replace("%3A", "")// :

            cleanedNumber = normalizeDigitsOnly(cleanedNumber)
        }

        if (cleanedNumber.isNotBlank()) {
          //  if (mtsGlobalAll.iSaveDebugMode == 1) {
          //      Clipboard.copyTextToClipboard(this, "Intent", numberMTS)
          //  }
            binding.dialpadInput.setText(cleanedNumber)
            binding.dialpadInput.setSelection(cleanedNumber.length)
            return cleanedNumber
        }
        return null
    }

    private fun addNumberToContact() {
       // val phoneNumber = getSelectedPhoneNumber() ?: return

        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, binding.dialpadInput.value)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            launchActivityIntent(this)
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


    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent("android.provider.Telephony.SECRET_CODE")
                intent.data = "android_secret_code://$secretCode".toUri()
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered: List<Contact> = if (config.enableT9dialpad) {
            // T9 režim enabled
            allContacts.filter { contact ->
                var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                    contact.getNameToDisplay().normalizeString()
                ).filterNot { it.isWhitespace() }

                if (hasRussianLocale) {
                    var currConvertedName = ""
                    convertedName.lowercase(Locale.getDefault()).forEach { char ->
                        val convertedChar = russianCharsMap.getOrElse(char) { char }
                        currConvertedName += convertedChar
                    }
                    convertedName = currConvertedName
                }

                contact.doesContainPhoneNumber(text) || convertedName.contains(text, true)
            }.sortedWith(compareBy { !it.doesContainPhoneNumber(text) })
        } else {// --- Numerický režim (pouze čísla) ---
            val normalizedInput = text.filter { it.isDigit() }
            allContacts.filter { contact ->
                contact.phoneNumbers.any { phone ->
                    val normalizedPhone = phone.normalizedNumber.filter { it.isDigit() }
                    normalizedPhone.contains(normalizedInput)
                }
            }
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
                val contactTemp = it as Contact
                startContactDetailsIntentID(contactTemp.rawId.toLong(), contactTemp.source)
            }
        ).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
        bLongClick = false
    }

    private fun dialpadValueChangedBEZT9(text: String) {

        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                // Moderní způsob pro předání secret code Androidu
                val intent = Intent("android.provider.Telephony.SECRET_CODE")
                intent.data = "android_secret_code://$secretCode".toUri()
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

    //ponechá jen čísla
    val normalizedInput = text.filter { it.isDigit() }

        val filtered = allContacts.filter { contact ->
            contact.phoneNumbers.any { phone ->
                val number = phone.normalizedNumber
                val normalizedPhone = number.filter { it.isDigit() }
                normalizedPhone.contains(normalizedInput)
            }
        }

        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

    ContactsAdapter(
        activity = this,
        contacts = ArrayList(filtered),
        recyclerView = binding.dialpadList,
        highlightText = text,
        itemClick = {
            mtsGlobalAll.showNumberPickerDialog(this, it as Contact, -2,  onDismiss = { finish() })
            clearInputWithDelay()
        },
        profileIconClick = {
            val contactTemp = it as Contact
            startContactDetailsIntentID(contactTemp.rawId.toLong(), contactTemp.source)
        }
    ).apply {
        binding.dialpadList.adapter = this
    }

    binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
    binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
    bLongClick = false
 }



    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(binding.dialpadInput.value)
        }
    }

    // Vibrátorek
    private fun zavibruj() {
        val v = getSystemService(Vibrator::class.java)
        if (v != null && v.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(
                80,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            v.vibrate(effect)
        }
    }

    private fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        // Vibrátorek
        if (!bLongClick) zavibruj()

        //prázdný text návrat
        if (number.isNullOrEmpty()) return
        // * nebo # předvyplní poslední recent číslo
        else if ( (number.equals("*")) || (number.equals("#")) ) {
//            RecentsHelper(this).getRecentCalls(queryLimit = 1) {
//                val mostRecentNumber = it.firstOrNull()?.phoneNumber
//                if (!mostRecentNumber.isNullOrEmpty()) {
//                    runOnUiThread {
//                        binding.dialpadInput.setText(mostRecentNumber)
//                    }
//                }
//            }
        }
        //kód pro IMEI
        else if (number == "*#06#") {
                mtsGlobalAll.showIMEIDialog(this)
                return
            }
        //něco tam je
        else {
            mtsGlobalAll.mtsSwhoDetailDialer(this, number, bLongClick, 0, onDismiss = { finish() } )
            clearInputWithDelay()
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
            val result = speedDial(char.digitToInt())
            if (result) {
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
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }

}
