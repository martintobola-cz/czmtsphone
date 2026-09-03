package cz.mts.phone.activities

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentManager
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beInvisible
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.beVisibleIf
import cz.mts.base.extensions.getContrastColor
import cz.mts.base.extensions.getFormattedDuration
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.isSystemInDarkMode
import cz.mts.base.extensions.isVisible
import cz.mts.base.extensions.lightenColor
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.dpToPx
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.isOreoMr1Plus
import cz.mts.base.helpers.isOreoPlus
import cz.mts.base.helpers.LOWER_ALPHA
import cz.mts.base.helpers.LOWER_ALPHA_INT
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.base.helpers.PhoneNumberHelper
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.phone.databinding.ActivityCallBinding
import cz.mts.phone.extensions.addCharacter
import cz.mts.phone.extensions.audioManager
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.disableKeyboard
import cz.mts.phone.extensions.getAvailableSIMCardLabels
import cz.mts.phone.extensions.getCallDuration
import cz.mts.phone.extensions.getStateCompat
import cz.mts.phone.extensions.hasCapability
import cz.mts.phone.helpers.CallActivityUI
import cz.mts.phone.helpers.CallManager
import cz.mts.phone.helpers.CallManagerListener
import cz.mts.phone.helpers.CallSwipeHandler
import cz.mts.phone.helpers.getCallContact
import cz.mts.phone.models.AudioRoute
import cz.mts.phone.models.CallContact
import cz.mts.phone.R
import cz.mts.phone.activities.mtsGlobalAll.checkNumberForRating
import cz.mts.phone.activities.mtsGlobalAll.fakeAvatar
import cz.mts.phone.extensions.isDndActive
import cz.mts.phone.extensions.isOutgoing
import cz.mts.phone.extensions.keyguardManager
import cz.mts.phone.extensions.powerManager
import cz.mts.phone.fragments.ConferenceFragment
import cz.mts.phone.helpers.AudioOutputRoutingHelper
import cz.mts.base.helpers.CALLUUID
import cz.mts.phone.activities.mtsGlobalAll.openSpamNumberWeb
import cz.mts.phone.helpers.CacheContacts
import cz.mts.phone.helpers.CallContactAvatarHelper
import cz.mts.phone.helpers.CallFilterResult
import cz.mts.phone.helpers.CallNotificationManagerMTs2
import cz.mts.phone.helpers.getCallFilterInfo
import cz.mts.phone.helpers.SmsQuickReplyOverlay
import kotlin.String
import kotlin.collections.List
import cz.mts.phone.extensions.getKeyEvent
import cz.mts.phone.helpers.SmsHistoryManager

class CallActivity : SimpleActivity(), CallSwipeHandler.Host {

    override var customNavBarLightIcons: Boolean? = null

    private val routeChooser by lazy { AudioOutputRoutingHelper(this) }
    private val callNotificationManager by lazy { CallNotificationManagerMTs2(this) }
    private val binding by viewBinding(ActivityCallBinding::inflate)

    private var answerPulseAnimator: ObjectAnimator? = null

    // ── NOVÉ: swipe handler a SMS overlay ────────────────────────────────────
    private val swipeHandler by lazy { CallSwipeHandler(binding, this) }
    private var smsOverlay: SmsQuickReplyOverlay? = null

    // ── NOVÉ: implementace CallSwipeHandler.Host ──────────────────────────────
    override fun onAcceptCall() = acceptCall()
    override fun onDeclineCall() = endCall()
    override fun getHostProperTextColor(): Int = getProperTextColor()
    override fun getHostDrawable(resId: Int): Drawable? = getDrawable(resId)
    override fun getHostColor(resId: Int): Int = getColor(resId)
    override val isRTLLayout: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    //private var isSpeakerOn = false
    private var isMicrophoneOff = false
    private var isActivityEnded = false
    //private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var screenOnWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var viewsUnderDialpad = arrayListOf<Pair<View, Float>>()
    private var dialpadHeight = 0f
    private var iIntentType : Int = 0
    private var sIntentUUIDcall : String = ""
    private var sMyUUIDcall : String = ""
    private var sMyUUIDcallOverlay : String = "" //konkrétní nalistovaný živý hovor v overlay okně
    private var sMyUUIDcallOverlayList : List<String> = emptyList() //seznam všech živých hovorů pro overlay (mimo hlavní sMyUUIDcall !!)
    private val callContactAvatarHelper = CallContactAvatarHelper(this)
    private var sMyListPosition = 0 //číslo záznamu z sMyUUIDcallOverlayList zobrazené v overlay okně
    private var bIsUpdating = false
    private val sConferenceFragmentTag = "ConferenceFragment"
    private var updating = false
    private var bVibrateIsDone = false


    override fun onCreate(savedInstanceState: Bundle?) {
        addLockScreenFlags()

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        handleIntent(intent)
        setVars()

        CallActivityUI.markVisible()

        setupEdgeToEdge(
            padTopSystem = listOf(binding.callHolder),
            padBottomSystem = listOf(binding.callHolder),
        )

        initUI()

        CallManager.addListener(callCallback)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntent(intent)

        if ((iIntentType == 100) && (!isActivityEnded)) {
            //sIntentUUIDcall je call, na kterém došlo ke změně, ale ten ná nezajímá, nepředáváme ho
            setMyUUIDcallOverlayList("")
        } else {
            setVars()
        }
        if (CallActivityUI.isPaused()) return  //protože se o to postará onResume
        onResumedUiSafe()
    }

