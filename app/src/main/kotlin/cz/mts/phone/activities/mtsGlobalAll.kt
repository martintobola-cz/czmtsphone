package cz.mts.phone.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.activities.ManageBlockedNumbersActivity
import cz.mts.base.extensions.adjustAlpha
import cz.mts.base.extensions.adjustColor
import cz.mts.base.extensions.formatDateOrTime
import cz.mts.base.extensions.getBlockedNumbers
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.isDefaultDialer
import cz.mts.base.extensions.isNumberBlocked
import cz.mts.base.extensions.launchActivityIntent
import cz.mts.base.extensions.launchSendSMSIntent
import cz.mts.base.extensions.telecomManager
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.helpers.MY_APP_NAME_GOOGLE_ID
import cz.mts.base.helpers.MySIMcountryISO
import cz.mts.base.helpers.PERMISSION_CALL_PHONE
import cz.mts.base.helpers.PhoneNumberHelper.getCountryWithFlag
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.PhoneNumberHelper.numberForRecents
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.models.PhoneNumber
import cz.mts.phone.R
import cz.mts.phone.extensions.appVersionCode
import cz.mts.phone.extensions.appVersionName
import cz.mts.phone.extensions.areMultipleSIMsAvailable
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.getAvailableSIMCardLabels
import cz.mts.phone.extensions.startContactDetailsIntentID
import cz.mts.phone.extensions.telephonyService
import cz.mts.phone.helpers.AppUpdateNotificationManager
import cz.mts.phone.helpers.CallFilterResult
import cz.mts.phone.helpers.getCallContact
import cz.mts.phone.helpers.getCallFilterInfo
import cz.mts.phone.models.RecentCall
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer


object mtsGlobalAll {
    var bSimAccountsChecked : Boolean = false
    var bShowDialog : Boolean = false
    var bisUnknown : Boolean = false
    var iSaveDebugMode : Int = 0
    var sSaveNumber: String = ""
    var sSaveFormattedNumber: String = ""
    var sSaveName: String = ""
    var iSaveSim : Int = 0
    var iSaveSimIfForced : Int = 0
    var sSaveNumberType : String = "" //mobil, work...
    var sSaveCallType: String = ""  //"příchozí / odchozí ...
    var sSaveDateTime: String = ""
    var sActiveSimSlotPhoneNumber = Array(2) { " " }
    var sActiveSimSlotCountry = Array(2) { " " }
    var iActiveSimSlot = IntArray(2) { -2 }
    var iActiveSimSlotESIM = IntArray(2) { 0 }
    var iActiveSimSlotID = IntArray(2) { -2 }
    var iActiveSimSlotIDreal = IntArray(2) { -123456789 }
    var sActiveSimSlotID = Array(2) { "-99" }
    var sActiveSimSlotOperator = Array(2) { " " }
    var sActiveSimSlotLabel = Array(2) { " " }
    var iActiveSimSlotColor = IntArray(2) { Color.YELLOW }
    var bActiveSimSlotSOS = Array(2) { false }
    var handleActiveSimSlot = arrayOfNulls<PhoneAccountHandle>(2)
    var areMultipleSIMsAvailable : Boolean = false
    var bOnlyOneSimAvailable: Boolean = true
    var bitmapAvatar: Bitmap? = null
    var listPhoneNumber: List<PhoneNumber>? = null

    private val SIM_LABEL_REGEX = """^(.*?)\s*\((.*?)\)\s*$""".toRegex()
    private val DIACRITICS_REMOVAL = "\\p{M}+".toRegex()
    private val NON_LETTER_NUMBER_SPACE = "[^\\p{L}\\p{N}\\s]".toRegex()


    fun resetVars() {
        bShowDialog = false
        bisUnknown = false
        sSaveNumber = ""
        sSaveFormattedNumber = ""
        sSaveName = ""
        iSaveSim = -99
        sSaveNumberType  = "" //mobil, work...
        sSaveCallType = ""  //"příchozí / odchozí ...
        sSaveDateTime = ""
        bitmapAvatar = null
        listPhoneNumber = null
    }


    //vrátí počet SIM v telefonu jako int,
    // a první dva sloty předvyplní do globálních proměnných, aby byly k dispozici stále
    fun numberOfReadySim(activity: BaseSimpleActivity): Int {

        // reset (stejné hodnoty, jen stručně)
        for (i in 0..1) {
            iActiveSimSlotID[i] = -99
            iActiveSimSlotIDreal[i] = -123456789
            sActiveSimSlotID[i] = "-99"
            iActiveSimSlot[i] = -99
            sActiveSimSlotLabel[i] = ""
            sActiveSimSlotOperator[i] = ""
            sActiveSimSlotCountry[i] = ""
            iActiveSimSlotESIM[i] = 0
            sActiveSimSlotPhoneNumber[i] = ""
            iActiveSimSlotColor[i] = activity.getProperTextColor()
            handleActiveSimSlot[i] = null
            bActiveSimSlotSOS[i] = false
        }

        areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()

        var count = 0

        activity.getAvailableSIMCardLabels()
            .sortedBy { it.indexid }
            .take(2)
            .forEachIndexed { index, simAccount ->
            if (index < 2) {
                val match = SIM_LABEL_REGEX.find(simAccount.label)
                sActiveSimSlotOperator[index] = match?.groups?.get(1)?.value ?: simAccount.label
                sActiveSimSlotLabel[index] = match?.groups?.get(2)?.value ?: ""
                iActiveSimSlot[index] = 1
                iActiveSimSlotIDreal[index] = simAccount.subscriptionId
                iActiveSimSlotID[index] = simAccount.indexid   // může nabýt pouze hodnot 1 a 2 !!! viz. getAvailableSIMCardLabels
                sActiveSimSlotID[index] = simAccount.handle.id
                iActiveSimSlotColor[index] = simAccount.color
                handleActiveSimSlot[index] = simAccount.handle
                sActiveSimSlotPhoneNumber[index] = simAccount.phoneNumber
                iActiveSimSlotESIM[index] = simAccount.type //pokud je 1 jde o esimku
                bActiveSimSlotSOS[index] = isEmergencyOnlyLabel(sActiveSimSlotOperator[index])
                sActiveSimSlotCountry[index] = simAccount.countryISO

                //ulož ISO, budeme ho potřebovat pro geolokaci a normalizace čísel
                if (sActiveSimSlotCountry[index].isNotBlank()) {
                    MySIMcountryISO.setISO(index,sActiveSimSlotCountry[index], iActiveSimSlotID[index])
                }

                bSimAccountsChecked = true
            }
            count++
        }

        if (count == 1) bOnlyOneSimAvailable = true
        else bOnlyOneSimAvailable = false

        return count
    }


    //context musí být typu Activity!! jinak crash...
    fun showMyAlertDialog(context: Context, sMytext: String, sMyTitle: String, bShowTitle: Boolean, iButtonText: Int) {

      val sButtontext = when (iButtonText){
           0 -> context.getString(R.string.dialog_ok)
          else -> context.getString(R.string.dialog_close)
      }

      if (bShowTitle) {
          AlertDialog.Builder(context)
              .setTitle(sMyTitle)
              .setMessage(sMytext)
              .setPositiveButton(sButtontext) { dialog, _ -> dialog.dismiss() }
              .show()
      } else {
          AlertDialog.Builder(context)
              .setMessage(sMytext)
              .setPositiveButton(sButtontext) { dialog, _ -> dialog.dismiss() }
              .show()
      }
    }
    fun showIMEIDialog(context: Context) {
      try {
            val telephonyService = context.telephonyService
            val imeiList = mutableListOf<String>()
            val slotCount =  telephonyService.phoneCount
            for (slotIndex in 0 until slotCount) {
                val imei = telephonyService.getImei(slotIndex)
                imei?.let {
                    val text = "SIM ${slotIndex + 1}:  $imei"
                    imeiList.add(text)
                }
            }
             if (imeiList.isEmpty()) {
                 imeiList.add(context.getString(R.string.none_simslot2_mts))
            }
          showMyAlertDialog(context, imeiList.joinToString("\n"), "IMEI", true, 0)
        } catch (e: Exception) {
          showMyAlertDialog(context, e.message.toString(), "IMEI", true, 0)
        }
    }

