package cz.mts.phone.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import cz.mts.base.dialogs.ConfirmationAdvancedDialog
import cz.mts.base.helpers.LocalContactPhotoStorage
import cz.mts.base.dialogs.RadioGroupDialog
import cz.mts.base.extensions.applyColorFilter
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.getDateTimeFromDateString
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.base.extensions.hideKeyboard
import cz.mts.base.extensions.insetsController
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.showErrorToast
import cz.mts.base.extensions.showKeyboard
import cz.mts.base.extensions.toast
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.value
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.DEFAULT_ADDRESS_TYPE
import cz.mts.base.helpers.DEFAULT_EMAIL_TYPE
import cz.mts.base.helpers.DEFAULT_EVENT_TYPE
import cz.mts.base.helpers.DEFAULT_IM_TYPE
import cz.mts.base.helpers.DEFAULT_PHONE_NUMBER_TYPE
import cz.mts.base.helpers.LocalContactsHelper
import cz.mts.base.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import cz.mts.base.helpers.SimpleContactsHelper
import cz.mts.base.helpers.VcfImporter
import cz.mts.base.helpers.VcfImporter.MAX_PHOTO_BYTES_LOCAL
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.models.PhoneNumber
import cz.mts.base.models.RadioItem
import cz.mts.base.models.contacts.Address
import cz.mts.base.models.contacts.Contact
import cz.mts.base.models.contacts.Email
import cz.mts.base.models.contacts.Event
import cz.mts.base.models.contacts.IM
import cz.mts.base.models.contacts.Organization
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityEditLocalContactBinding
import cz.mts.phone.databinding.ItemEditEmailBinding
import cz.mts.phone.databinding.ItemEditImBinding
import cz.mts.phone.databinding.ItemEditPhoneNumberBinding
import cz.mts.phone.databinding.ItemEditStructuredAddressBinding
import cz.mts.phone.databinding.ItemEditWebsiteBinding
import cz.mts.phone.databinding.ItemEventBinding
import cz.mts.phone.helpers.CacheContacts
import java.util.Calendar
import java.util.LinkedList
import java.util.Locale

/**
 * Aktivita pro editaci lokálního kontaktu uloženého v Room databázi.
 *
 * Spuštění:
 *   val intent = Intent(context, EditLocalContactActivity::class.java)
 *   intent.putExtra(EditLocalContactActivity.CONTACT_ID, contactId)
 *   startActivity(intent)
 *
 * Vrací Activity.RESULT_OK po úspěšném uložení.
 *
 * Co je podporováno:
 *   - prefix, jméno, prostřední jméno, příjmení, suffix, přezdívka
 *   - telefonní čísla (s výběrem typu a přepínáním primárního čísla)
 *   - e-maily (s výběrem typu)
 *   - strukturované adresy (s výběrem typu)
 *   - IM (s výběrem protokolu)
 *   - události – narozeniny, výročí apod. (s výběrem datumu přes DatePickerDialog)
 *   - poznámky
 *   - organizace / pracovní pozice
 *   - webové stránky (pouze jedna)
 *   - hvězdičkování (oblíbené)
 *
 * Co není podporováno (záměrně vynecháno):
 *   - skupiny
 *   - vyzváněcí tón
 *   - zdroj kontaktu (je vždy MTS_PHONE / lokální)
 */
class EditLocalContactActivity : SimpleActivity() {
    override var customNavBarLightIcons: Boolean? = null

    companion object {
        const val CONTACT_ID = "contact_id"
        const val CONTACT_SOURCE = "contact_source"
    }
    protected var contact: Contact? = null

    private var isSaving = false
    private var iContactSource = 0
    private var mLastSavePromptTS = 0L
    private val binding by viewBinding(ActivityEditLocalContactBinding::inflate)

    /**
     * Dočasně drží nová foto data (ByteArray) vybraná uživatelem, ale dosud neuložená na disk.
     * null = uživatel foto nezměnil.
     */
    private var pendingPhotoBytes: ByteArray? = null

    /**
     * Původní URI fotky kontaktu při otevření aktivity.
     * Potřebujeme ji, abychom po potvrzení uložení mohli smazat starý soubor.
     */
    private var originalPhotoUri: String = ""