    private fun onResumedUiSafe() {
        if (isConferenceOpen()) return
        resetUiState()
        updateState()
        startCallDurationUpdates()
    }


    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        CallActivityUI.markVisible()
        onResumedUiSafe()
    }

    override fun onPause() {
        super.onPause()
        CallActivityUI.markPaused()
        stopCallDurationUpdates()
    }

    override fun onDestroy() {
        CallActivityUI.markDestroyed()
        stopCallDurationUpdates()
        super.onDestroy()
        CallManager.removeListener(callCallback)

        stopAnswerPulse()

        smsOverlay?.dismiss()
        smsOverlay = null

        if (!isDndActive) zavibruj(true) //aby člověk poznal, že mu to někdo položil

        disableProximitySensor()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        try {
            if (screenOnWakeLock?.isHeld == true) {
                screenOnWakeLock?.release()
            }
        } catch (_: Exception) {
        }

    }


    override fun onBackPressedCompat(): Boolean {

        if (smsOverlay != null) {
            dismissSmsOverlay()
            return true
        }

        if (binding.dialpadWrapper.isVisible()) {
            hideDialpad()
            return true
        }

        val fragment = supportFragmentManager.findFragmentByTag(sConferenceFragmentTag)
        if (fragment != null) {
            supportFragmentManager.popBackStack()
            supportFragmentManager.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    supportFragmentManager.removeOnBackStackChangedListener(this)
                    onResumedUiSafe()
                }
            })
            return true
        }
        // Allow minimizing active call - user can return via notification
        return false
    }

    private fun showConferenceFragment() {
        val containerId = R.id.conference_overlay_fragment

        // Pokud už fragment existuje, nic nedělej
        val existing = supportFragmentManager.findFragmentByTag(sConferenceFragmentTag)
        if (existing != null) return

        resetUiState(true)

        val fragment = ConferenceFragment()
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment, sConferenceFragmentTag)
            .addToBackStack(null)  // umožní zpět tlačítko zavřít fragment
            .commit()
    }


    private fun initButtons() = binding.apply {
        // ── ZMĚNA: swipe + click listenery callAccept/callDecline řeší CallSwipeHandler ──
        // V no-swipe režimu nastaví single-click, v swipe režimu double-click.
        // NENASTAVUJ zde callAccept/callDecline click listenery v swipe větvi –
        // přepsaly by double-click logiku v CallSwipeHandler (původní bug #2).
        swipeHandler.init(config.disableSwipeToAnswer)

        if (config.disableSwipeToAnswer) {
            startAnswerPulse()
        }

        callDeclineSms.beGone()
        callDeclineSms.setOnClickListener { showSmsQuickReply() }

        callEnd.setOnClickListener { endCall() }
        callToggleMicrophone.setOnClickListener { toggleMicrophone() }
        callToggleSpeaker.setOnClickListener { changeCallAudioRouteX(false) }
        callDialpad.setOnClickListener { toggleDialpadVisibility() }
        dialpadClose.setOnClickListener { hideDialpad() }
        callToggleHold.setOnClickListener { toggleHold() }

        //callAdd.beGone()
        //callAdd.setOnClickListener {
        // TODO
        //  Intent(applicationContext, DialpadActivity::class.java).apply {
        //      addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        //      startActivity(this)
        //  }
        //}
        callSwap.setOnClickListener { callswap() }
        callMerge.setOnClickListener { callmerge() }
        callManage.setOnClickListener { showConferenceFragment() } // startActivity(Intent(this@CallActivity, ConferenceActivity::class.java))

        // Tooltip při dlouhém podržení
        arrayOf(
            callToggleMicrophone, callDialpad,
            callToggleHold, callSwap, callMerge, callManage, callDeclineSms //callToggleHold, callAdd, callSwap, callMerge, callManage
        ).forEach { imageView ->
            imageView.setOnLongClickListener {
                if (!imageView.contentDescription.isNullOrEmpty()) {
                    toast(imageView.contentDescription.toString())
                }
                true
            }
        }

        callToggleSpeaker.setOnLongClickListener {
            changeCallAudioRouteX(true)
            true
        }


        // Inicializace dialpadu
        dialpadInclude.apply {
            dialpad0Holder.setOnClickListener { dialpadPressed('0') }
            dialpad1Holder.setOnClickListener { dialpadPressed('1') }
            dialpad2Holder.setOnClickListener { dialpadPressed('2') }
            dialpad3Holder.setOnClickListener { dialpadPressed('3') }
            dialpad4Holder.setOnClickListener { dialpadPressed('4') }
            dialpad5Holder.setOnClickListener { dialpadPressed('5') }
            dialpad6Holder.setOnClickListener { dialpadPressed('6') }
            dialpad7Holder.setOnClickListener { dialpadPressed('7') }
            dialpad8Holder.setOnClickListener { dialpadPressed('8') }
            dialpad9Holder.setOnClickListener { dialpadPressed('9') }
            dialpadPlusHolder.setOnClickListener { dialpadPressed('+') }
            dialpadAsteriskHolder.setOnClickListener { dialpadPressed('*') }
            dialpadHashtagHolder.setOnClickListener { dialpadPressed('#') }
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput() }

            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder,
                dialpad4Holder, dialpad5Holder, dialpad6Holder, dialpad7Holder,
                dialpad8Holder, dialpad9Holder, dialpadPlusHolder, dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }

            dialpad0Holder.setOnLongClickListener {
                dialpadPressed('+')
                true
            }
        }

        // Nastavení barev a pozadí
        dialpadWrapper.setBackgroundColor(
            if (isSystemInDarkMode()) {
                getProperBackgroundColor().lightenColor(2)
            } else {
                getProperBackgroundColor()
            }

        )

        arrayOf(dialpadClose, callSimImage, dialpadClearChar).forEach {
            it.applyColorFilter(getProperTextColor())
        }

        val bgColor = getProperBackgroundColor()
        val inactiveColor = getInactiveButtonColor()
        arrayOf(
            callToggleMicrophone, callToggleSpeaker, callDialpad,
            callToggleHold, callSwap, callMerge, callManage //callToggleHold, callAdd, callSwap, callMerge, callManage
        ).forEach {
            it.applyColorFilter(bgColor.getContrastColor())
            it.background.applyColorFilter(inactiveColor)
        }

        callSimId.setTextColor(getProperTextColor().getContrastColor())
        dialpadInput.disableKeyboard()

        dialpadWrapper.onGlobalLayout {
            dialpadHeight = dialpadWrapper.height.toFloat()
        }
    }

    // ── NOVÉ: SMS quick reply overlay ─────────────────────────────────────────

    private fun showSmsQuickReply() {
        if (smsOverlay != null) return  // už zobrazeno

        val overlay = SmsQuickReplyOverlay(this).apply {
            listener = object : SmsQuickReplyOverlay.Listener {
                override fun onDeclineWithSms(message: String) {
                    sendQuickSms(message)
                    endCall()
                    dismissSmsOverlay()
                }
                override fun onDismissed() = dismissSmsOverlay()
            }
        }
        val root = findViewById<ViewGroup>(android.R.id.content)
        overlay.show(root)
        smsOverlay = overlay
    }

    private fun dismissSmsOverlay() {
        smsOverlay?.dismiss()
        smsOverlay = null
    }


    //pozor jistota že simka přes kterou se SMS odešle je stejná jako v tom hovoru platí až od API 31 !!!
    //jinak se bere výchozí ze systému
    //takže možná raději nedovolit ani funkcionalitu použíbvat u menšího api???
    private fun sendQuickSms(message: String) {

        val call = CallManager.getCallById(sMyUUIDcall) ?: return
        val callerNumber = call.details?.handle?.schemeSpecificPart ?: return

        val subscriptionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details.extras?.getInt(
                "android.telecom.extra.SUBSCRIPTION_ID",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            ) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } else {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        try {
            val smsManager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                        ?.createForSubscriptionId(subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            if (mtsGlobalAll.iSaveDebugMode == 1)  toast(message)
            else {
                smsManager?.sendTextMessage(callerNumber, null, message, null, null) //odeslat SMS
            }
            SmsHistoryManager.saveSms(
                context = this,
                phoneNumber = callerNumber,
                message = message
            )

        } catch (e: Exception) {
            toast(e.message.toString())
        }
    }

    // ── Zbytek beze změny ─────────────────────────────────────────────────────

    private fun dialpadPressed(char: Char) {
        CallManager.keypad(char, sMyUUIDcall)
        binding.dialpadInput.addCharacter(char)
    }

    private fun changeCallAudioRouteX(showRouteMenu : Boolean) {
        routeChooser.showChooserOrToggle(CallManager.getCallById(sMyUUIDcall), showRouteMenu)
    }


    private fun updateCallAudioState(route: AudioRoute?) {
        if (route != null) {
            isMicrophoneOff = audioManager.isMicrophoneMute
            updateMicrophoneButton()

            val isSpeakerOn = route == AudioRoute.SPEAKER  //"🔊"
            val isEarpieceOn = route == AudioRoute.EARPIECE //  "👂🏻"
            // val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()
            binding.callToggleSpeaker.apply {
                if (isSpeakerOn) setImageResource(R.drawable.ic_volume_up_vector)
                //else setImageResource(R.drawable.ic_volume_down_vector)
                else if (isEarpieceOn) setImageResource(R.drawable.ic_volume_down_vector)
                else setImageResource(R.drawable.ic_headset_vector)
            }

            toggleButtonColor(binding.callToggleSpeaker, enabled = isSpeakerOn)
            //createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (isSpeakerOn) {
                disableProximitySensor()
            } else {
                enableProximitySensor()
            }
        }
    }

    private fun toggleMicrophone() {
        isMicrophoneOff = !isMicrophoneOff

        // primární cesta – inCallService, pokud máme
        CallManager.inCallService?.setMuted(isMicrophoneOff)

        // fallback – AudioManager
        try {
            audioManager.isMicrophoneMute = isMicrophoneOff
        } catch (_: Exception) {}

        // finální sync (některé ROM to přepnou zpět)
        isMicrophoneOff = audioManager.isMicrophoneMute

        updateMicrophoneButton()
    }


    private fun updateMicrophoneButton() {
        toggleButtonColor(binding.callToggleMicrophone, isMicrophoneOff)
        binding.callToggleMicrophone.contentDescription = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
    }

    private fun toggleDialpadVisibility() {
        if (binding.dialpadWrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun findVisibleViewsUnderDialpad(): Sequence<Pair<View, Float>> {
        return binding.ongoingCallHolder.children
            .filter { it is ImageView && it.isVisible() }
            .map { view -> Pair(view, view.alpha) }
    }

    private fun showDialpad() {
        binding.dialpadWrapper.apply {
            updatePadding(
                bottom = binding.root.bottom - binding.callEnd.top + resources.getDimensionPixelSize(R.dimen.activity_margin)
            )

            translationY = dialpadHeight
            alpha = 0f
            animate()
                .withStartAction { beVisible() }
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(200L)
                .alpha(1f)
                .translationY(0f)
                .start()
        }

        viewsUnderDialpad.clear()
        viewsUnderDialpad.addAll(findVisibleViewsUnderDialpad())
        viewsUnderDialpad.forEach { (view, _) ->
            view.run {
                animate().scaleX(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
                animate().scaleY(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
            }
        }
    }

    private fun hideDialpad() {
        binding.dialpadWrapper.animate()
            .withEndAction { binding.dialpadWrapper.beGone() }
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(200L)
            .alpha(0f)
            .translationY(dialpadHeight)
            .start()

        viewsUnderDialpad.forEach { (view, alpha) ->
            view.run {
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleX(1f).alpha(alpha).duration = 250L
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleY(1f).alpha(alpha).duration = 250L
            }
        }
    }


    private fun holdIconRefresh(call : Call?)
    {
        if (call == null) return
        val isOnHold = call.getStateCompat() == Call.STATE_HOLDING
        toggleButtonColor(binding.callToggleHold, isOnHold)
        binding.callToggleHold.contentDescription = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
        binding.holdStatusLabel.beVisibleIf(isOnHold)
    }

    private fun toggleHold(call : Call? = null) {
        if (call == null) CallManager.toggleHold(CallManager.getCallById(sMyUUIDcall))
        else CallManager.toggleHold(call)
        updateState()
    }


    private fun updateOtherPersonsInfo(callContact : CallContact, avatarUri: String?, isConference : Boolean) {
        binding.apply {
            spamcheck.beGone()
            val (id, name, _, number, numberLabel, presentation, source) = callContact
            callerNameLabel.beVisible()
            callerNameLabel.text = name.ifEmpty { getString(R.string.unknown_caller) }
            val isHiddenNumber = (id.toInt() == 0 && (presentation == 3 || presentation == 2))  //skryté čslo
            // SMS tlačítko: jen pokud je povolené v nastavení a zároveň číslo není skryté
            if (config.swhowDeclineAndSMSbutton && !isHiddenNumber) {
                callDeclineSms.beVisible()
            } else {
                callDeclineSms.beGone()
            }
            if (!isHiddenNumber && number.isNotBlank()) {
                if (numberLabel.isNotEmpty()) {
                    callerNumber.text = "$number - $numberLabel"
                    callerNumber.beVisible()
                } else {
                    callerNumber.text = number
                    if (CacheContacts.bSpamChecking && id.toInt() == 0) { // kontakt nesmí existovat
                        val sSpamEmoji = CallManager.getSpamEmojiByCallId(sMyUUIDcall)
                        if (sSpamEmoji != null)
                        {
                            callerNumber.text = number + " " + sSpamEmoji
                            callerNumber.setOnClickListener { openSpamNumberWeb(this@CallActivity, number ) }
                        }
                        callerNumber.beVisible()
                    } else {
                        if (normalizeDigitsOnly(name) == normalizeDigitsOnly(number)) callerNumber.beGone()
                        else callerNumber.beVisible()
                    }
                }
            } else {
                callerNumber.beGone()
            }

            //pokud je jméno hodně dlouhé, tak zmenšíme font a dáme tučný
            //sice callerNameLabel může být na dva řádky, ale u dlouhého jména původní text  ve velikosti "caller_name_text_size" vypadá hnusně
            if (callerNameLabel.text.length > 16) {
                callerNameLabel.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.call_status_text_size)
                )
                callerNameLabel.typeface = Typeface.defaultFromStyle(Typeface.BOLD)
            }

            callerAvatar.beVisible()
            callerAvatar.apply {
                if (avatarUri.isNullOrEmpty()) {
                    //val bgColor = getProperPrimaryColor()
                    // setBackgroundResource(R.drawable.circle_background)
                    if (isConference) setImageResource(R.drawable.conferenceavatar)
                    else if (mtsGlobalAll.iSaveDebugMode == 2) setImageBitmap(SimpleContactsHelper(this@CallActivity.baseContext).getCircularBitmapFromRID(R.drawable.karlavatar))
                    else if (isHiddenNumber) setImageResource(R.drawable.anonymousavatar) //setImageBitmap(SimpleContactsHelper(this@CallActivity.baseContext).getCircularBitmapFromRID(R.drawable.anonymousavatar))
                    else setImageResource(R.drawable.fakeavatar)
                    setPadding(resources.getDimensionPixelSize(R.dimen.activity_margin))
                    //  applyColorFilter(bgColor.getContrastColor())
                    //  background.applyColorFilter(bgColor)
                } else {
                    if (!isFinishing && !isDestroyed) {
                        Glide.with(this)
                            .load(avatarUri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(this)
                    }
                }
            }
        }
    }


    private fun checkCalledSIMCard(call: Call?) {
        binding.callSimImage.beInvisible()
        binding.callSimId.beInvisible()

        val slot = CallManager.getSimSlotByCall(call)
        if (slot == 0) return

        val simColor = CallManager.getSimColorByCall(call)
        val simIndexId = CallManager.getSimIndexIdByCall(call)
        val index = slot - 1  // zpět na 0-based

        val acceptDrawableId = when (index) {
            0 -> R.drawable.ic_phone_one_vector
            1 -> R.drawable.ic_phone_two_vector
            else -> R.drawable.ic_phone_vector
        }

        val iconRes = if (index == 1) R.drawable.ic_sim2 else R.drawable.ic_sim1
        var useFallback = index !in 0..1

        try {
            if (!useFallback) {
                val drawable = AppCompatResources
                    .getDrawable(binding.callSimImage.context, iconRes)
                    ?.mutate()

                if (drawable != null) {
                    val fixedColor = simColor or 0xFF000000.toInt()
                    drawable.setTintMode(PorterDuff.Mode.SRC_IN)
                    drawable.setTint(fixedColor)
                    binding.callSimImage.setImageDrawable(drawable)
                    binding.callSimImage.applyColorFilter(fixedColor)
                    binding.callSimImage.beVisible()
                } else {
                    useFallback = true
                }
            }
        } catch (_: Exception) {
            useFallback = true
        }

        if (useFallback) {
            binding.callSimId.text = simIndexId.toString()
            binding.callSimId.beVisible()
            binding.callSimImage.beVisible()
            binding.callSimId.setTextColor(getProperBackgroundColor())
            binding.callSimImage.applyColorFilter(simColor)
        }

        try {
            val rippleBg = resources.getDrawable(R.drawable.ic_call_accept, theme) as RippleDrawable
            val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
            layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, getDrawable(acceptDrawableId))
            binding.callAccept.setImageDrawable(rippleBg)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCardOLD(call: Call?) {
        try {
            val simLabels = getAvailableSIMCardLabels()
                .sortedBy { it.indexid}
                .take(2)
            binding.callSimImage.beInvisible()
            binding.callSimId.beInvisible()

            if (simLabels.size <= 1) return

            simLabels.forEachIndexed { index, sim ->
                if (sim.handle != call?.details?.accountHandle) return@forEachIndexed

                val acceptDrawableId = when (index) {
                    0 -> R.drawable.ic_call_accept_simone
                    1 -> R.drawable.ic_call_accept_simtwo
                    else -> R.drawable.ic_call_accept
                }

                val simColor = sim.color
                val iconRes = if (index == 1) R.drawable.ic_sim2 else R.drawable.ic_sim1
                var useFallback = index !in 0..1

                try {
                    if (!useFallback) {
                        val drawable = AppCompatResources
                            .getDrawable(binding.callSimImage.context, iconRes)
                            ?.mutate()

                        if (drawable != null) {
                            val fixedColor = simColor or 0xFF000000.toInt()
                            drawable.setTintMode(PorterDuff.Mode.SRC_IN)
                            drawable.setTint(fixedColor)

                            binding.callSimImage.setImageDrawable(drawable)
                            binding.callSimImage.applyColorFilter(fixedColor)
                            binding.callSimImage.beVisible()
                        } else {
                            useFallback = true
                        }
                    }
                } catch (_: Exception) {
                    useFallback = true
                }

                if (useFallback) {
                    binding.callSimId.text = sim.indexid.toString()
                    binding.callSimId.beVisible()
                    binding.callSimImage.beVisible()
                    binding.callSimId.setTextColor(getProperBackgroundColor())
                    binding.callSimImage.applyColorFilter(simColor)
                }

                binding.callAccept.setImageResource(acceptDrawableId)
            }
        } catch (_: Exception) {
            // zachováno původní chování – tiché potlačení chyby
        }
    }


    private fun updateCallState(call: Call) {
        val state = call.getStateCompat()

        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_SIMULATED_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStartedOrHolded(call)
            Call.STATE_HOLDING -> callStartedOrHolded(call)
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            //TODO       Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        // Nastavení status textu
        val statusTextId = when (state) {
            Call.STATE_SIMULATED_RINGING -> R.string.is_calling
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
            // Call.STATE_HOLDING -> R.string.call_on_hold řeší se na jiném (holdStatusLabel)
            else -> null
        }

        binding.apply {
            callStatusLabel.beVisible()
            if (statusTextId != null)  callStatusLabel.text = getString(statusTextId)
            else  callStatusLabel.text = "" //naskočí tam časomíra


            country.beInvisible()
            // Country label
            if (state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING) {
                val incomingNumber = call.details.handle?.schemeSpecificPart.orEmpty()
                val countryText = PhoneNumberHelper.getCountryWithFlag(incomingNumber)
                country.text = countryText
                country.beVisibleIf(!countryText.isNullOrEmpty())
            }

            // Buttons
            val controlsEnabled =
                !isActivityEnded &&
                    (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING)
            callManage.beVisibleIf(!isActivityEnded && call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
            val isHoldAndActiveCalls = CallManager.hasActiveAndHoldCall()
            setActionButtonEnabled(callSwap, controlsEnabled && isHoldAndActiveCalls)
            setActionButtonEnabled(callMerge, controlsEnabled && isHoldAndActiveCalls)
            setActionButtonEnabled(callToggleHold,controlsEnabled)

            callSwap.beVisibleIf(isHoldAndActiveCalls)
            callMerge.beVisibleIf(isHoldAndActiveCalls)
            holdIconRefresh(call)
        }
    }

    private fun updateState() {
        if (isConferenceOpen()) return
        val callsCount = CallManager.getAliveCallsCount()
        var callMy = CallManager.getCallById(sMyUUIDcall)
        //kontrola na duchy
        val callsAllCount = sMyUUIDcallOverlayList.size + 1  // (+sMyUUIDcall )
        if (callsAllCount != callsCount) {
            sIntentUUIDcall = sMyUUIDcall
            iIntentType = 14
            setVars()
        }

        if (callMy == null) {
            if (callsCount < 1) {
                activityEnded(true)
                return
            } else {
                sIntentUUIDcall = ""
                iIntentType = 0
                setVars()

                callMy = CallManager.getCallById(sMyUUIDcall)
                if (callMy == null) {
                    activityEnded(true)
                    return
                }
            }
        }
        updateCallContactInfo(CallManager.getCallById(sMyUUIDcall))

        val callsOverlayCount = sMyUUIDcallOverlayList.size

        binding.apply {
            controlsSingleCall.beVisible()
            topOverlay.beVisibleIf(callsOverlayCount >= 1)
            callNotification.notificationPageLeft.beInvisible()
            callNotification.notificationPageRight.beInvisible()
            callStatusLabel.beVisible()
        }

        updateCallState(callMy)

        //sedí zobrazená notifikace s hovorem v UI
        //problém je, že CallActivityUI.sUUIDonNotification vyplní notifikace těsně před zobrazením a to už může
        // být UI dávno nahoře a tak zavolá znovupřekreslení notifikace, ale to asi ničemu nevadí...
        if (badIntegrity())  changeNotification(callMy)


        updateCallAudioState(CallManager.getCallAudioRoute())


        if (callsOverlayCount >= 1) { //existuje něco pro overlay ?
            var iPosition = 0
            for (i in sMyUUIDcallOverlayList.indices) {
                iPosition += 1
                sMyUUIDcallOverlay = sMyUUIDcallOverlayList[i]
                val call = CallManager.getCallById(sMyUUIDcallOverlay)
                if (sMyListPosition <= 0) sMyListPosition = 1
                if (sMyListPosition >= callsOverlayCount) sMyListPosition = callsOverlayCount
                if (callsOverlayCount != sMyListPosition) continue //přeskočíme na další
                //záznam vyhovuje tomu co se má zobrazit v overlay
                getCallContact(applicationContext, call) { contact ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val isUnknown = (contact.id.toInt() == 0) && (contact.presentation == 3)
                        binding.apply {
                            var callerName = contact.name.ifEmpty { getString(R.string.unknown_caller) }
                            var callerNumber = contact.number
                            var sSpamEmoji = ""
                            val bSpamEmoji = ((contact.id.toInt() == 0) && (CacheContacts.bSpamChecking))

                            if ((callerNumber.isBlank()) || (normalizeDigitsOnly(callerNumber) == normalizeDigitsOnly(callerName)))
                                callerNumber = ""

                            val isConfenerce = CallManager.isConference(call)
                            if (isConfenerce) {
                                callerNumber = ""
                            }

                            if ((bSpamEmoji) && (!isConfenerce) && (!isUnknown)) {
                                sSpamEmoji = CallManager.getSpamEmojiByCall(call) ?: ""
                            }
                            if ((sSpamEmoji.isNotBlank()) && (!isConfenerce) ) {
                                if  (callerNumber.isBlank()) {callerName += " " + sSpamEmoji}
                                else callerNumber += " " + sSpamEmoji
                            }

                            val callstate = call.getStateCompat()
                            val isNotSupported = callstate == Call.STATE_DISCONNECTING
                                || callstate == Call.STATE_DISCONNECTED
                                || callstate == Call.STATE_AUDIO_PROCESSING
                                || callstate == Call.STATE_NEW
                                || callstate == Call.STATE_PULLING_CALL
                                || callstate == Call.STATE_SELECT_PHONE_ACCOUNT

                            if (isNotSupported) return@runOnUiThread

                            val bCanBeAccepted = callstate == Call.STATE_HOLDING || callstate == Call.STATE_RINGING || callstate == Call.STATE_SIMULATED_RINGING

                            val contentTextId = if (callstate == Call.STATE_RINGING || callstate == Call.STATE_SIMULATED_RINGING) R.string.call_type1_mts
                            else if (callstate == Call.STATE_HOLDING) R.string.call_on_hold
                            else if (callstate == Call.STATE_CONNECTING || callstate == Call.STATE_DIALING) R.string.dialing
                            else if (callstate == Call.STATE_ACTIVE) R.string.mts_active_call
                            else R.string.mts_none

                            callNotification.notificationPageLeft.beVisibleIf(sMyListPosition > 1)
                            callNotification.notificationPageRight.beVisibleIf(sMyListPosition != callsOverlayCount)

                            val dpx = dpToPx(10)
                            callNotification.notificationHolder.setPadding(
                                dpx,
                                dpx,
                                dpx,
                                dpx
                            )

                            //invertovat barvy
                            val colorBackground = withCcAlpha(getProperTextColor())
                            val colorText = config.backgroundColor
                            val colorPrimary = getProperPrimaryColor()

                            val bg = topOverlay.background
                            if (bg is GradientDrawable) bg.setColor(colorBackground)
                            else topOverlay.setBackgroundColor(colorBackground)

                            callNotification.notificationCallerName.setTextColor(colorText)
                            callNotification.notificationPhoneNumber.setTextColor(colorText)
                            callNotification.notificationSimInfo.setTextColor(colorText)
                            callNotification.notificationCallStatus.setTextColor(colorPrimary)

                            callNotification.notificationCallerName.setText(callerName)
                            callNotification.notificationPhoneNumber.setText(callerNumber)

                            if (callerNumber.isBlank()) callNotification.notificationPhoneNumber.beGone()
                            else callNotification.notificationPhoneNumber.beVisible()

                            val iSimSlot = CallManager.getSimSlotByCall(call)
                            val simText = if ((iSimSlot) == 1) "📞¹ "
                                          else if ((iSimSlot) == 2) "📞² "
                                          else ""
                            callNotification.notificationSimInfo.setText(simText)
                            if (iSimSlot == 0) callNotification.notificationSimInfo.beGone()
                            else callNotification.notificationSimInfo.beVisible()

                            callNotification.notificationCallStatus.setText(getString(contentTextId))
                            if (bCanBeAccepted)  {
                                callNotification.notificationAcceptCall.beVisible()
                                callNotification.notificationButtonsGap.beVisible()
                            }
                            else {
                                callNotification.notificationAcceptCall.beGone()
                                callNotification.notificationButtonsGap.beGone()
                            }

                            //pozadí tlačítek, aby to hezky ladilo s textem
                            (callNotification.notificationAcceptCall.background.mutate() as? GradientDrawable)
                                ?.setColor(colorText)
                            (callNotification.notificationDeclineCall.background.mutate() as? GradientDrawable)
                                ?.setColor(colorText)

                            callNotification.notificationAcceptCall.setImageResource(R.drawable.ic_phone_green_vector)
                            callNotification.notificationDeclineCall.setImageResource(R.drawable.ic_phone_down_red_vector)

                            val callContactAvatar = if (CallManager.isConference(call)) fakeAvatar(this@CallActivity, R.drawable.conferenceavatar)
                            else if (mtsGlobalAll.iSaveDebugMode == 2) fakeAvatar(this@CallActivity, R.drawable.karlavatar)
                            else if (isUnknown) fakeAvatar(this@CallActivity, R.drawable.anonymousavatar)
                            else callContactAvatarHelper.getCallContactAvatar(contact, false) ?: fakeAvatar(this@CallActivity, R.drawable.fakeavatar)
                            callNotification.notificationThumbnail.setImageBitmap(SimpleContactsHelper(this@CallActivity.baseContext).getCircularBitmap(callContactAvatar))

                            val openOverlayClick = View.OnClickListener {
                                switchCallOverlay(call)
                            }
                            callNotification.notificationHolder.setOnClickListener(openOverlayClick)
                            callNotification.notificationContentHolder.setOnClickListener(openOverlayClick)

                            callNotification.notificationAcceptCall.setOnClickListener {
                                //test na hold hovor,
                                if (call.getStateCompat() == Call.STATE_HOLDING) CallManager.toggleHold(call)
                                else if (call.getStateCompat() == Call.STATE_RINGING || call.getStateCompat() == Call.STATE_SIMULATED_RINGING) CallManager.accept(call)
                            }
                            callNotification.notificationDeclineCall.setOnClickListener {
                                CallManager.reject(call)
                            }
                            callNotification.notificationPageLeft.setOnClickListener {
                                sMyListPosition -= 1
                                updateState()
                            }
                            callNotification.notificationPageRight.setOnClickListener {
                                sMyListPosition += 1
                                updateState()
                            }
                            //callNotification.notificationCallerName.setOnClickListener {
                            //    switchCallOverlay(call)
                            //}
                            //callNotification.notificationThumbnail.setOnClickListener {
                            //    switchCallOverlay(call)
                            //}

                        }
                    }
                }
            }
        }
    }

    //tady call service nezafunguje, takže musíme patchnout i notifikaci
    private fun switchCallOverlay(call : Call?) {
        val stateNow = call?.getStateCompat() ?: return
        if (!isFinishing && !isDestroyed && stateNow != Call.STATE_DISCONNECTING && stateNow != Call.STATE_DISCONNECTED) {
//        if (call != null && !isFinishing && !isDestroyed && stateNow != Call.STATE_DISCONNECTING && stateNow != Call.STATE_DISCONNECTED) {
            iIntentType = 14
            sIntentUUIDcall = sMyUUIDcallOverlay
            setVars()
            changeNotification(call)
            onResumedUiSafe()
        }
    }

    private fun changeNotification(call : Call) {
        val stateNow = call.getStateCompat()
        if (!isFinishing && !isDestroyed && stateNow != Call.STATE_DISCONNECTING && stateNow != Call.STATE_DISCONNECTED) {
            val canBeAccepted = stateNow == Call.STATE_RINGING || stateNow == Call.STATE_SIMULATED_RINGING
            callNotificationManager.doNotification(call, false, false, false, canBeAccepted)
        }
    }


    private fun checkNumberForSpamOLD(number: String) {
        if (isFinishing || isDestroyed) return
        getCallFilterInfo(applicationContext, number) { result ->
            runOnUiThread {
                if ((!isFinishing) && (!isDestroyed) && (result != null)) {
                    binding.callerNumber.text = checkNumberForRating(result, number, false)
                    if (mtsGlobalAll.iSaveDebugMode != 0) {
                        binding.spamcheck.beVisible()
                        binding.spamcheck.text = result.toString()
                    }
                }
            }
        }
    }

    private fun updateCallContactInfo(call: Call?) {
        getCallContact(applicationContext, call) { contact ->
            val avatar = if (!CallManager.isConference(call)) contact.photoUri else null
            runOnUiThread {
                updateOtherPersonsInfo(contact,avatar, CallManager.isConference(call))
                checkCalledSIMCard(call)
                bIsUpdating = false
            }
        }
    }

    private fun acceptCall() {
        CallManager.accept(sMyUUIDcall)
        stopAnswerPulse()
    }


    private fun initOutgoingCallUI() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
    }

    private fun callRinging() {
        binding.incomingCallHolder.beVisible()

    }

    private fun callswap() {
        CallManager.swap(sMyUUIDcall, sMyUUIDcallOverlay)
    }

    private fun callmerge() {
        CallManager.merge(sMyUUIDcall, sMyUUIDcallOverlay)
    }

    private fun callStartedOrHolded(call: Call) {
        if (call.isOutgoing() && call.getStateCompat() == Call.STATE_ACTIVE) {zavibruj()}
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
    }

    // Vibrátorek
    private fun zavibruj(bFinish : Boolean = false) {

        if (!bFinish) {
            if (bVibrateIsDone) return
        }


        val v = getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return

        val effect = VibrationEffect.createOneShot(
            400,
            VibrationEffect.DEFAULT_AMPLITUDE
        )

        v.vibrate(effect)

        bVibrateIsDone = true
    }

    private fun startCallDurationUpdates() {
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)
    }

    private fun stopCallDurationUpdates() {
        callDurationHandler.removeCallbacks(updateCallDurationTask)
    }


    //TODO  XXXXXXXXX  sIntentUUIDcall ????
//    private fun showPhoneAccountPicker() {
//        if (callContact != null) {
//            getHandleToUse(intent, callContact!!.number) { handle ->
//                CallManager.getCallById(sIntentUUIDcall)?.phoneAccountSelected(handle, false)
//            }
//        }
//    }

    private fun endCall() {
        CallManager.reject(sMyUUIDcall)
        sMyUUIDcall = ""
        sMyUUIDcallOverlay = ""
        sMyUUIDcallOverlayList = emptyList()
        sMyUUIDcall = ""
        stopAnswerPulse()

        if (isActivityEnded)  return

        if (CallManager.getAliveCallsCount() == 0)  //zbytečné shazovat, protože service během okmažiku znovu hodí intent
        {
            activityEnded(false)
            runOnUiThread {
                if (callDuration > 0) {
                    disableAllActionButtons()
                    @SuppressLint("SetTextI18n")
                    binding.callStatusLabel.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                    //Handler(mainLooper).postDelayed(3000) {
                    activityEnded(true)
                    //}
                } else {
                    disableAllActionButtons()
                    binding.callStatusLabel.text = getString(R.string.call_ended)
                    activityEnded(true)
                }
            }
        } else {initUI()}
    }

    private fun activityEnded(bFinish : Boolean = false) {
        CallActivityUI.markDestroyed()
        isActivityEnded = true
        if (bFinish) safeFinishAndRemoveTask()
    }
    private fun safeFinishAndRemoveTask() {
        CallActivityUI.markDestroyed()
        try {
            if (intent != null) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private fun onCallAudioStateChanged(route: AudioRoute?) {
        updateCallAudioState(route)
    }

    private val callCallback = object : CallManagerListener {
        //    override fun onStateChanged() {
        //        updateState()
        //    }

        override fun onCallFilterResult(call : Call, result: CallFilterResult) {
            runOnUiThread { updateState() }
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {
            onCallAudioStateChanged(audioState)
        }

        //     override fun onPrimaryCallChanged(call: Call) {
        //         callDurationHandler.removeCallbacks(updateCallDurationTask)
        //         updateCallContactInfo(call)
        //         //updateState()
        //     }
    }

    private val updateCallDurationTask = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            val call = CallManager.getCallById(sMyUUIDcall) ?: return
            callDuration = call.getCallDuration()
            if (callDuration >= 1) {
                if (badIntegrity()) {
                    if (!updating) {
                        updating = true
                        updateState()
                    }
                }
                binding.callStatusLabel.text = callDuration.getFormattedDuration()
            }
            callDurationHandler.postDelayed(this, 1000)
        }
    }


    @SuppressLint("NewApi")
    private fun addLockScreenFlags()
    {
        // modernní API (O MR1+)
        if (isOreoMr1Plus())
        {
            try {
                // zavolat co nejdříve, ještě před setContentView
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                }
            } catch (ignored: Exception) {
            }
        } else {
            // fallback pro starší verze
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Pokusíme se dismissnout keyguard (pokud je API dostupné)
        try {
            if (isOreoPlus()) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } catch (ignored: Exception) {
        }

        // krátké držení wake locku, aby se obrazovka rozsvítila
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
        }
    }

    private fun enableProximitySensor() {
        if (config.disableProximitySensor) return

        if (proximityWakeLock?.isHeld == true) return

        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            MY_APP_NAME_GOOGLE_ID+":proximity"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun disableProximitySensor() {
        proximityWakeLock?.let { wl ->
            if (wl.isHeld) wl.release()
        }
        proximityWakeLock = null
    }


    private fun disableAllActionButtons() {
        (binding.ongoingCallHolder.children + binding.callEnd)
            .filter { it is ImageView && it.isVisible() }
            .forEach { view ->
                setActionButtonEnabled(button = view as ImageView, enabled = false)
            }
    }

    private fun setActionButtonEnabled(button: ImageView, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun getActiveButtonColor() = getProperPrimaryColor()

    private fun getInactiveButtonColor() = getProperTextColor().adjustAlpha(0.10f)

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {
        if (enabled) {
            val color = getActiveButtonColor()
            view.background.applyColorFilter(color)
            val iconColor = getProperPrimaryColor() //if (isSystemInDarkMode()) color.getContrastColor() else getProperPrimaryColor()
            view.applyColorFilter(iconColor)
        } else {
            view.background.applyColorFilter(getInactiveButtonColor())
            view.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(getKeyEvent(KeyEvent.KEYCODE_DEL))
    }

    private fun clearCharNewBetter(view: View) {
        binding.dialpadInput.text?.let {
            if (it.isNotEmpty()) {
                it.delete(it.length - 1, it.length)
            }
        }
    }

    private fun clearInput(): Boolean {
        binding.dialpadInput.setText("")
        return true
    }

    //zodpovědný za naplnění sIntentUUIDcall a iIntentType
    private fun handleIntent(intent: Intent) {

        //iIntentType = 1 - aktivní hovor v sIntentUUIDcall zobrazit na fullscreenu
        //  = 5 - hovor sIntentUUIDcall, který má jít jako první do overlay, nikoliv zrušit probíhající call !
        //  = 10 - hovor z sIntentUUIDcall je jediný, takže zobrazot na fulscreenu, ostatní žádné nejsou
        //  = 11, 12, 13, ťuknul na notifikaci a chce tedy zobrazit fullscreen, hovor je v sIntentUUIDcall
        //  = 100 - pouze malý refresh, protože některý hovor se stal HOLDING anebo změny v konferenci (PARENT, CHILD)
        //  = 0 - žádný hovor k zobrazení anebo se spustila aktivita bez intentu

        iIntentType = 0
        sIntentUUIDcall = ""

        //výchozí kdyby žádný intent nebyl předán
        val callForUI = CallManager.getCallForUIblind() ?: return
        sIntentUUIDcall = CallManager.getIdByCall(callForUI) ?: ""

        val extras = intent.extras ?: return
        //standardní cesta jediného živého hovoru přes callservice
        iIntentType = if (extras.getBoolean("from_incall_service", false)) 10
        //volání UI přes ťuknutí na notifikaci
        else if (extras.getBoolean("from_doNotification", false)) 13
        //volání UI přes ťuknutí na notifikaci
        else if (extras.getBoolean("from_startNotificationAsync", false)) 11
        //volání UI přes ťuknutí na notifikaci
        else if (extras.getBoolean("from_updateNotificationWithContact", false)) 12
        //nově aktivní hovor (měl by se ihned zobrazit na fullscreenu), vyvolá ho zelené tlačítko na notifikaci
        else if (extras.getBoolean("from_incall_service_high_priority", false)) 1
        //objevil se nový hovor (příchozí či odchozí), ale máme jiné živé hovory
        else if (extras.getBoolean("from_incall_service_new_inout_call", false)) 5
        //jeden nebo více hovorů na stacku po odebrání hovoru a tento, který se předává by se měl zobrazit
        else if (extras.getBoolean("from_incall_service_remove", false)) 1
        //některý hovor se stal HOLDING anebo změny v konferenci (PARENT, CHILD)
        else if (extras.getBoolean("from_incall_service_nochange", false)) 100
        // no_call, žádný hovor k zobrazení anebo se spustila aktivita bez intentu
        else 0

        val sUUIDcall = extras.getString(CALLUUID).orEmpty()
        if (sUUIDcall.isBlank()) return
        //hovor z intentu
        sIntentUUIDcall = sUUIDcall

    }

    private fun initUI() = binding.apply {
        updateTextColors(binding.callHolder)
        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
    }

    private fun resetUiState(conferenceIsOn : Boolean = false) = binding.apply {
        if (conferenceIsOn) conferenceOverlayFragment.beVisible()
        else conferenceOverlayFragment.beGone()
        // Incoming / ongoing
        incomingCallHolder.beGone()
        ongoingCallHolder.beGone()
        // Overlay
        topOverlay.beGone()
        callNotification.notificationPageLeft.beGone()
        callNotification.notificationPageRight.beGone()
        // Hold / multi-call
        onHoldStatusHolder.beGone()
        controlsSingleCall.beGone()
        controlsTwoCalls.beGone()
        // Dialpad
        dialpadWrapper.beGone()
        // Buttons default
        callEnd.beGone()
        callerAvatar.beGone()
        callerNumber.beGone()
        callerNameLabel.beGone()
        callSimImage.beGone()
        callSimId.beGone()
        callStatusLabel.beGone()
        holdStatusLabel.beGone()
        country.beGone()
        spamcheck.beGone()
    }

    private fun setVars() {

        sMyListPosition = 0
        //sMyUUIDcall = ""
        sMyUUIDcallOverlay = ""

        val callMy: Call? = when {
            iIntentType == 0 ->
                CallManager.getCallForUIblind()

            iIntentType != 5 ->
                CallManager.getCallById(sIntentUUIDcall)

            else -> null //intenType 5 - více hovorů
            //           CallManager.getCallById(CallActivityUI.sUUIDonNotification)  //necháme prioritně ten, který je na notifikaci
        }

        if (iIntentType != 5) {
            sMyUUIDcall =
                CallManager.getIdByCall(callMy)
                    ?: CallManager.getIdByCall(CallManager.getCallForUIblind())
                        .orEmpty()
        } //pokud je 5 tak necháváme co v proměnné sMyUUIDcall bylo

        if (sMyUUIDcall.isBlank()) {
            activityEnded(true)
            return
        }

        CallActivityUI.sUUIDonUI = sMyUUIDcall
        CallActivityUI.bUUIDonUIConference = CallManager.isConference(CallManager.getCallById(sMyUUIDcall))
        updating = false
        if (sMyUUIDcall == sIntentUUIDcall) sIntentUUIDcall = ""
        setMyUUIDcallOverlayList(sIntentUUIDcall)

    }


    private fun setMyUUIDcallOverlayList(sIntentData : String) {
        val calls = CallManager.getAliveCallsCount()
        sMyUUIDcallOverlayList =
            if (sIntentData.isEmpty() && calls <= 1) emptyList()
            else CallManager.buildOrderedCallIdList(false, sMyUUIDcall, sIntentData)
    }

    private fun withCcAlpha(color: Int): Int {
        return (color and 0x00FFFFFF) or (0xCC shl 24)
    }

    private fun badIntegrity(): Boolean {
        if (CallActivityUI.sUUIDonUI.isBlank()) return true
        if (CallActivityUI.sUUIDonNotification != CallActivityUI.sUUIDonUI) return true
        if (CallActivityUI.bUUIDonNotificationConference != CallActivityUI.bUUIDonUIConference) return true
        return false
    }

    private fun startAnswerPulse() {
        answerPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.callAccept,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)
        ).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopAnswerPulse() {
        answerPulseAnimator?.cancel()
        answerPulseAnimator = null
        binding.callAccept.scaleX = 1f
        binding.callAccept.scaleY = 1f
    }

    private fun isConferenceOpen(): Boolean =
        supportFragmentManager.findFragmentByTag(sConferenceFragmentTag) != null
}
