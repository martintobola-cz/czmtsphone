package cz.mts.phone.activities

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import com.google.gson.Gson
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.models.contacts.Contact
import cz.mts.base.views.MyRecyclerView
import cz.mts.phone.R
import cz.mts.phone.adapters.SelectNumbersAdapter
import cz.mts.phone.adapters.SpeedDialAdapter
import cz.mts.phone.databinding.ActivityManageSpeedDialBinding
import cz.mts.phone.dialogs.SelectContactDialog
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.interfaces.RemoveSpeedDialListener
import cz.mts.phone.models.PhonePickerItem
import cz.mts.phone.models.PhoneTypeUi
import cz.mts.base.models.SpeedDial

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    override var customNavBarLightIcons: Boolean? = null

    private val binding by viewBinding(ActivityManageSpeedDialBinding::inflate)

    private var allContacts = mutableListOf<Contact>()
    private var speedDialValues = mutableListOf<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(manageSpeedDialScrollview))
            setupMaterialScrollListener(manageSpeedDialScrollview, manageSpeedDialAppbar)
        }

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            // getContacts callback – přepneme na main thread kvůli thread-safety mutableListOf
            runOnUiThread {
                allContacts.addAll(contacts)
                allContacts.sort()
            }
        }

        updateTextColors(binding.manageSpeedDialScrollview)
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(binding.manageSpeedDialAppbar, NavigationIcon.Arrow)
    }

    override fun onPause() {
        super.onPause()
        saveSpeedDial()
    }

    private fun saveSpeedDial() {
        config.speedDial = Gson().toJson(speedDialValues)
    }


    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) { any ->
            val clickedSpeedDial = any as SpeedDial
            if (allContacts.isEmpty()) return@SpeedDialAdapter

            SelectContactDialog(this, allContacts) { selectedContact ->
                if (selectedContact.phoneNumbers.size > 1) {
                    showNumberPickerDialog(clickedSpeedDial, selectedContact)
                } else {
                    assignSingleNumber(clickedSpeedDial, selectedContact)
                }
            }
        }.apply {
            binding.speedDialList.adapter = this
        }
    }

    private fun showNumberPickerDialog(clickedSpeedDial: SpeedDial, selectedContact: Contact) {
        val items = buildPhonePickerItems(selectedContact)

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_numbers, null)
        view.findViewById<MyRecyclerView>(R.id.mtsRecyclerView).apply {
            setHasFixedSize(true)
            adapter = SelectNumbersAdapter(
                items,
                allowMultipleNumbersPerContact = false
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = items
                    .filterIsInstance<PhonePickerItem.Number>()
                    .firstOrNull { it.isSelected }

                selected?.let { numberItem ->
                    assignNumberFromPicker(clickedSpeedDial, selectedContact, numberItem)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()   // ← create, ne show

        // Nastavení pozadí a barev před zobrazením
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 64f
                setColor(getProperBackgroundColor())
            }
        )

        dialog.show()

        // Tlačítka jsou dostupná až po show()
        val primaryColor = getProperPrimaryColor()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(primaryColor)
            setTypeface(typeface, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(primaryColor)
            setTypeface(typeface, Typeface.BOLD)
        }
    }

      private fun assignSingleNumber(speedDial: SpeedDial, contact: Contact) {
        speedDialValues.firstOrNull { it.id == speedDial.id }?.apply {
            displayName = contact.getNameToDisplay()
            number = contact.phoneNumbers.first().value
        }
        updateAdapter()
    }

    private fun assignNumberFromPicker(
        speedDial: SpeedDial,
        contact: Contact,
        numberItem: PhonePickerItem.Number
    ) {
        speedDialValues.firstOrNull { it.id == speedDial.id }?.apply {
            displayName = contact.getNameToDisplay()
            number = numberItem.phone.value
            type = numberItem.phone.type
            label = numberItem.phone.label
        }
        updateAdapter()
    }


    /**
     * Sestaví seznam položek pro dialog výběru čísla.
     * Poznámka: tato metoda se volá jen při size > 1,
     */
    private fun buildPhonePickerItems(contact: Contact): List<PhonePickerItem> {
        val items = mutableListOf<PhonePickerItem>()

        items += PhonePickerItem.Header(
            contactId = contact.id,
            contactName = contact.getNameToDisplay()
        )

        var primaryAlreadyUsed = false

        contact.phoneNumbers.forEach { phone ->
            val isSelected = phone.isPrimary && !primaryAlreadyUsed
            if (isSelected) primaryAlreadyUsed = true

            items += PhonePickerItem.Number(
                contactId = contact.id,
                contactName = contact.getNameToDisplay(),
                phone = phone,
                type = PhoneTypeUi(
                    rawType = phone.type,
                    label = getPhoneNumberTypeText(phone.type, " ")
                ),
                isSelected = isSelected
            )
        }

        return items
    }


    override fun removeSpeedDial(ids: ArrayList<Int>) {
        ids.forEach { dialId ->
            speedDialValues.firstOrNull { it.id == dialId }?.apply {
                displayName = ""
                number = ""
                type = null
                label = null
            }
        }
        updateAdapter()
    }
}