    //zde pokud v iSimSlot bude 1 nebo 2 jde o funkcionalitu "volej poslední volané číslo z dialeru" a jde o číslo SIM
    //MTSXXXXX
    fun mtsSwhoDetailDialer(activity: BaseSimpleActivity, sDialText: String, bForceDialog: Boolean, iSimLastOutgoingCall: Int, onDismiss: (() -> Unit)? = null) {

        resetVars()

        sSaveNumber = normalizeDigitsOnly(sDialText)
        sSaveFormattedNumber = sDialText
        sSaveName = sDialText

        var fakeRecentCall = RecentCall(
        id = 0,
        phoneNumber = sSaveNumber,
        name = sDialText,
        photoUri = "",
        startTS = 0L,
        duration = 0,
        type = 0,
        simID = iSimLastOutgoingCall,
        simColor = 0,
        specificNumber = "",
        specificType = "",
        isUnknownNumber = false,
        )


        sSaveNumberType = activity.getString(R.string.number_type1_mts)  //mobil, work...
        sSaveCallType = ""  //"příchozí / odchozí ...
        sSaveDateTime = ""

        mtsGlobalAll.bitmapAvatar = fakeAvatar(activity, R.drawable.fakeavatar)

        val iDefault = getDefaultSimSlotForCalls(activity)
        iSaveSim = iDefault  //-1 a 22 odstraní později setHelpTextForCall

        val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
        if ((simSlot == 0) || (simSlot == 1)) {
            iSaveSim = simSlot
            bShowDialog = false
        }

        if (bForceDialog)  bShowDialog = true //long click na dialeru...
        if (iDefault != iSaveSim) bShowDialog = true //vybraný simslot není default pro volání

        if (setHelpTextForCall(activity)) bShowDialog = true  //není-li dostupná žádná sim bude po návratu iSaveSim -1
        //chce-li se vybírat (22) tak v iSaveSim bude první lepší slot anebo zase -1

        getCallContact(activity.applicationContext, null, mtsGlobalAll.sSaveNumber) {contact2 ->
            if (activity.isFinishing || activity.isDestroyed) {
                onDismiss?.invoke()
                return@getCallContact
            }
            if (contact2.id.toInt() != 0) {
                mtsGlobalAll.sSaveName = contact2.name
                mtsGlobalAll.bitmapAvatar = getBitmapFromUri(activity, contact2.photoUri)
                    ?: fakeAvatar(activity, R.drawable.fakeavatar)
                fakeRecentCall = fakeRecentCall.copy(name = contact2.name, photoUri = contact2.photoUri)
            }

            //JDE z funkce "volej poslední odchozí hovor" a nebyl longpress pro force dialogu
            if ((!bForceDialog) &&(iSimLastOutgoingCall > 0)) //větší jak 0 značí, že se JDE z funkce "volej poslední odchozí hovor"
            {
                //naplníme "fake" recentcall a pošleme dál, abychom tady nemuselí řešit/vyhodnocovat simku recentcallu
                //barva sim není potřeba, bere se z uložených jednou načtených hodnot numberOfReadySim()
                //fakeRecentCall = fakeRecentCall.copy(simColor = mtsGlobalAll.iActiveSimSlotColor[(iSimLastOutgoingCall-1)])
                mtsCallRecentCall(activity,fakeRecentCall,-2, false, activity.getString(R.string.number_type6_mts))
                //onDismiss?.invoke()
                return@getCallContact
            }

            //1. vstupní bod - volání přes dialer ručně vyťukáním čísla anebo URI intentem předaným jinou aplikací
            if (bShowDialog) mtsCallShowDialog(activity, true, true, onDismiss = { onDismiss?.invoke() })
            else {
                callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, mtsGlobalAll.iSaveSim)
                onDismiss?.invoke()
            }
        }


    }

    fun mtsSwhoDetailRecentCall(activity: BaseSimpleActivity, recentCall: RecentCall) {
        getRecentCallValues(activity, recentCall)
        mtsCallShowDialog(activity, false, true)
    }

    fun showNumberPickerDialog(activity: BaseSimpleActivity, selContact: cz.mts.base.models.contacts.Contact, bUseSimOne: Boolean) {
        val iSimSlot : Int = when (bUseSimOne) {
            true -> 0
            else -> 1
        }
        showNumberPickerDialog(activity, selContact, iSimSlot)
    }

    //volání recent přes ... menu (sim natvrdo vybrána uživatelem předem)
    fun mtsCallRecentCall(activity: BaseSimpleActivity, recentCall: RecentCall, bUseSimOne: Boolean) {
        val iSimSlot : Int = when (bUseSimOne) {
            true -> 0
            else -> 1
        }
        mtsCallRecentCall(activity, recentCall, iSimSlot, true)
    }

    //iSim = -2  měla by být dostupná pouze jedna SIM přes kterou se bude volat, resp. chceme systémovou sim default pro volání
    //iSim = 0, SIM 1
    //iSim = 1, SIM 2
    fun mtsCallRecentCall(activity: BaseSimpleActivity, recentCall: RecentCall?, iSim: Int, bForce: Boolean, sTypCisla : String = "") {

        if (recentCall == null) return

        //skrytému číslu nelze volat zpět
        if (recentCall.isUnknownNumber) return

        resetVars()

        //naplň proměnné
        getRecentCallValues(activity, recentCall)
        var iSimFromRecent = mtsGlobalAll.iSaveSim

        val iDefault = getDefaultSimSlotForCalls(activity)
        //sim přes kterou recent hovor probíhal tak už neexistuje
        if (iSimFromRecent < 0) iSimFromRecent = iDefault

        val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
        val bForceSim = (iSim in 0..1)
        val bCustomSim = (simSlot in 0..1)
        mtsGlobalAll.iSaveSim = when {
            bForceSim -> iSim
            bCustomSim -> simSlot
            else -> iSimFromRecent // beze změny
        }

        bShowDialog =
            (iDefault !in 0..1) ||
                (
                    (bForceSim && bCustomSim && setOf(iSaveSim, iSimFromRecent, iDefault, simSlot, iSim).size != 1) ||
                        (bForceSim && !bCustomSim && setOf(iSaveSim, iSimFromRecent, iDefault, iSim).size != 1) ||
                        (!bForceSim && bCustomSim && setOf(iSaveSim, iSimFromRecent, iDefault, simSlot).size != 1) ||
                        (!bForceSim && !bCustomSim && setOf(iSaveSim, iSimFromRecent, iDefault).size != 1)
                    )

        if (setHelpTextForCall(activity)) bShowDialog = true  //není-li dostupná žádná sim bude po návratu iSaveSim -1
                                                      //chce-li se vybírat (22) tak v iSaveSim bude první lepší slot anebo zase -1

        if (sTypCisla.isNotBlank()) {
            sSaveNumberType = sTypCisla
        }

        //2. bod volání z recents
        if ((activity.config.showCallConfirmation) || (bShowDialog)) mtsCallShowDialog(activity, true, true)
        else callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, mtsGlobalAll.iSaveSim)
    }

    fun showNumberPickerDialog(
        activity: BaseSimpleActivity?,
        contact: cz.mts.base.models.contacts.Contact,
        iSim : Int,
        onDismiss: (() -> Unit)? = null) {

        if (activity == null) {
            onDismiss?.invoke()
            return
        }

        resetVars()

        val iDefault = getDefaultSimSlotForCalls(activity)

        listPhoneNumber = contact.phoneNumbers
            .sortedWith(compareByDescending<PhoneNumber> { it.isPrimary }
                .thenBy { it.type ?: "" })

        //jméno je u kontaktů o řádek v mtsGlobalAll.sSaveNumber jen proto aby se to dalo v dialogu níže a nebyla tam mezera...
        mtsGlobalAll.sSaveName = contact.getNameToDisplay()
        mtsGlobalAll.sSaveNumberType = " " //mobil, work...
        mtsGlobalAll.sSaveNumber = " "
        mtsGlobalAll.sSaveFormattedNumber= " "
        mtsGlobalAll.sSaveNumberType = " "

        mtsGlobalAll.bitmapAvatar = getBitmapFromUri(activity, contact.photoUri)
            ?: fakeAvatar(activity, R.drawable.fakeavatar)


        val count = listPhoneNumber?.size ?: 0
        //pokud je v seznamu jen jedno číslo tak ho ulož do global proměnných
        mtsGlobalAll.sSaveNumber = listPhoneNumber?.takeIf { it.size == 1 }
            ?.firstOrNull()
            ?.value //normalizedNumber
            ?: ""

        when (iDefault) {
            0 -> mtsGlobalAll.iSaveSim = 0
            1 -> mtsGlobalAll.iSaveSim = 1
            else ->   mtsGlobalAll.bShowDialog = true  //iDefault = -1 nebo 22
        }

        val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
        val bForceSim = (iSim in 0..1)
        val bCustomSim = (simSlot in 0..1)
        iSaveSim = when {
            bForceSim -> iSim
            bCustomSim -> simSlot
            else -> iSaveSim // beze změny
        }

        if (!bShowDialog) {
            bShowDialog =
                (iDefault !in 0..1) ||
                    (
                        (bForceSim && bCustomSim && setOf(iSaveSim, iDefault, simSlot, iSim).size != 1) ||
                            (bForceSim && !bCustomSim && setOf(iSaveSim, iDefault, iSim).size != 1) ||
                            (!bForceSim && bCustomSim && setOf(iSaveSim, iDefault, simSlot).size != 1) ||
                            (!bForceSim && !bCustomSim && setOf(iSaveSim, iDefault).size != 1)
                        )
        }

        if (setHelpTextForCall(activity)) bShowDialog = true
        //zajímá nás pouze forced
        if (bForceSim) mtsGlobalAll.iSaveSimIfForced = mtsGlobalAll.iSaveSim
        //ostatní nás nezajímá proto podvrhneme hloupost
        else mtsGlobalAll.iSaveSimIfForced = 22

        //3. bod z kontaktů
        if ((activity.config.showCallConfirmation) || (bShowDialog) || (count > 1))  mtsCallShowDialog(activity, true, false, onDismiss = { onDismiss?.invoke() })
        else {
            callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, mtsGlobalAll.iSaveSim)
            onDismiss?.invoke()
        }
    }

  fun mtsCallShowDialog(
      activity: BaseSimpleActivity,
      bDoCall: Boolean,  //false = zobrazení detailu hovoru
      bDialogType: Boolean, //true jde o volání z recent hovorů; false jdeme z kontaktů
      viewX: View? = null,
      recent : RecentCall? = null,
      onDismiss: (() -> Unit)? = null) {

        val iAvailableSimCount = numberOfReadySim(activity) //aby se vyplnily globální proměnné
        val bShakeOn = activity.baseContext.config.shakeEffectConfirmingCall
        val bEvasControlMode = (iAvailableSimCount > 1) && (!bShakeOn)
        val formatPhoneNumbers = activity.config.formatPhoneNumbers
        val iProperBackgroundColor = activity.getProperBackgroundColor()
        val iProperPrimaryColor = activity.getProperPrimaryColor()
        val iProperTextColor = activity.getProperTextColor()
        val iProperTextColorSecondary = iProperTextColor.adjustAlpha(0.4f)

        try {(viewX?.parent as? ViewGroup)?.removeView(viewX) //pokud je viewX už někde připojený tak se odpojí dřív, než ho předáme dialogu
        } catch (_: Exception) { }

        val view = if (viewX == null) LayoutInflater.from(activity).inflate(R.layout.dialog_do_call, null)
                   else viewX
        val spamrating = view.findViewById<TextView>(R.id.spamRate)
        val country = view.findViewById<TextView>(R.id.country)
            country.setTextColor(iProperTextColor)
        val photo = view.findViewById<ImageView>(R.id.photo)
        val phoneNumber = view.findViewById<TextView>(R.id.phoneNumber)
            phoneNumber.setTextColor(iProperTextColor)
        val specificType = view.findViewById<TextView>(R.id.specificType)
            specificType.setTextColor(iProperPrimaryColor)
        val name = view.findViewById<TextView>(R.id.name)
            name.setTextColor(iProperTextColor)
        val simInfo = view.findViewById<TextView>(R.id.sim1Info)
            simInfo.setTextColor(iProperTextColor)
        val sim2Info = view.findViewById<TextView>(R.id.sim2Info)
            sim2Info.setTextColor(iProperTextColor)
        val simIcon = view.findViewById<ImageView>(R.id.sim1Icon)
        val simIconTwo = view.findViewById<ImageView>(R.id.sim2Icon)
        val simIconCustom = view.findViewById<ImageView>(R.id.simIconCustom)
        val pinSaveSim = view.findViewById<TextView>(R.id.pinIcon)
            pinSaveSim.setTextColor(iProperTextColor)
        val spacerBeforeSim1 = view.findViewById<View>(R.id.spacerBeforeSim1)
        simIcon.visibility = View.VISIBLE
        specificType.visibility = View.VISIBLE
        spamrating.visibility = View.GONE

      val iAccentColor = iProperTextColor

        var bOnlyOne = false

        // u kontaktů musíme vyplnit správné čísla pro výběr pokud jich je více
        if (!bDialogType) {
            mtsGlobalAll.sSaveFormattedNumber = listPhoneNumber
                    ?.filterNotNull() // vyhodí null položky
                    ?.joinToString(separator = "\n") { phone ->
                        val number = phone.value ?: "" //normalizedNumber ?: ""
                        val formatted = numberForRecents(number, formatPhoneNumbers)
                        val label = getNumberTypeLabel2(activity, phone.type)
                        "$formatted ($label)"
                    }
                    ?: ""

            mtsGlobalAll.sSaveNumberType = ""

            val phoneListContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val phoneList = listPhoneNumber
                ?.filterNotNull()
                ?.map {
                    val number = it.value
                    val formatted = numberForRecents(number,  formatPhoneNumbers) ?: ""
                    val label = getNumberTypeLabel2(activity, it.type)
                    formatted to label
                } ?: emptyList()

            // Ulož formátovaná čísla pro pozdější použití
            mtsGlobalAll.sSaveFormattedNumber = phoneList.joinToString("\n") { (formatted, label) ->
                "$formatted ($label)"
            }

            // Nastav výchozí hodnoty
            if (phoneList.isNotEmpty()) {
                mtsGlobalAll.sSaveNumber = phoneList[0].first
                mtsGlobalAll.sSaveNumberType = phoneList[0].second
            } else {
                mtsGlobalAll.sSaveNumber = ""
                mtsGlobalAll.sSaveNumberType = ""
            }

            // vytvoř TextView pro každé číslo
            val showEmoji = phoneList.size > 1

            phoneList.forEachIndexed { index, (formatted, label) ->
                val numberView = TextView(activity).apply {
                    text = makePhoneText(formatted, index == 0, iAccentColor, showEmoji)
                    textSize = 20f
                    setPadding(0, 12, 0, 12)
                    gravity = Gravity.START
                    setTextColor(iAccentColor)
                    setTypeface(null, if (index == 0) Typeface.BOLD else Typeface.NORMAL)
                }

                numberView.setOnClickListener {
                    // reset všech ostatních
                    for (i in 0 until phoneListContainer.childCount) {
                        val child = phoneListContainer.getChildAt(i) as TextView
                        val (childFormatted, _) = phoneList[i]
                        child.text = makePhoneText(childFormatted, false, iAccentColor, showEmoji)
                        child.setTypeface(null, Typeface.NORMAL)
                    }

                    // zvýrazni vybraný
                    numberView.text = makePhoneText(formatted, true, iAccentColor, showEmoji)
                    numberView.setTypeface(null, Typeface.BOLD)

                    // ulož výběr
                    mtsGlobalAll.sSaveNumber = formatted
                    mtsGlobalAll.sSaveNumberType = label
                    specificType.text = label

                    //musí se překreslit i custom SIMky !!!!!!!!
                    pinSaveSim.text = "📌 "
                    val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
                    val bForceSim = (iSaveSimIfForced in 0..1)
                    val bCustomSim = (simSlot in 0..1)

                    iSaveSim = when {
                        bForceSim -> iSaveSimIfForced
                        bCustomSim -> simSlot
                        else -> getDefaultSimSlotForCalls(activity) //může vrátit 22 i -1
                    }

                    if (bCustomSim) {
                        drawSim(simIconCustom, null,  simSlot, iProperTextColor, false)
                        if (areMultipleSIMsAvailable) simIconCustom.visibility = View.VISIBLE
                    } else simIconCustom.visibility = View.GONE

                    setHelpTextForCall(activity)
                    //if (iSaveSim == 22) iSaveSim = iActiveSimSlot.indexOf(1) //to udělá i setHelpTextForCall

                    if (bEvasControlMode) {
                        //pouze změnit font podle iSaveSim čím zvýrazníme default sim, přes kterou by se mělo volat
                        if (iSaveSim <= 0) {
                            simInfo.setTextColor(iProperTextColor)
                            sim2Info.setTextColor(iProperTextColorSecondary)
                        } else {
                            simInfo.setTextColor(iProperTextColorSecondary)
                            sim2Info.setTextColor(iProperTextColor)
                        }
                    } else {
                        drawSim(simIcon, simInfo, mtsGlobalAll.iSaveSim, iProperTextColor, true)
                    }
                }

                phoneListContainer.post {
                    if (!bOnlyOne) {
                        val firstChild = phoneListContainer.getChildAt(0)
                        firstChild?.performClick()
                        bOnlyOne = true
                    }
                }

                phoneListContainer.addView(numberView)
            }

            // nahradíme původní TextView kontejnerem
            val parent = phoneNumber.parent as ViewGroup
            val index = parent.indexOfChild(phoneNumber)
            parent.removeView(phoneNumber)
            parent.addView(phoneListContainer, index)
        } else { //jdeme zde z recents
            phoneNumber.text = mtsGlobalAll.sSaveFormattedNumber
            phoneNumber.setTypeface(null, Typeface.BOLD)
        }


        name.text = mtsGlobalAll.sSaveName
        //10 kliknutí zapne DEBUG mód, ve kterém se nebude volat, ale ukazovat jen dialog s infem
        var clickCount = 0
        var lastClickTime = 0L
        name.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            // Pokud mezi kliknutími uplyne víc než 1,5 sekundy → resetuj čítač
            if (currentTime - lastClickTime > 1500) {
                clickCount = 0
            }
            clickCount++
            lastClickTime = currentTime
            if (clickCount >= 10) {
                clickCount = 0 // reset po splnění
                if (iSaveDebugMode != 0) iSaveDebugMode = 0
                else iSaveDebugMode = 1
                if (mtsGlobalAll.sSaveName.lowercase().indexOf("karel") > -1) {
                    activity.config.apply {
                        themeIdSaved = 9
                        textColor = Color.parseColor("#000000")
                        backgroundColor = Color.parseColor("#FED900")
                        primaryColor = Color.parseColor("#106D1F")
                        accentColor = Color.parseColor("#106D1F")
                        navBarColor = Color.parseColor("#106D1F")
                    }
                    iSaveDebugMode = 2
                }
                Toast.makeText(activity, "Debug mode " + iSaveDebugMode.toString(), Toast.LENGTH_SHORT).show()
                if (iSaveDebugMode == 2) activity.finish()
            }
        }


        specificType.text = mtsGlobalAll.sSaveNumberType
        sim2Info.setTypeface(null, Typeface.NORMAL)
        if (bDoCall) {
            country.text = ""
            country.visibility = View.GONE

            if (bEvasControlMode) {  //nechceme animaci a máme více símek
                simIconTwo.visibility = View.VISIBLE
                spacerBeforeSim1.visibility = View.VISIBLE
                val areOperatorSame = sActiveSimSlotOperator[0] == sActiveSimSlotOperator[1]
                setHelpTextForCall(activity, 0, areOperatorSame)
                drawSim(simIcon, simInfo,  0, iProperTextColor, true)
                setHelpTextForCall(activity, 1, areOperatorSame)
                drawSim(simIconTwo, sim2Info,  1, iProperTextColor, true)
                if (iSaveSim <= 0) {
                    simInfo.setTextColor(iProperTextColor)
                    sim2Info.setTextColor(iProperTextColorSecondary)
                } else {
                    simInfo.setTextColor(iProperTextColorSecondary)
                    sim2Info.setTextColor(iProperTextColor)
                }
            } else {
                drawSim(simIcon, simInfo,  mtsGlobalAll.iSaveSim,iProperTextColor, true)
                simIconTwo.visibility = View.GONE
                sim2Info.text = if (bShakeOn) "\n   " + activity.getString(R.string.call_now_mts_a) + "   \n   " + activity.getString(R.string.call_now_mts_b) + "   \n   \u260E\n     "
                                else activity.getString(R.string.call_now_mts_a) + " " + activity.getString(R.string.call_now_mts_b) + " \u260E "
                sim2Info.setTypeface(null, Typeface.BOLD)
                sim2Info.setTextColor(specificType.textColors)
                if (bShakeOn) sim2Info.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.shake_pulse_animation))
            }
        } else {
            simIcon.visibility = View.GONE
            simIconTwo.visibility = View.GONE
            sim2Info.text = ""
            country.visibility = View.VISIBLE
            country.text = getCountryWithFlag(mtsGlobalAll.sSaveNumber)
            drawSim(simIcon, simInfo,  mtsGlobalAll.iSaveSim,iProperTextColor, false)

            //neuložené číslo, nevyskytující se v kontaktech
            if (mtsGlobalAll.sSaveNumberType == activity.getString(R.string.number_type2_mts)) {
                getCallFilterInfo(activity.applicationContext, normalizeDigitsOnly(mtsGlobalAll.sSaveNumber)) { result ->
                    if (result != null) {
                        val sSpamPin = checkNumberForRating(result, mtsGlobalAll.sSaveNumber, true)
                        if (spamrating.isAttachedToWindow) {
                            spamrating.text = sSpamPin
                            spamrating.visibility = View.VISIBLE
                            spamrating.setOnClickListener {
                                openSpamNumberWeb(activity, mtsGlobalAll.sSaveNumber)
                            }
                        }
                    }
                }
            }
        }


        if (!sSaveFormattedNumber.equals(sSaveName)) name.setTypeface(null, Typeface.BOLD)
        else name.setTypeface(null, Typeface.NORMAL)

        val titleView = TextView(activity).apply {
            text = " "
            setTextColor(iAccentColor)
        //    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.LEFT
        //    setPadding(0, 40, 0, 20)  // uprav padding, aby byl symetrický
            //setTypeface(null, Typeface.BOLD)
            //setPadding(52, 49, 0, 20)
        }

        val dialog: AlertDialog = if (bDoCall) {
            AlertDialog.Builder(activity)
                 //   .setCustomTitle(titleView) //title nepoužíváme dělal nám jen mezeru
                    .setView(view)
                    .setPositiveButton(R.string.dialog_back) { dialog, _ -> resetVars() }
                    .setNeutralButton(R.string.dialog_sms) { dialog, _ -> sendSMS(activity, mtsGlobalAll.sSaveNumber) }
                    .create()
            } else {
                AlertDialog.Builder(activity)
                //  .setCustomTitle(titleView)
                    .setView(view)
                   // .setPositiveButton(R.string.dialog_ok) { dialog, _ -> resetVars() }
                    .setPositiveButton(R.string.dialog_close) { dialog, _ -> resetVars() }
                    .setNeutralButton(R.string.dialog_sms) { dialog, _ -> sendSMS(activity, mtsGlobalAll.sSaveNumber) }
                    .setNegativeButton(R.string.dialog_call) {dialog, _ -> mtsCallRecentCall(activity, recent, -2, false)}
                    .create()
        }

            val roundedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f                       // ← poloměr rohů (v pixelech)
            setColor(iProperBackgroundColor.adjustColor(8, true))    // ← barva pozadí
        }

        // Nastavení pozadí dialogu
        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        positiveButton?.setTextColor(activity.getProperPrimaryColor())
        negativeButton?.setTextColor(activity.getProperPrimaryColor())
        neutralButton?.setTextColor(activity.getProperPrimaryColor())
        positiveButton?.setTypeface(positiveButton.typeface, Typeface.BOLD)
        negativeButton?.setTypeface(negativeButton.typeface, Typeface.BOLD)
        neutralButton?.setTypeface(neutralButton.typeface, Typeface.BOLD)
        positiveButton?.isAllCaps = false
        negativeButton?.isAllCaps = false
        neutralButton?.isAllCaps = false

        if (bisUnknown) {
            neutralButton?.visibility = View.GONE  // SMS
            negativeButton?.visibility = View.GONE // Call
        }

        //předání infa
        var dismissed = false
        dialog.setOnDismissListener {
           if (dismissed) return@setOnDismissListener
           dismissed = true
           onDismiss?.invoke()
        }

        if (areMultipleSIMsAvailable) {
            pinSaveSim.visibility = View.VISIBLE
            pinSaveSim.text = "📌 "
            val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
            if ((simSlot == 0) || (simSlot == 1)) {
                drawSim(simIconCustom, null,  simSlot, iProperTextColor, false)
                simIconCustom.visibility = View.VISIBLE
            } else {
                simIconCustom.visibility = View.GONE
            }

            pinSaveSim.setOnClickListener {
                val simSlot = checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
                //zatím není nic přiřazeno
                if (simSlot == -4) {
                    if ((iAvailableSimCount > 0) && ((iSaveSim == 0) || (iSaveSim == 1))) {
                        saveCustomSimMts(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.iSaveSim)
                        simIconCustom.visibility = View.VISIBLE
                    }
                } else {
                    removeCustomSimMts(activity, mtsGlobalAll.sSaveNumber)
                    simIconCustom.visibility = View.GONE
                }
                drawSim(simIconCustom, null,  mtsGlobalAll.iSaveSim, iProperTextColor, false)
            }
        } //if (areMultipleSIMsAvailable)
        else pinSaveSim.visibility = View.GONE

        //pokud nezobrazujeme detail hovoru ale chceme volat (je jedno jestli z recent nebo kontaktů)
        //pak umožníme přepínat SIMku, ale jen tehdy jsou-li dostupné
        simInfo.setOnClickListener {
            if (bEvasControlMode) {
               // klik znamená volání z SIM1 a né přepínání SIMek
                dialog.dismiss()
                callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, 0)
            }
            else if ((bDoCall) && (iActiveSimSlot[0] == 1) && (iActiveSimSlot[1] == 1)) {
                    if (mtsGlobalAll.iSaveSim == 0) mtsGlobalAll.iSaveSim = 1
                    else mtsGlobalAll.iSaveSim = 0
                    setHelpTextForCall(activity)
                    drawSim(simIcon, simInfo,  mtsGlobalAll.iSaveSim, iProperTextColor, true)
            }
        }

        simIcon.setOnClickListener {
            if (bEvasControlMode) {
                //pokud je nějaká SIM uložena tak ji odstraníme bez toastu a pinSaveSim.performClick() se postará o uložení a překreslení nové
                if (checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber) != -4)
                    removeCustomSimMts(activity, mtsGlobalAll.sSaveNumber, false)
                iSaveSim = 0
                pinSaveSim.performClick()
            }
            else simInfo.performClick()
        }

        sim2Info.setOnClickListener {
            if (bEvasControlMode) {
                // klik znamená volání z druhé simky v bEvasControlMode
                dialog.dismiss()
                callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, 1)
            } else {
                dialog.dismiss()
                //pokud není žádná aktivní SIM tak pouze zde je v iSaveSim -1
                callNumber(activity, mtsGlobalAll.sSaveNumber, mtsGlobalAll.sSaveName, mtsGlobalAll.iSaveSim)
            }
        }

        simIconTwo.setOnClickListener {
            if (bEvasControlMode) {
                //pokud je nějaká SIM uložena tak ji odstraníme bez toastu a pinSaveSim.performClick() se postará o uložení a překreslení nové
                if (checkCustomSimMts(activity, mtsGlobalAll.sSaveNumber) != -4)
                    removeCustomSimMts(activity, mtsGlobalAll.sSaveNumber, false)
                iSaveSim = 1
                pinSaveSim.performClick()
            }
            else sim2Info.performClick()
        }

        if (bisUnknown) {
            name.setTextColor(iProperPrimaryColor)
            simIconCustom.visibility = View.GONE
            country.visibility = View.GONE
            pinSaveSim.visibility = View.GONE
            specificType.visibility = View.GONE
            mtsGlobalAll.bitmapAvatar = fakeAvatar(activity, R.drawable.anonymousavatar)
            mtsGlobalAll.sSaveNumber = ""
        }

        //obrázek a akce na něm
        mtsGlobalAll.bitmapAvatar?.let { avatar -> photo.setImageBitmap(SimpleContactsHelper(activity.baseContext).getCircularBitmap(avatar)) }
        photo.setOnClickListener {
            getCallContact(activity.applicationContext, null, normalizeDigitsOnly(mtsGlobalAll.sSaveNumber)) { contact ->
                if (contact.id.toInt() != 0) {
                    dialog.dismiss()
                    resetVars()
                    if (activity.isFinishing || activity.isDestroyed) resetVars()
                    else activity.startContactDetailsIntentID(contact.id, contact.source)
                }
            }
        }
  }



    public fun fakeAvatar(context: Context, drawableResId: Int): Bitmap {
        // Načtení drawable jako bitmapa
        val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // Pokusí se zjistit typ čísla z kontaktů (mobil, práce, domů...)
    private fun getNumberTypeLabel(context: Context, number: String): String {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.TYPE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val type = cursor.getInt(0)
                    return ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources, type, ""
                    ).toString()
                }
            }
        } catch (_: Exception) { }
        return ""
    }

    fun getNumberTypeLabel2(context: Context?, type: Int?): String {
        val ctx = context ?: return " "
        val sRet = ctx.getString(R.string.other)
        if (type == null) return sRet

        return context.getPhoneNumberTypeText(type, sRet).lowercase()
    }



    fun getBitmapFromUri(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null

        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }


//    fun getContactFromPhoneNumberNotBlocking(context: Context, phoneNumber: String): Contact? {

 //       if (phoneNumber.isBlank()) return null
 //       if (phoneNumber.equals(" ")) return null

 //       return CacheContacts
 //           .findContactByPhoneNumber(context, phoneNumber)
 //           ?.toMinimalContact()

      //  val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
      //  val projection = arrayOf(
      //      ContactsContract.PhoneLookup._ID,
      //      ContactsContract.PhoneLookup.DISPLAY_NAME,
      //      ContactsContract.PhoneLookup.PHOTO_URI
      //  )
      //  var contact: Contact? = null
      //  activity.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
      //      if (cursor.moveToFirst()) {
      //          val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
      //          val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
      //          val photoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI))
      //          contact = Contact(id, name, photoUri ?: "")
      //      }
      //  }
      //  return contact
   // }
    data class Contact(
        val id: Long,
        val name: String,
        val photoUri: String?
    )


    private fun sendSMS(activity: BaseSimpleActivity, sNumber: String) {
        resetVars()
        if (sNumber.isNotBlank()) activity.launchSendSMSIntent( normalizeDigitsOnly(sNumber))
    }

        private fun callNumber(activity: BaseSimpleActivity, sNumber: String, name: String, iSimSlot: Int) {

        resetVars()

        if (sNumber == "*#06#") {
            showIMEIDialog(activity)
            return
        }

        val number = sNumber  //MTSXXX TODO normalizovat, zkontrolovat?????

        if (number.isEmpty()) {
            val sBody = activity.getString(R.string.call_error1_number_mts) + " " + activity.getString(R.string.call_empty_string_mts)
            showMyAlertDialog(activity, sBody, "Error", true, 0)
            return
        }

        if (iSimSlot > 1) {
            showMyAlertDialog(activity, "index_call_ID > 1 ", "SIM error", true, 0)
            return
        }

      //  if (iSimSlot < 0)
      //  {
      //      showMyAlertDialog(activity, "Sim slot < 0 ", "SIM error", true, 0)
      //      return
      //  }

        if (activity.isNumberBlocked(number, activity.getBlockedNumbers())) {
            val sTitle = activity.getString(R.string.call_error1_title_mts)
            val sBody = activity.getString(R.string.call_error1_text_mts)
            showMyAlertDialog(activity, "\n" + activity.getString(R.string.call_error1_number_mts) + " " + numberForRecents(number,  activity.config.formatPhoneNumbers) + " " +  sBody, sTitle, true, 0)
            return
        }

      //  && (isAnySimReady(activity, false)
      if ( (numberOfReadySim(activity) > 0) && (iSimSlot >= 0) ) {

        val handleForCall = handleActiveSimSlot[iSimSlot]
        //požadovaný simslot a jeho handle je dostupný
        if ((iActiveSimSlot[iSimSlot] == 1) && (handleForCall != null)) {

       //     val useSimOne: Boolean = when (iSimSlot) {
       //         0 -> true
       //         else -> false
       //     }

            var sHelpText = "( " + iSimSlot.toString() +  " index_call_ID )"

            if (iActiveSimSlot[0] == 1) {
                sHelpText += "\n\nindex_call_ID: 0\nhandle_id = " + sActiveSimSlotID[0] +
                    "\nidreal: " + iActiveSimSlotIDreal[0].toString() +
                    "\nnumber: " + sActiveSimSlotPhoneNumber[0] +
                    "\noperator: " + sActiveSimSlotOperator[0] +
                    "\nlabel: " + sActiveSimSlotLabel[0] +
                    "\ncolor: " + iActiveSimSlotColor[0].toString() +
                    "\ncountry: " + sActiveSimSlotCountry[0] +
                    "\nhandle: " + (handleActiveSimSlot[0].toString())
            }

            if (iActiveSimSlot[1] == 1) {
                sHelpText += "\n\nindex_call_ID: 1\nhandle_id = " + sActiveSimSlotID[1] +
                    "\nidreal: " + iActiveSimSlotIDreal[1].toString() +
                    "\nnumber: " + sActiveSimSlotPhoneNumber[1] +
                    "\noperator: " + sActiveSimSlotOperator[1] +
                    "\nlabel: " + sActiveSimSlotLabel[1] +
                    "\ncolor: " + iActiveSimSlotColor[1].toString() +
                    "\ncountry: " + sActiveSimSlotCountry[1] +
                    "\nhandle: " + (handleActiveSimSlot[1].toString())
            }


            if (iSaveDebugMode == 1) {

                var defaultHandle: PhoneAccountHandle? = null
                try {defaultHandle = activity.telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) }
                catch (e : Exception) {}

                val defaultVoiceID = "\n\ndefault voice: " + SubscriptionManager.getDefaultVoiceSubscriptionId()
                val defaultOutgoingID = if (defaultHandle != null) "\ndefault OUT: " + defaultHandle.id
                                        else ""

                showMyAlertDialog(
                    activity,
                    sHelpText + defaultVoiceID + defaultOutgoingID,
                    "Volám... " + number,
                    true,
                    0
                )
            }
            else activity.launchCallIntentNew(number, handleForCall) //activity.callContactWithSim(number, useSimOne)

        }  else { //požadovaný simslot není dostupný
            showMyAlertDialog(activity, "index_call_ID " + (iSimSlot).toString() + " not ready !" , "Error", true, 0)
            }

      } else {
            showMyAlertDialog(activity, activity.getString(R.string.none_simslot_mts), "SIM", true, 0)
      }

    }


  //  Hodnota	Význam
  //  0	Výchozí SIM1
  //  1	Výchozí SIM2
  //  22	Zeptej se dialogem
  //  -1	Žádná aktivní SIM, volání není možné

  //vrací index (viz. getAvailableSIMCardLabels)  který je výchozí pro volání

    fun getDefaultSimSlotForCalls(activity: BaseSimpleActivity): Int {

        val iSimActiveCount = numberOfReadySim(activity)
        if (iSimActiveCount <= 0) return -1  //žádná SIM
        else if (iSimActiveCount == 1) return iActiveSimSlot.indexOf(1) //jen jedna SIM

        var defaultHandle: PhoneAccountHandle? = null
        try {defaultHandle = activity.telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) }
        catch (e : Exception) {}

        //máme více SIM v telefonu
        try {

            if (defaultHandle != null) {
                val defaultPhoneAccountID = defaultHandle.id
                if (iActiveSimSlot[0] == 1 && defaultPhoneAccountID.equals(sActiveSimSlotID[0])) return 0
                if (iActiveSimSlot[1] == 1 && defaultPhoneAccountID.equals(sActiveSimSlotID[1])) return 1
            }

            val defaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (defaultVoiceSubId < 0) return 22 //neplatná hodnota, anebo si uživatzel chce ručně vybírat

          //  if (iActiveSimSlot[0] == 1 && iActiveSimSlotIDreal[0] == defaultVoiceSubId) return 0
          //  if (iActiveSimSlot[1] == 1 && iActiveSimSlotIDreal[1] == defaultVoiceSubId) return 1

            if (iActiveSimSlot[0] == 1 && sActiveSimSlotID[0] == defaultVoiceSubId.toString()) return 0
            if (iActiveSimSlot[1] == 1 && sActiveSimSlotID[1] == defaultVoiceSubId.toString()) return 1

        } catch (e: Exception) {
        }
            return 22
    }

    fun getRecentCallValues(activity: BaseSimpleActivity, recentCall: RecentCall) {

        if (!bSimAccountsChecked) numberOfReadySim(activity)

        bisUnknown = recentCall.isUnknownNumber

        // ---- číslo, na které se volalo ----
        mtsGlobalAll.sSaveNumber = when {
            bisUnknown -> activity.getString(R.string.number_type5_mts) //skryté číslo
            else -> recentCall.specificNumber.ifBlank { recentCall.phoneNumber }

        }
        // formátované číslo mezerami pokud je formátování ON v settings...
        mtsGlobalAll.sSaveFormattedNumber = if (bisUnknown) {activity.getString(R.string.unknown) }
                                            else { numberForRecents(mtsGlobalAll.sSaveNumber,  activity.config.formatPhoneNumbers)  }
        // typ čísla (mobile, work...)
        val typeLabel = when {
            recentCall.specificType.isNotBlank() -> recentCall.specificType
            else -> getNumberTypeLabel(activity, mtsGlobalAll.sSaveNumber)
        }

        mtsGlobalAll.iSaveSim = iActiveSimSlotID.indexOf(recentCall.simID)

        //jméno
        mtsGlobalAll.sSaveName = when {
            bisUnknown -> mtsGlobalAll.sSaveNumber //skryté číslo
            recentCall.name.isNotBlank() -> recentCall.name
            else -> mtsGlobalAll.sSaveFormattedNumber
        }

        val normalizedNameNumber = normalizeDigitsOnly(mtsGlobalAll.sSaveName)
        //o jaké číslo jde?
        mtsGlobalAll.sSaveNumberType = when {
            bisUnknown -> ""  //skryté číslo
            normalizedNameNumber.isNotBlank() && normalizeDigitsOnly(mtsGlobalAll.sSaveFormattedNumber) == normalizedNameNumber -> activity.getString(R.string.number_type2_mts)  //je to číslo a nemáme ho uložené v kontaktech
            typeLabel.isNotEmpty() -> typeLabel.lowercase()  //máme uložené v kontaktech jako nějaký spešl typ
            else -> activity.getString(R.string.number_type4_mts)  //jde o jedinné číslo uložené u daného kontaktu
        }

        //typ hovoru
        mtsGlobalAll.sSaveCallType = when (recentCall.type) {
            1, 9 -> activity.getString(R.string.call_type1_mts)
            2, 10 -> activity.getString(R.string.call_type2_mts)
            3 -> activity.getString(R.string.call_type3_mts)
            4 -> activity.getString(R.string.call_type4_mts)
            5 -> activity.getString(R.string.call_type5_mts)
            6 -> activity.getString(R.string.call_type6_mts)
            7 -> activity.getString(R.string.call_type7_mts)
            else -> activity.getString(R.string.call_type8_mts) + " (" + recentCall.type + ")"
        }

        val ts = if (recentCall.startTS < 10_000_000_000L) {
            recentCall.startTS * 1000
        } else {
            recentCall.startTS
        }
        mtsGlobalAll.sSaveDateTime = ts.formatDateOrTime(activity, true, true, false , true)

      //  val dur = recentCall.duration
     //   mtsGlobalAll.sTextForCalling = when {
     //       dur == 0 -> ""
     //       dur < 60 -> "${dur}s"
     //       dur < 3600 -> "${dur / 60}m ${dur % 60}s"
     //       else -> "${dur / 3600}h ${(dur % 3600) / 60}m"
     //   }

        mtsGlobalAll.bitmapAvatar = getBitmapFromUri(activity, recentCall.photoUri)
            ?: fakeAvatar(activity, R.drawable.fakeavatar)
    }

    fun setHelpTextForCall(activity : BaseSimpleActivity, iForceSavedSim : Int = -1, areOperatorSame : Boolean = false) : Boolean {
        mtsGlobalAll.sSaveDateTime = " "
        val iSaveSimTempX = iSaveSim
        if  (iForceSavedSim >= 0) iSaveSim = iForceSavedSim

        var bRet : Boolean = false

        if ((iSaveSim == 0) || (iSaveSim == 1)) {
            mtsGlobalAll.sSaveCallType =
                if (bOnlyOneSimAvailable) activity.getString(R.string.call_from_sim_mts)
                else activity.getString(R.string.call_from_sim_mts) + (iSaveSim + 1).toString()
        }
        else if (iSaveSim == 22) {
            bRet = true
            iSaveSim = iActiveSimSlot.indexOf(1)
            mtsGlobalAll.sSaveCallType =
                if (bOnlyOneSimAvailable) activity.getString(R.string.call_from_sim_mts)
                else activity.getString(R.string.call_from_sim_mts) + (iSaveSim + 1).toString()
        }

        if ((iSaveSim == 0) || (iSaveSim == 1)) {
            mtsGlobalAll.sSaveDateTime =
                buildSimInfo(
                     mtsGlobalAll.sActiveSimSlotOperator[iSaveSim],
                     mtsGlobalAll.sActiveSimSlotLabel[iSaveSim],
                    areOperatorSame)
        }
        if (iSaveSim < 0)  {
            bRet = true
            mtsGlobalAll.sSaveCallType = activity.getString(R.string.none_sim_mts)
        }

        if ((iSaveSim == 0) || (iSaveSim == 1)) {
            if (iActiveSimSlot[iSaveSim] != 1) bRet = true  //z vybraného simslotu nelze volat
        }

        if  (iForceSavedSim >= 0) iSaveSim = iSaveSimTempX
        return bRet
    }

    fun buildSimInfo(
        operator: String,
        label: String,
        withLabel: Boolean // se posílá true jen pokud SIM1 i SIM2 mají stejný operator String
    ): String {
        return when {
            operator.isNotBlank()  && label.isNotBlank() && withLabel ->
                "$operator, $label"
            operator.isNotBlank()  ->
                "($operator)"
            label.isNotBlank() ->
                "($label)"
            else ->
                " "
        }
    }

    fun makePhoneText(
        formatted: String,
        isSelected: Boolean,
        accentColor: Int,
        showEmoji: Boolean
        ): CharSequence {

        if (!isSelected || !showEmoji) return formatted

        val sb = SpannableStringBuilder(formatted)
        val phoneEmoji = "  \u2705"
        val emojiStart = sb.length
        sb.append(phoneEmoji)
        sb.setSpan(
            ForegroundColorSpan(accentColor),
            emojiStart,
            sb.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return sb
    }


    fun checkCustomSimMts(activity: BaseSimpleActivity, sNumber: String): Int {

       val iRet = checkCustomSimSharedPref(activity, sNumber)
       if (iRet != -4) return iRet

       return -4

   //    try {
   //           //původní metoda, která ukládá PhoneHandle
   //           val PhoneAccountHandleID = activity.MainAppConfig.getCustomSIM(sNumber)
   //           //zatím není nic přiřazeno
  //            if (PhoneAccountHandleID == null) return -4
  //            if (PhoneAccountHandleID.id.indexOf(sActiveSimSlotID[0]) >= 0) return 0
  //            if (PhoneAccountHandleID.id.indexOf(sActiveSimSlotID[1]) >= 0) return 1
  //     } catch (e: Exception) {}
//
 //      return -4
    }

    fun checkCustomSimSharedPref(activity: BaseSimpleActivity, sNumber: String): Int {

    try {
        val sPhoneNumber = normalizeDigitsOnly(sNumber)

        return activity.config.getCustomSim(sPhoneNumber)
    } catch (_: Exception)
    {}
        return -4
    }

    fun removeCustomSimMts(activity: BaseSimpleActivity, sNumber: String,  bToastOn : Boolean = true) {

 //       val PhoneAccountHandleID = activity.MainAppConfig.getCustomSIM(sNumber.normalizePhoneForCompareMTs())
 //       //zatím není nic přiřazeno
 //       if (PhoneAccountHandleID != null) {
 //           activity.MainAppConfig.removeCustomSIM(sNumber.normalizePhoneForCompareMTs())
 //           Toast.makeText(activity, activity.getString(R.string.delete_custom_sim1_mts) + " " + sNumber + " " + activity.getString(R.string.delete_custom_sim2_mts), Toast.LENGTH_SHORT).show()
 //           return
 //       }

        val sPhoneNumber = normalizeDigitsOnly(sNumber)
        activity.config.removeCustomSim(sPhoneNumber)
        if (bToastOn) Toast.makeText(activity, activity.getString(R.string.delete_custom_sim1_mts) + " " + sPhoneNumber + " " + activity.getString(R.string.delete_custom_sim2_mts), Toast.LENGTH_SHORT).show()
    }

    fun saveCustomSimMts(activity: BaseSimpleActivity, sNumber: String, sSimSlot : Int) {

        val sPhoneNumber = normalizeDigitsOnly(sNumber)
        activity.config.saveCustomSim(sPhoneNumber, sSimSlot)
        Toast.makeText(activity, "SIM " + (sSimSlot+1).toString() + " "
            + activity.getString(R.string.save_custom_sim1_mts) + " " + sPhoneNumber + " "
            + activity.getString(R.string.save_custom_sim2_mts), Toast.LENGTH_SHORT).show()

    }

    fun drawSim(imageView: ImageView, tw: TextView?, iSimSlot: Int, ifallBackColor: Int, bDoCalltype: Boolean) {

        // Vyber správnou ikonu podle SIM slotu
        val iconRes = when (iSimSlot) {
            0 -> if (bActiveSimSlotSOS[iSimSlot]) R.drawable.ic_sim_alert else R.drawable.ic_sim1
            1 -> if (bActiveSimSlotSOS[iSimSlot]) R.drawable.ic_sim_alert else R.drawable.ic_sim2
            else -> R.drawable.ic_sim_alert
        }

        if (tw != null) {
            tw.text = if (bDoCalltype && mtsGlobalAll.sSaveDateTime.isNotBlank()) mtsGlobalAll.sSaveCallType + "\r\n" + mtsGlobalAll.sSaveDateTime
                      else if (bDoCalltype && mtsGlobalAll.sSaveDateTime.isBlank()) mtsGlobalAll.sSaveCallType
                      else mtsGlobalAll.sSaveDateTime
        }


        try {
            val drawable = AppCompatResources.getDrawable(imageView.context, iconRes)?.mutate()

            if (drawable != null) {
                var simColor = ifallBackColor
                if (iSimSlot in 0..1)
                     simColor = mtsGlobalAll.iActiveSimSlotColor.getOrNull(iSimSlot) ?: ifallBackColor
                val alpha = (simColor ushr 24) and 0xFF
                val realColor = if (alpha == 0) ifallBackColor else simColor  // 💡 toto řeší zmizení ikon
                drawable.setTint(realColor)
                imageView.setImageDrawable(drawable)
            }
        } catch (e: Exception) {
            try {
                 val drawable = AppCompatResources.getDrawable(imageView.context, iconRes)?.mutate()
                 if (drawable != null) {
                 drawable.setTint(ifallBackColor)
                 imageView.setImageDrawable(drawable)
                }
            } catch (e: Exception) {}
        }

        if (iconRes == R.drawable.ic_sim_alert) return
        if (bOnlyOneSimAvailable) imageView.visibility = View.GONE
    }


    fun launchAbout(activity: BaseSimpleActivity) {

        //unlockAll(activity)

        val url = "https://mts.speccy.cz/mtsphone-news.htm#v" + activity.appVersionCode.toInt().toString()
        val linkText = "see news"
        val sText = "\n" +
            "🏷️ [version]: " + activity.appVersionName +  "   " + linkText + "\n\n" +
            "🌐 [home]:" + "\n" +
            "mts.speccy.cz/mtsphone.htm" + "\n\n" +
            "🔑 [license]:" + "\n" +
            "GNU/GPL3, Apache 2.0, MIT, BSD" + "\n\n" +
            "🪙 [donate]:" + "\n" +
            "BTC: 14b8S8D98xBx4G5DCkt4XYsU3X4QQ7nivj" + "\n\n" +
            "📜 [history]:" + "\n" +
            "This application has its roots as SimpleMobileTools (Slovak developer Tibor Kabuta) " +
            "and his successors Fossify (Indian developer Naveen Singh). " +
            "I made a new fork, with love, from the Czech Republic  \uD83C\uDDE8\uD83C\uDDFF" + "\n"

        val spannable = SpannableString(sText)
        val linkStart = sText.indexOf(linkText)
        if (linkStart >= 0) {
            spannable.setSpan(
                URLSpan(url),
                linkStart,
                linkStart + linkText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val dialog: AlertDialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.about_mts)
                .setMessage(spannable)
                .setPositiveButton("OK") { dialog, _ -> newsnotify(activity) }
                .setNeutralButton("WWW") { dialog, _ -> goToMyWww(activity) }
                .setNegativeButton("BTC") { dialog, _ -> goToMyBtc(activity) }
                .create()
        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = spannable  // znovu nastavit, aby se aplikoval movementMethod
        }

    }



private fun goToMyBtc(activity: BaseSimpleActivity) {
    goWWW(activity, "https://drive.google.com/file/d/1IUaYSi05fpQy34Elc2ykdvoIM4jw6U6e/view?usp=drive_link")
    copyTextToClipboard(activity, "BTC address", "14b8S8D98xBx4G5DCkt4XYsU3X4QQ7nivj")
}

private fun goToMyWww(activity: BaseSimpleActivity) {
    goWWW(activity, "https://mts.speccy.cz/mtsphone.htm")
}

fun newsnotify(activity: BaseSimpleActivity) {
    AppUpdateNotificationManager(activity).showSpecialNotification(
        clickAction = AppUpdateNotificationManager.ClickAction.OPEN_MY_WEB
    )
}

private fun goWWW (activity: BaseSimpleActivity, sWWW : String ) {
    try {
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sWWW))
    activity.startActivity(browserIntent)
    } catch (e: Exception ) { activity.toast(e.message.toString())}

}



    fun launchBlockedManagement(context : Context) {
       Intent(context, ManageBlockedNumbersActivity::class.java).apply {context.startActivity(this)}
    }