    /** Launcher pro výběr obrázku z úložiště (ekvivalent setupContactsImport v SettingsActivity). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePickedPhoto(uri)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        window.insetsController().isAppearanceLightStatusBars = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Edge-to-edge výplň – ScrollView přijme inset od klávesnice a systémových lišt
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactScrollview))

        val contactId = intent.getIntExtra(CONTACT_ID, 0)
        iContactSource = intent.getIntExtra(CONTACT_SOURCE, 0)
        if (contactId == 0) {
            finish()
            return
        }

        // Načteme kontakt na pozadí – Room nesmí být dotazován na UI vlákně
        ensureBackgroundThread {
            if (iContactSource == 1) //lokální skryté, uložené v db
                contact = LocalContactsHelper(this).getContactWithId(contactId)
            //normální android systémové
            else contact = CacheContacts.findContactById(contactId)

            runOnUiThread {
                if (contact == null) {
                    toast(cz.mts.base.R.string.unknown_error_occurred)
                    finish()
                    return@runOnUiThread
                }
                gotContact()
                // Zapamatujeme si původní URI hned po načtení kontaktu
                originalPhotoUri = contact?.photoUri.orEmpty()
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        maybeShowUnsavedChangesDialog { finish() }
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inicializace UI po načtení kontaktu
    // ─────────────────────────────────────────────────────────────────────────

    private fun gotContact() {
        // ScrollView zobrazíme až po naplnění dat, aby nebylo vidět prázdné probliknutí
        binding.contactScrollview.beVisible()

        // Naplníme formulář hodnotami z kontaktu
        setupEditContact()

        // Avatar – kulatý obrázek kontaktu
        setupAvatar()

        // Přebarvení ikon kontaktu barvou textu
        val textColor = getProperTextColor()
        arrayOf(
            binding.contactNameImage,
            binding.contactNumbersImage,
            binding.contactEmailsImage,
            binding.contactAddressesImage,
            binding.contactImsImage,
            binding.contactEventsImage,
            binding.contactNotesImage,
            binding.contactOrganizationImage,
            binding.contactWebsitesImage,
            binding.contactChangePhoto,
            binding.contactSourceImage
        ).forEach { it.applyColorFilter(textColor) }

        // Přebarvení tlačítek „přidat" primární barvou
        val primaryColor = getProperPrimaryColor()
        arrayOf(
            binding.contactNumbersAddNew,
            binding.contactEmailsAddNew,
            binding.contactAddressesAddNew,
            binding.contactImsAddNew,
            binding.contactEventsAddNew
        ).forEach { it.applyColorFilter(primaryColor) }

        // Pozadí tlačítek „přidat" barvou textu
        arrayOf(
            binding.contactNumbersAddNew.background,
            binding.contactEmailsAddNew.background,
            binding.contactAddressesAddNew.background,
            binding.contactImsAddNew.background,
            binding.contactEventsAddNew.background
        ).forEach { it?.applyColorFilter(textColor) }

        // Hvězdička – oblíbené
        binding.contactToggleFavorite.apply {
            tag = contact!!.starred
            updateFavoriteIcon()

            setOnClickListener { toggleFavorite() }
            setOnLongClickListener {
                toast(R.string.toggle_favorite)
                true
            }
        }

        // Kliknutí na foto / změna fota
        binding.contactPhoto.setOnClickListener { trySetPhoto() }
        binding.contactChangePhoto.setOnClickListener { trySetPhoto() }

        // Tlačítka pro přidání nové položky (website nemá tlačítko přidat – je jen jedno pole)
        binding.contactNumbersAddNew.setOnClickListener { addNewPhoneNumberField() }
        binding.contactEmailsAddNew.setOnClickListener { addNewEmailField() }
        binding.contactAddressesAddNew.setOnClickListener { addNewAddressField() }
        binding.contactImsAddNew.setOnClickListener { addNewIMField() }
        binding.contactEventsAddNew.setOnClickListener { addNewEventField() }

        setupToolbar()
        updateTextColors(binding.contactScrollview)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Avatar
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAvatar() {
        val c = contact ?: return
        //     val textColor = getProperTextColor()

        val placeholderDrawable = ContextCompat.getDrawable(this, R.drawable.fakeavatar)

        SimpleContactsHelper(this).loadContactImage(
            path = c.photoUri,
            imageView = binding.contactPhoto,
            placeholderName = c.getNameToDisplay(),
            placeholderImage = placeholderDrawable,
            isUnknown = false
        )
    }

    private fun setupToolbar() {
        val primaryColor = getProperPrimaryColor()

        binding.contactToolbar.apply {

            navigationIcon?.mutate()?.setTint(primaryColor)

            if (iContactSource == 1) {
              menu.add(Menu.NONE, R.id.save, Menu.NONE, cz.mts.base.R.string.save).apply {
                setIcon(R.drawable.ic_check_vector)
                icon?.mutate()?.setTint(primaryColor)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                setOnMenuItemClickListener {
                    saveContact()
                    true
                }
              }
            }

            setNavigationOnClickListener {
                maybeShowUnsavedChangesDialog {
                    hideKeyboard()
                    finish()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Naplnění formuláře daty kontaktu (setup* metody)
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupEditContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupNames()
        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupEvents()
        setupNotes()
        setupOrganization()
        setupWebsite()
        setupSource()
    }

    private fun setupNames() {
        contact!!.apply {
            binding.contactPrefix.setText(prefix)
            binding.contactFirstName.setText(firstName)
            binding.contactMiddleName.setText(middleName)
            binding.contactSurname.setText(surname)
            binding.contactSuffix.setText(suffix)
            binding.contactNickname.setText(nickname)
        }
    }

    private fun setupPhoneNumbers() {
        val phoneNumbers = contact!!.phoneNumbers
        phoneNumbers.forEachIndexed { index, number ->
            val view = binding.contactNumbersHolder.getChildAt(index)
            val holder = if (view == null) {
                ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false)
                    .also { binding.contactNumbersHolder.addView(it.root) }
            } else {
                ItemEditPhoneNumberBinding.bind(view)
            }
            holder.apply {
                contactNumber.setText(number.value)
                contactNumber.tag = number.normalizedNumber
                setupPhoneNumberTypePicker(contactNumberType, number.type, number.label)
                defaultToggleIcon.tag = if (number.isPrimary) 1 else 0
                setupRemoveButton(contactNumberRemove, binding.contactNumbersHolder, holder.root) {
                    initNumberHolders()
                }
            }
        }

        // Pokud kontakt ještě nemá žádné číslo, přidáme prázdný slot
        if (phoneNumbers.isEmpty()) {
            val holder = ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false)
            setupPhoneNumberTypePicker(holder.contactNumberType, DEFAULT_PHONE_NUMBER_TYPE, "")
            holder.defaultToggleIcon.tag = 0
            setupRemoveButton(holder.contactNumberRemove, binding.contactNumbersHolder, holder.root) {
                initNumberHolders()
            }
            binding.contactNumbersHolder.addView(holder.root)
        }

        initNumberHolders()
    }

    private fun setupEmails() {
        contact!!.emails.forEachIndexed { index, email ->
            val view = binding.contactEmailsHolder.getChildAt(index)
            val holder = if (view == null) {
                ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false)
                    .also { binding.contactEmailsHolder.addView(it.root) }
            } else {
                ItemEditEmailBinding.bind(view)
            }
            holder.apply {
                contactEmail.setText(email.value)
                setupEmailTypePicker(contactEmailType, email.type, email.label)
                setupRemoveButton(contactEmailRemove, binding.contactEmailsHolder, holder.root)
            }
        }
    }

    private fun setupAddresses() {
        // Vždy používáme strukturovanou adresu (item_edit_structured_address.xml)
        contact!!.addresses.forEachIndexed { index, address ->
            val view = binding.contactAddressesHolder.getChildAt(index)
            val holder = if (view == null) {
                ItemEditStructuredAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false)
                    .also { binding.contactAddressesHolder.addView(it.root) }
            } else {
                ItemEditStructuredAddressBinding.bind(view)
            }
            holder.apply {
                contactStreet.setText(address.street)
                contactNeighborhood.setText(address.neighborhood)
                contactCity.setText(address.city)
                contactPostcode.setText(address.postcode)
                contactPobox.setText(address.pobox)
                contactRegion.setText(address.region)
                contactCountry.setText(address.country)
                setupAddressTypePicker(contactStructuredAddressType, address.type, address.label)
                setupRemoveButton(contactAddressRemove, binding.contactAddressesHolder, holder.root)
            }
        }
    }

    private fun setupIMs() {
        contact!!.IMs.forEachIndexed { index, im ->
            val view = binding.contactImsHolder.getChildAt(index)
            val holder = if (view == null) {
                ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false)
                    .also { binding.contactImsHolder.addView(it.root) }
            } else {
                ItemEditImBinding.bind(view)
            }
            holder.apply {
                contactIm.setText(im.value)
                setupIMTypePicker(contactImType, im.type, im.label)
                setupRemoveButton(contactImRemove, binding.contactImsHolder, holder.root)
            }
        }
    }

    private fun setupEvents() {
        contact!!.events.forEachIndexed { index, event ->
            val view = binding.contactEventsHolder.getChildAt(index)
            val holder = if (view == null) {
                ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false)
                    .also { binding.contactEventsHolder.addView(it.root) }
            } else {
                ItemEventBinding.bind(view)
            }
            holder.apply {
                contactEvent.apply {
                    event.value.getDateTimeFromDateString(true, this)
                    tag = event.value
                    alpha = 1f
                }
                setupEventTypePicker(this, event.type)
                // Existující událost – mazací tlačítko odebere celý řádek
                setupRemoveButton(contactEventRemove, binding.contactEventsHolder, holder.root)
            }
        }
    }

    private fun setupNotes() {
        binding.contactNotes.setText(contact!!.notes)
    }

    private fun setupOrganization() {
        binding.contactOrganizationCompany.setText(contact!!.organization.company)
        binding.contactOrganizationJobPosition.setText(contact!!.organization.jobPosition)
    }


    private fun setupSource() {
        val source = contact?.source.orEmpty()
        binding.contactSourceHolder.contactSource.setText(source)
    }

    /**
     * Website – povolena pouze jedna.
     * Žádné tlačítko „přidat" – layout obsahuje jedno pevné pole s mínus tlačítkem pro smazání.
     * Pokud kontakt má website, předvyplníme ho; jinak pole zůstane prázdné.
     */
    private fun setupWebsite() {
        // Ujistíme se, že v holderu je právě jedno pole (může být předpřipraveno v layoutu)
        if (binding.contactWebsitesHolder.childCount == 0) {
            val holder = ItemEditWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false)
            updateTextColors(holder.root)
            setupWebsiteRemoveButton(holder)
            binding.contactWebsitesHolder.addView(holder.root)
        }

