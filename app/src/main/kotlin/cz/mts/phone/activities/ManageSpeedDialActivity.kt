package cz.mts.phone.activities

import android.os.Bundle
import com.google.gson.Gson
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.shouldUseLightIcons
import cz.mts.base.extensions.updateTextColors
import cz.mts.base.extensions.viewBinding
import cz.mts.base.helpers.ContactsHelper
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.models.SpeedDial
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.adapters.SpeedDialAdapter
import cz.mts.phone.databinding.ActivityManageSpeedDialBinding
import cz.mts.phone.dialogs.SelectContactDialog
import cz.mts.phone.dialogs.SelectPhoneNumberDialog
import cz.mts.phone.interfaces.RemoveSpeedDialListener
import cz.mts.phone.models.PhonePickerItem
import cz.mts.base.extensions.baseConfig as config

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
                    SelectPhoneNumberDialog(this, selectedContact) { numberItem ->
                        assignNumberFromPicker(clickedSpeedDial, selectedContact, numberItem)
                    }
                } else {
                    assignSingleNumber(clickedSpeedDial, selectedContact)
                }
            }
        }.apply {
            binding.speedDialList.adapter = this
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