//    fun unlockAll(activity: BaseSimpleActivity) {
//        activity.config.hadThankYouInstalled = true
//    }


    fun isEmergencyOnlyLabel(label: String?): Boolean {

        if (label.isNullOrBlank()) return false

       // normalizace (lowercase + odstranění diakritiky)
        val normalized = Normalizer.normalize(label.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REMOVAL, "")   // odstranění diakritiky
            .replace(NON_LETTER_NUMBER_SPACE, " ") // odstranění symbolů
            .replace("\\s+".toRegex(), " ")
            .trim()

        // Klíčová slova a fráze (můžeš doplnit podle potřeby)
        val keywords = listOf(
            // angličtina
            "emergency", "emergency calls only",
            // čeština / slovensko
            "tisnov", "tisnova", "pouze tisnov", "jen tisnov",
            // němčina
            "notruf", "nur notruf", "nur notrufe",
            // španělština
            "llamadas de emergencia", "solo llamadas de emergencia",
            // francouzština
            "appels urgence", "appels d urgence",
            // italština
            "chiamate di emergenza",
            // portugalština
            "chamadas de emergencia",
            // nizozemština
            "noodoproep", "noodoproepen",
            // čínština / japonština / korejština – substring stačí
            "紧急", "緊急", "긴급"
        )

        // jednoduché testování
        for (kw in keywords) {
            if (normalized.contains(kw)) return true
        }

        // fallback pro zkrácené / zmršené texty (např. "EMERG ONLY")
        val fuzzyFragments = listOf("emerg", "notruf", "tisn", "urgent", "acil", "darurat")
        for (frag in fuzzyFragments) {
            if (normalized.contains(frag)) return true
        }

        return false
    }

    fun BaseSimpleActivity.launchCallIntentNew(recipient: String, handle: PhoneAccountHandle? = null) {
        handlePermission(PERMISSION_CALL_PHONE) {
            val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
            Intent(action).apply {
                data = Uri.fromParts("tel", recipient, null)

                if (handle != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }

                if (isDefaultDialer()) {
                    val packageName = if (config.appId.contains(".debug", true)) MY_APP_NAME_GOOGLE_ID+".debug" else MY_APP_NAME_GOOGLE_ID
                    val className = MY_APP_NAME_GOOGLE_ID+".activities.DialerActivity"
                    setClassName(packageName, className)
                }

                launchActivityIntent(this)
            }
        }
    }

    fun categoryToEmoji(sLabel: String, sStatus: String, bRetBlankString: Boolean = false): String {
        val statusEmoji = when (sStatus.trim().lowercase()) {
            "spam" -> "⛔"
            "safe" -> "🟢"
            else -> ""
        }
        val labelEmoji = when (sLabel.trim().lowercase()) {
            "scam" -> "🎣"
            "telemarketing", "surveys" -> "🗣️"
            "silent_call" -> "🤖"
            "financial_services" -> "💰"
            "debt_collection" -> "🥊"
            "business" -> "🏢"
            "retail" -> "🛒"
            "personal_blacklist" -> "📓"
            //"private_number" -> "🕶️"
            else -> ""
        }

        val result = statusEmoji + labelEmoji
            //listOf(statusEmoji, labelEmoji)
           // .filter { it.isNotBlank() }
           // .joinToString(",")

        return if (!bRetBlankString) result.ifBlank { "❔" } else result
    }


    fun checkNumberForRating(result: CallFilterResult, number: String, bRecent: Boolean): String {

        //error výsledek
        if (!result.ok) return "💥😕"

        val sRetNumber = normalizeDigitsOnly(result.normalizedNumber, false)
        val sVstupniNumber = normalizeDigitsOnly(number, false)
        val bSpamCallfilterApp = result.status.trim().lowercase() == "spam"

        //vstupní anebo vrácené číslo je prázdné
        if (sVstupniNumber.isBlank() || sRetNumber.isBlank()) {
            return "∅😕"
        }

        //vstupní a vrácené číslo si nejsou podobné
        if (!sRetNumber.contains(sVstupniNumber) && !sVstupniNumber.contains(sRetNumber)) {
            return "≠😕"
        }

        // čísla si odpovídají takže vyhodnotíme
        val callFilterEmoji = categoryToEmoji(result.label, result.status, true)
        val pos = result.iResultPositive
        val neg = result.iResultNegative
        val neu = result.iResultNeutral
        val total = pos + neg + neu

        var bSpam = false
        val sRetText: String

        when {
            //1. je to spam a známe labelEmoji
            bSpamCallfilterApp && callFilterEmoji.isNotBlank() -> {
                sRetText = callFilterEmoji
                bSpam = true
            }
            //2. alespoň 2 hodnocení a pozitivní převažuje nad negativním+neutrálním
            (total > 1) && (pos > (neg + neu)) -> {
                sRetText = " 🟢 ${pos}x"
            }
            //3. alespoň 2 hodnocení a negativní převažuje nad pozitivním+neutrálním
            (total > 1) && (neg > (pos + neu)) -> {
                sRetText = " ⛔ ${neg}x"
                bSpam = true
            }
            //4. nedá se jednoznačně určit
            //(total > 1) && ((neg + pos) > 0) -> {
            //    sRetText = " ⛔ ${neg}x, 🟢 ${pos}x"
            //}
            //5. dáme hodnocení Callfilter.app
            else -> {
                sRetText = categoryToEmoji(result.label, result.status)
            }
        }

        return if (!bRecent) sRetText
        else when {
            bSpamCallfilterApp -> categoryToEmoji(result.label, result.status)
            bSpam -> "⛔"
            else -> ""
        }
    }

    fun openSpamNumberWeb (activity : BaseSimpleActivity, sPhoneNumber : String)
    {
        val url = "https://www.muzutozvednout.cz/search?q=" + URLEncoder.encode(sPhoneNumber.filter { it.isDigit() || it == '+' }, StandardCharsets.UTF_8.toString())
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.toast(e.message.toString())
        }
    }
}