        // Naplníme první (a jediné) pole hodnotou
        val websiteHolder = ItemEditWebsiteBinding.bind(binding.contactWebsitesHolder.getChildAt(0))
        val website = contact!!.websites.firstOrNull() ?: ""
        websiteHolder.contactWebsite.setText(website)
        setupWebsiteRemoveButton(websiteHolder)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pomocná funkce: nastavení mínus tlačítka pro odebrání řádku
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nastaví vzhled a chování mínus tlačítka.
     * Po kliknutí odebere [itemView] z [container].
     * Volitelný [afterRemove] callback se zavolá po odebrání (např. pro aktualizaci hvězdiček).
     */
    private fun setupRemoveButton(
        removeButton: ImageView,
        container: android.view.ViewGroup,
        itemView: android.view.View,
        afterRemove: (() -> Unit)? = null
    ) {
        removeButton.apply {
            applyColorFilter(getProperPrimaryColor())
            background?.applyColorFilter(getProperTextColor())
            setOnClickListener {
                container.removeView(itemView)
                afterRemove?.invoke()
            }
        }
    }

    /**
     * Mínus tlačítko pro website – pouze vymaže obsah pole, neposkytuje odebrání řádku
     * (pole je jen jedno a musí v layoutu zůstat).
     */
    private fun setupWebsiteRemoveButton(holder: ItemEditWebsiteBinding) {
        holder.contactWebsiteRemove.apply {
            applyColorFilter(getProperPrimaryColor())
            background?.applyColorFilter(getProperTextColor())
            setOnClickListener {
                holder.contactWebsite.setText("")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nastavení "type picker" klikatelnosti pro každý řádek
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupPhoneNumberTypePicker(typeField: TextView, type: Int, label: String) {
        typeField.apply {
            text = phoneNumberTypeText(type, label)
            setOnClickListener { showPhoneNumberTypePicker(this) }
        }
    }

    private fun setupEmailTypePicker(typeField: TextView, type: Int, label: String) {
        typeField.apply {
            text = emailTypeText(type, label)
            setOnClickListener { showEmailTypePicker(this) }
        }
    }

    private fun setupAddressTypePicker(typeField: TextView, type: Int, label: String) {
        typeField.apply {
            text = addressTypeText(type, label)
            setOnClickListener { showAddressTypePicker(this) }
        }
    }

    private fun setupIMTypePicker(typeField: TextView, type: Int, label: String) {
        typeField.apply {
            text = imTypeText(type, label)
            setOnClickListener { showIMTypePicker(this) }
        }
    }

    /**
     * Nastaví klikatelnost na textový zobrazovač datumu i na výběr typu události.
     * Voláno jak při načítání existující události, tak při přidávání nové.
     */
    private fun setupEventTypePicker(eventHolder: ItemEventBinding, type: Int = DEFAULT_EVENT_TYPE) {
        eventHolder.contactEventType.apply {
            setText(eventTypeTextResId(type))
            setOnClickListener { showEventTypePicker(this) }
        }
        eventHolder.contactEvent.setOnClickListener {
            showDatePickerForEvent(eventHolder.contactEvent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogy pro výběr typu
    // ─────────────────────────────────────────────────────────────────────────

    private fun showPhoneNumberTypePicker(typeField: TextView) {
        val items = arrayListOf(
            RadioItem(Phone.TYPE_MOBILE, getString(cz.mts.base.R.string.mobile)),
            RadioItem(Phone.TYPE_HOME, getString(cz.mts.base.R.string.home)),
            RadioItem(Phone.TYPE_WORK, getString(cz.mts.base.R.string.work)),
            RadioItem(Phone.TYPE_MAIN, getString(cz.mts.base.R.string.main_number)),
            RadioItem(Phone.TYPE_FAX_WORK, getString(cz.mts.base.R.string.work_fax)),
            RadioItem(Phone.TYPE_FAX_HOME, getString(cz.mts.base.R.string.home_fax)),
            RadioItem(Phone.TYPE_PAGER, getString(cz.mts.base.R.string.pager)),
            RadioItem(Phone.TYPE_OTHER, getString(cz.mts.base.R.string.other))
        )
        RadioGroupDialog(this, items, phoneNumberTypeId(typeField.value)) {
            typeField.text = phoneNumberTypeText(it as Int, "")
        }
    }

    private fun showEmailTypePicker(typeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Email.TYPE_HOME, getString(cz.mts.base.R.string.home)),
            RadioItem(CommonDataKinds.Email.TYPE_WORK, getString(cz.mts.base.R.string.work)),
            RadioItem(CommonDataKinds.Email.TYPE_MOBILE, getString(cz.mts.base.R.string.mobile)),
            RadioItem(CommonDataKinds.Email.TYPE_OTHER, getString(cz.mts.base.R.string.other))
        )
        RadioGroupDialog(this, items, emailTypeId(typeField.value)) {
            typeField.text = emailTypeText(it as Int, "")
        }
    }

    private fun showAddressTypePicker(typeField: TextView) {
        val items = arrayListOf(
            RadioItem(StructuredPostal.TYPE_HOME, getString(cz.mts.base.R.string.home)),
            RadioItem(StructuredPostal.TYPE_WORK, getString(cz.mts.base.R.string.work)),
            RadioItem(StructuredPostal.TYPE_OTHER, getString(cz.mts.base.R.string.other))
        )
        RadioGroupDialog(this, items, addressTypeId(typeField.value)) {
            typeField.text = addressTypeText(it as Int, "")
        }
    }

    private fun showIMTypePicker(typeField: TextView) {
        val items = arrayListOf(
            RadioItem(Im.PROTOCOL_AIM, getString(R.string.aim)),
            RadioItem(Im.PROTOCOL_MSN, getString(R.string.windows_live)),
            RadioItem(Im.PROTOCOL_YAHOO, getString(R.string.yahoo)),
            RadioItem(Im.PROTOCOL_SKYPE, getString(R.string.skype)),
            RadioItem(Im.PROTOCOL_QQ, getString(R.string.qq)),
            RadioItem(Im.PROTOCOL_GOOGLE_TALK, getString(R.string.hangouts)),
            RadioItem(Im.PROTOCOL_ICQ, getString(R.string.icq)),
            RadioItem(Im.PROTOCOL_JABBER, getString(R.string.jabber))
        )
        RadioGroupDialog(this, items, imTypeId(typeField.value)) {
            typeField.text = imTypeText(it as Int, "")
        }
    }

    private fun showEventTypePicker(typeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Event.TYPE_BIRTHDAY, getString(R.string.birthday)),
            RadioItem(CommonDataKinds.Event.TYPE_ANNIVERSARY, getString(R.string.anniversary)),
            RadioItem(CommonDataKinds.Event.TYPE_OTHER, getString(cz.mts.base.R.string.other))
        )
        RadioGroupDialog(this, items, eventTypeId(typeField.value)) {
            typeField.setText(eventTypeTextResId(it as Int))
        }
    }

    /**
     * Zobrazí systémový DatePickerDialog a po výběru datumu aktualizuje TextView události.
     * Tag na TextView nese raw date string ve formátu "YYYY-MM-DD".
     */
    private fun showDatePickerForEvent(eventTextView: TextView) {
        val cal = Calendar.getInstance()
        val rawTag = eventTextView.tag?.toString() ?: ""

        // Pokud máme uložené datum, předvyplníme picker
        if (rawTag.isNotEmpty()) {
            try {
                // Formáty: "YYYY-MM-DD" nebo "--MM-DD" (bez roku)
                val parts = rawTag.trimStart('-').split("-").filter { it.isNotEmpty() }
                when {
                    parts.size >= 3 -> {
                        cal.set(Calendar.YEAR, parts[0].toInt())
                        cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                        cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                    }
                    parts.size == 2 -> {
                        cal.set(Calendar.MONTH, parts[0].toInt() - 1)
                        cal.set(Calendar.DAY_OF_MONTH, parts[1].toInt())
                    }
                }
            } catch (_: Exception) { /* ponecháme aktuální datum */ }
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                eventTextView.apply {
                    dateStr.getDateTimeFromDateString(true, this)
                    tag = dateStr
                    alpha = 1f
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Přidávání nových polí
    // ─────────────────────────────────────────────────────────────────────────

    private fun addNewPhoneNumberField() {
        val holder = ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false)
        updateTextColors(holder.root)
        setupPhoneNumberTypePicker(holder.contactNumberType, DEFAULT_PHONE_NUMBER_TYPE, "")
        holder.defaultToggleIcon.tag = 0
        setupRemoveButton(holder.contactNumberRemove, binding.contactNumbersHolder, holder.root) {
            initNumberHolders()
        }
        binding.contactNumbersHolder.addView(holder.root)
        binding.contactNumbersHolder.onGlobalLayout {
            holder.contactNumber.requestFocus()
            showKeyboard(holder.contactNumber)
        }
        initNumberHolders()
    }

    private fun addNewEmailField() {
        val holder = ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false)
        updateTextColors(holder.root)
        setupEmailTypePicker(holder.contactEmailType, DEFAULT_EMAIL_TYPE, "")
        setupRemoveButton(holder.contactEmailRemove, binding.contactEmailsHolder, holder.root)
        binding.contactEmailsHolder.addView(holder.root)
        binding.contactEmailsHolder.onGlobalLayout {
            holder.contactEmail.requestFocus()
            showKeyboard(holder.contactEmail)
        }
    }

    private fun addNewAddressField() {
        val holder = ItemEditStructuredAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false)
        updateTextColors(holder.root)
        setupAddressTypePicker(holder.contactStructuredAddressType, DEFAULT_ADDRESS_TYPE, "")
        setupRemoveButton(holder.contactAddressRemove, binding.contactAddressesHolder, holder.root)
        binding.contactAddressesHolder.addView(holder.root)
        binding.contactAddressesHolder.onGlobalLayout {
            holder.contactStreet.requestFocus()
            showKeyboard(holder.contactStreet)
        }
    }

    private fun addNewIMField() {
        val holder = ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false)
        updateTextColors(holder.root)
        setupIMTypePicker(holder.contactImType, DEFAULT_IM_TYPE, "")
        setupRemoveButton(holder.contactImRemove, binding.contactImsHolder, holder.root)
        binding.contactImsHolder.addView(holder.root)
        binding.contactImsHolder.onGlobalLayout {
            holder.contactIm.requestFocus()
            showKeyboard(holder.contactIm)
        }
    }

    private fun addNewEventField() {
        val holder = ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false)
        updateTextColors(holder.root)
        setupEventTypePicker(holder)
        setupRemoveButton(holder.contactEventRemove, binding.contactEventsHolder, holder.root)
        binding.contactEventsHolder.addView(holder.root)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Primární číslo (hvězdička u každého čísla)
    // ─────────────────────────────────────────────────────────────────────────

    /** Přepne primární číslo – jen jedno číslo může být primární. */
    private fun setDefaultNumber(selected: ImageView) {
        val count = binding.contactNumbersHolder.childCount
        for (i in 0 until count) {
            val icon = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i)).defaultToggleIcon
            if (icon != selected) icon.tag = 0
        }
        selected.tag = if (selected.tag == 1) 0 else 1
        initNumberHolders()
    }

    /** Aktualizuje ikonky hvězdičky u telefonních čísel. */
    private fun initNumberHolders() {
        val count = binding.contactNumbersHolder.childCount
        // Jediné číslo – hvězdičku skryjeme (není potřeba označovat primární)
        if (count == 1) {
            ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(0))
                .defaultToggleIcon.beGone()
            return
        }
        for (i in 0 until count) {
            val holder = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i))
            val isPrimary = holder.defaultToggleIcon.tag == 1
            val drawableId = if (isPrimary) cz.mts.base.R.drawable.ic_star_vector
            else cz.mts.base.R.drawable.ic_star_outline_vector
            val drawable = ContextCompat.getDrawable(this, drawableId)?.apply {
                mutate()
                setTint(getProperTextColor())
            }
            holder.defaultToggleIcon.apply {
                setImageDrawable(drawable)
                beVisible()
                setOnClickListener { setDefaultNumber(this) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hvězdička – oblíbené
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleFavorite() {
        binding.contactToggleFavorite.tag =
            if (isContactStarred()) 0 else 1

        updateFavoriteIcon()
    }

    private fun isContactStarred() = binding.contactToggleFavorite.tag == 1

    @Suppress("DEPRECATION")
    private fun getStarDrawable(on: Boolean) = resources.getDrawable(
        if (on) cz.mts.base.R.drawable.ic_star_vector
        else cz.mts.base.R.drawable.ic_star_outline_vector
    )


    private fun trySetPhoto() {
        pickPhoto.launch(arrayOf("image/*"))
    }

    /**
     * Zpracuje URI obrázku vráceného výběrem souboru:
     *  1. Přečte bytes ze stream.
     *  2. Zmenší, pokud je třeba.
     *  3. Uloží do [pendingPhotoBytes] – na disk se NIC nepíše.
     *  4. Zobrazí náhled v [binding.contactPhoto].
     */
    private fun handlePickedPhoto(uri: Uri) {
        ensureBackgroundThread {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }

            if (bytes == null || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
                runOnUiThread { toast(cz.mts.base.R.string.unknown_error_occurred) }
                return@ensureBackgroundThread
            }

            val resized = VcfImporter.resizePhotoIfNeeded(bytes, MAX_PHOTO_BYTES_LOCAL)
            pendingPhotoBytes = resized

            runOnUiThread {
                Glide.with(this)
                    .load(resized)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .into(binding.contactPhoto)
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Uložení
    // ─────────────────────────────────────────────────────────────────────────

    private fun maybeShowUnsavedChangesDialog(discard: () -> Unit) {
        if (iContactSource != 1) {
            discard()
            return
        }
        val hasChanges = contact != null && contact != fillContactValues()
        val promptCooldownExpired = System.currentTimeMillis() - mLastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL
        if (promptCooldownExpired && hasChanges) {
            mLastSavePromptTS = System.currentTimeMillis()
            ConfirmationAdvancedDialog(
                activity = this,
                message = "",
                messageId = cz.mts.base.R.string.save_before_closing,
                positive = cz.mts.base.R.string.save,
                negative = cz.mts.base.R.string.discard
            ) { shouldSave ->
                if (shouldSave) saveContact() else discard()
            }
        } else {
            discard()
        }
    }

    private fun saveContact() {
        if (isSaving || contact == null) return

        val contactValues = fillContactValues()

        // Validace – alespoň jedno pole musí být vyplněno
        val textFields = listOf(
            contactValues.prefix, contactValues.firstName, contactValues.middleName,
            contactValues.surname, contactValues.suffix, contactValues.nickname,
            contactValues.notes, contactValues.organization.company,
            contactValues.organization.jobPosition
        )
        if (textFields.all { it.isEmpty() } &&
            contactValues.phoneNumbers.isEmpty() &&
            contactValues.emails.isEmpty() &&
            contactValues.addresses.isEmpty() &&
            contactValues.IMs.isEmpty() &&
            contactValues.events.isEmpty() &&
            contactValues.websites.isEmpty()
        ) {
            toast(R.string.fields_empty)
            return
        }

        contact = contactValues
        isSaving = true

        ensureBackgroundThread {
            // ── Fyzické uložení fotky na disk (pouze pokud uživatel vybral nový obrázek) ──
            val newPhotoUri: String = if (pendingPhotoBytes != null) {
                val saved = LocalContactPhotoStorage.save(this, pendingPhotoBytes!!)
                if (saved.isNotEmpty()) {
                    // Starý soubor smažeme až teď – kontakt se uloží vzápětí
                    LocalContactPhotoStorage.delete(this, originalPhotoUri)
                }
                saved
            } else {
                // Foto se nezměnilo – zachováme původní URI
                contact!!.photoUri
            }

            // Aktualizujeme contact s finálním URI
            contact = contact!!.copy(photoUri = newPhotoUri)

            val success = try {
                LocalContactsHelper(this).insertOrUpdateContact(contact!!)
            } catch (e: Exception) {
                showErrorToast(e)
                false
            }
            runOnUiThread {
                isSaving = false
                if (success) {
                    setResult(Activity.RESULT_OK)
                    hideKeyboard()
                    finish()
                } else {
                    toast(cz.mts.base.R.string.unknown_error_occurred)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sběr hodnot z formuláře → Contact objekt
    // ─────────────────────────────────────────────────────────────────────────

    private fun fillContactValues() = contact!!.copy(
        prefix = binding.contactPrefix.value,
        firstName = binding.contactFirstName.value,
        middleName = binding.contactMiddleName.value,
        surname = binding.contactSurname.value,
        suffix = binding.contactSuffix.value,
        nickname = binding.contactNickname.value,
        // Pro účely detekce změn: pokud čeká nový obrázek, použijeme sentinel,
        // jinak zachováme původní URI. Fyzické uložení probíhá až v saveContact().
        photoUri = if (pendingPhotoBytes != null) "pending_new_photo" else contact!!.photoUri,
        phoneNumbers = getFilledPhoneNumbers(),
        emails = getFilledEmails(),
        addresses = getFilledAddresses(),
        IMs = getFilledIMs(),
        events = getFilledEvents(),
        starred = if (isContactStarred()) 1 else 0,
        notes = binding.contactNotes.value,
        websites = getFilledWebsites()
    ).also { newContact ->
        newContact.organization = Organization(
            binding.contactOrganizationCompany.value,
            binding.contactOrganizationJobPosition.value
        )
    }

    private fun getFilledPhoneNumbers(): ArrayList<PhoneNumber> {
        val numbers = ArrayList<PhoneNumber>()
        val count = binding.contactNumbersHolder.childCount
        for (i in 0 until count) {
            val h = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i))
            val value = h.contactNumber.value
            if (value.isNotEmpty()) {
                val type = phoneNumberTypeId(h.contactNumberType.value)
                val label = if (type == Phone.TYPE_CUSTOM) h.contactNumberType.value else ""
                val normalizedNumber = h.contactNumber.tag?.toString()?.ifEmpty { value } ?: value
                numbers.add(PhoneNumber(value, type, label, normalizedNumber, h.defaultToggleIcon.tag == 1))
            }
        }
        return numbers
    }

    private fun getFilledEmails(): ArrayList<Email> {
        val emails = ArrayList<Email>()
        val count = binding.contactEmailsHolder.childCount
        for (i in 0 until count) {
            val h = ItemEditEmailBinding.bind(binding.contactEmailsHolder.getChildAt(i))
            val value = h.contactEmail.value
            if (value.isNotEmpty()) {
                val type = emailTypeId(h.contactEmailType.value)
                val label = if (type == CommonDataKinds.Email.TYPE_CUSTOM) h.contactEmailType.value else ""
                emails.add(Email(value, type, label))
            }
        }
        return emails
    }

    private fun getFilledAddresses(): ArrayList<Address> {
        val addresses = ArrayList<Address>()
        val count = binding.contactAddressesHolder.childCount
        for (i in 0 until count) {
            val h = ItemEditStructuredAddressBinding.bind(binding.contactAddressesHolder.getChildAt(i))
            val street = h.contactStreet.value
            val neighborhood = h.contactNeighborhood.value
            val city = h.contactCity.value
            val postcode = h.contactPostcode.value
            val pobox = h.contactPobox.value
            val region = h.contactRegion.value
            val country = h.contactCountry.value

            // Sestavení lidsky čitelné adresy (stejná logika jako v EditContactActivity / DAVdroid)
            val lineStreet = arrayOf(street, pobox, neighborhood).filter { it.isNotEmpty() }.joinToString(" ")
            val lineLocality = arrayOf(postcode, city).filter { it.isNotEmpty() }.joinToString(" ")
            val lines = LinkedList<String>()
            if (lineStreet.isNotEmpty()) lines += lineStreet
            if (lineLocality.isNotEmpty()) lines += lineLocality
            if (region.isNotEmpty()) lines += region
            if (country.isNotEmpty()) lines += country.uppercase(Locale.getDefault())
            val fullAddress = lines.joinToString("\n")

            if (fullAddress.isNotEmpty()) {
                val type = addressTypeId(h.contactStructuredAddressType.value)
                val label = if (type == StructuredPostal.TYPE_CUSTOM) h.contactStructuredAddressType.value else ""
                addresses.add(Address(fullAddress, type, label, country, region, city, postcode, pobox, street, neighborhood))
            }
        }
        return addresses
    }

    private fun getFilledIMs(): ArrayList<IM> {
        val ims = ArrayList<IM>()
        val count = binding.contactImsHolder.childCount
        for (i in 0 until count) {
            val h = ItemEditImBinding.bind(binding.contactImsHolder.getChildAt(i))
            val value = h.contactIm.value
            if (value.isNotEmpty()) {
                val type = imTypeId(h.contactImType.value)
                val label = if (type == Im.PROTOCOL_CUSTOM) h.contactImType.value else ""
                ims.add(IM(value, type, label))
            }
        }
        return ims
    }

    private fun getFilledEvents(): ArrayList<Event> {
        val unknown = getString(cz.mts.base.R.string.unknown)
        val events = ArrayList<Event>()
        val count = binding.contactEventsHolder.childCount
        for (i in 0 until count) {
            val h = ItemEventBinding.bind(binding.contactEventsHolder.getChildAt(i))
            val displayed = h.contactEvent.value
            // Událost ukládáme pouze pokud bylo vybráno datum (tag obsahuje raw date string)
            val rawDate = h.contactEvent.tag?.toString() ?: ""
            if (displayed.isNotEmpty() && displayed != unknown && rawDate.isNotEmpty()) {
                val type = eventTypeId(h.contactEventType.value)
                events.add(Event(rawDate, type))
            }
        }
        return events
    }

    /**
     * Website – pouze jedna. Načteme z prvního (a jediného) pole v holderu.
     */
    private fun getFilledWebsites(): ArrayList<String> {
        val websites = ArrayList<String>()
        if (binding.contactWebsitesHolder.childCount > 0) {
            val h = ItemEditWebsiteBinding.bind(binding.contactWebsitesHolder.getChildAt(0))
            val value = h.contactWebsite.value
            if (value.isNotEmpty()) websites.add(value)
        }
        return websites
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pomocné funkce: převod textového popisku ↔ integer konstanty
    // ─────────────────────────────────────────────────────────────────────────

    // --- Telefon ---

    private fun phoneNumberTypeId(value: String) = when (value) {
        getString(cz.mts.base.R.string.mobile) -> Phone.TYPE_MOBILE
        getString(cz.mts.base.R.string.home) -> Phone.TYPE_HOME
        getString(cz.mts.base.R.string.work) -> Phone.TYPE_WORK
        getString(cz.mts.base.R.string.main_number) -> Phone.TYPE_MAIN
        getString(cz.mts.base.R.string.work_fax) -> Phone.TYPE_FAX_WORK
        getString(cz.mts.base.R.string.home_fax) -> Phone.TYPE_FAX_HOME
        getString(cz.mts.base.R.string.pager) -> Phone.TYPE_PAGER
        getString(cz.mts.base.R.string.other) -> Phone.TYPE_OTHER
        else -> Phone.TYPE_CUSTOM
    }

    private fun phoneNumberTypeText(type: Int, label: String) = when (type) {
        Phone.TYPE_MOBILE -> getString(cz.mts.base.R.string.mobile)
        Phone.TYPE_HOME -> getString(cz.mts.base.R.string.home)
        Phone.TYPE_WORK -> getString(cz.mts.base.R.string.work)
        Phone.TYPE_MAIN -> getString(cz.mts.base.R.string.main_number)
        Phone.TYPE_FAX_WORK -> getString(cz.mts.base.R.string.work_fax)
        Phone.TYPE_FAX_HOME -> getString(cz.mts.base.R.string.home_fax)
        Phone.TYPE_PAGER -> getString(cz.mts.base.R.string.pager)
        Phone.TYPE_CUSTOM -> label.ifEmpty { getString(cz.mts.base.R.string.custom) }
        else -> getString(cz.mts.base.R.string.other)
    }

    // --- E-mail ---

    private fun emailTypeId(value: String) = when (value) {
        getString(cz.mts.base.R.string.home) -> CommonDataKinds.Email.TYPE_HOME
        getString(cz.mts.base.R.string.work) -> CommonDataKinds.Email.TYPE_WORK
        getString(cz.mts.base.R.string.mobile) -> CommonDataKinds.Email.TYPE_MOBILE
        getString(cz.mts.base.R.string.other) -> CommonDataKinds.Email.TYPE_OTHER
        else -> CommonDataKinds.Email.TYPE_CUSTOM
    }

    private fun emailTypeText(type: Int, label: String) = when (type) {
        CommonDataKinds.Email.TYPE_HOME -> getString(cz.mts.base.R.string.home)
        CommonDataKinds.Email.TYPE_WORK -> getString(cz.mts.base.R.string.work)
        CommonDataKinds.Email.TYPE_MOBILE -> getString(cz.mts.base.R.string.mobile)
        CommonDataKinds.Email.TYPE_CUSTOM -> label.ifEmpty { getString(cz.mts.base.R.string.custom) }
        else -> getString(cz.mts.base.R.string.other)
    }

    // --- Adresa ---

    private fun addressTypeId(value: String) = when (value) {
        getString(cz.mts.base.R.string.home) -> StructuredPostal.TYPE_HOME
        getString(cz.mts.base.R.string.work) -> StructuredPostal.TYPE_WORK
        getString(cz.mts.base.R.string.other) -> StructuredPostal.TYPE_OTHER
        else -> StructuredPostal.TYPE_CUSTOM
    }

    private fun addressTypeText(type: Int, label: String) = when (type) {
        StructuredPostal.TYPE_HOME -> getString(cz.mts.base.R.string.home)
        StructuredPostal.TYPE_WORK -> getString(cz.mts.base.R.string.work)
        StructuredPostal.TYPE_CUSTOM -> label.ifEmpty { getString(cz.mts.base.R.string.custom) }
        else -> getString(cz.mts.base.R.string.other)
    }

    // --- IM ---

    private fun imTypeId(value: String) = when (value) {
        getString(R.string.aim) -> Im.PROTOCOL_AIM
        getString(R.string.windows_live) -> Im.PROTOCOL_MSN
        getString(R.string.yahoo) -> Im.PROTOCOL_YAHOO
        getString(R.string.skype) -> Im.PROTOCOL_SKYPE
        getString(R.string.qq) -> Im.PROTOCOL_QQ
        getString(R.string.hangouts) -> Im.PROTOCOL_GOOGLE_TALK
        getString(R.string.icq) -> Im.PROTOCOL_ICQ
        getString(R.string.jabber) -> Im.PROTOCOL_JABBER
        else -> Im.PROTOCOL_CUSTOM
    }

    private fun imTypeText(type: Int, label: String) = when (type) {
        Im.PROTOCOL_AIM -> getString(R.string.aim)
        Im.PROTOCOL_MSN -> getString(R.string.windows_live)
        Im.PROTOCOL_YAHOO -> getString(R.string.yahoo)
        Im.PROTOCOL_SKYPE -> getString(R.string.skype)
        Im.PROTOCOL_QQ -> getString(R.string.qq)
        Im.PROTOCOL_GOOGLE_TALK -> getString(R.string.hangouts)
        Im.PROTOCOL_ICQ -> getString(R.string.icq)
        Im.PROTOCOL_JABBER -> getString(R.string.jabber)
        Im.PROTOCOL_CUSTOM -> label.ifEmpty { getString(cz.mts.base.R.string.custom) }
        else -> getString(cz.mts.base.R.string.other)
    }

    // --- Události ---

    private fun eventTypeId(value: String) = when (value) {
        getString(R.string.birthday) -> CommonDataKinds.Event.TYPE_BIRTHDAY
        getString(R.string.anniversary) -> CommonDataKinds.Event.TYPE_ANNIVERSARY
        else -> CommonDataKinds.Event.TYPE_OTHER
    }

    /** Vrací @StringRes ID pro zobrazení názvu typu události. */
    private fun eventTypeTextResId(type: Int) = when (type) {
        CommonDataKinds.Event.TYPE_BIRTHDAY -> R.string.birthday
        CommonDataKinds.Event.TYPE_ANNIVERSARY -> R.string.anniversary
        else -> cz.mts.base.R.string.other
    }

    private fun updateFavoriteIcon() {
        val isStarred = isContactStarred()

        binding.contactToggleFavorite.apply {
            setImageDrawable(getStarDrawable(isStarred))
            applyColorFilter(
                if (isStarred) getProperPrimaryColor()
                else getProperTextColor()
            )
        }
    }
}
