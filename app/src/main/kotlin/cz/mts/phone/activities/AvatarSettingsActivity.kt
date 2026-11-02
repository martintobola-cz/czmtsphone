package cz.mts.phone.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import cz.mts.base.extensions.*
import cz.mts.base.helpers.NavigationIcon
import cz.mts.base.views.MyAppCompatCheckbox
import cz.mts.phone.R
import cz.mts.phone.databinding.ActivityAvatarSettingsBinding
import cz.mts.phone.helpers.AvatarSource
import cz.mts.phone.helpers.AvatarSource.Kind

class AvatarSettingsActivity : SimpleActivity() {

    override var customNavBarLightIcons: Boolean? = null

    private val b by viewBinding(ActivityAvatarSettingsBinding::inflate)
    private var pendingKind: Kind? = null


        private class Column(
        val defImg: ImageView, val defCb: MyAppCompatCheckbox,
        val cusImg: ImageView, val cusCb: MyAppCompatCheckbox, val remove: ImageView
    )


    private lateinit var columns: Map<Kind, Column>

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val kind = pendingKind ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        if (AvatarSource.saveCustom(this, kind, uri)) {
            AvatarSource.setUseCustom(this, kind, true)
        } else {
            AvatarSource.resetToDefault(this, kind)   // smaže soubor + přepne na výchozí
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)

        setupEdgeToEdge(padBottomSystem = listOf(b.avatarSettingsContent))

        columns = mapOf(
            Kind.FAKE to Column(b.ivDefaultFake, b.cbDefaultFake, b.ivCustomFake, b.cbCustomFake, b.btnRemoveFake),
            Kind.BLOCK to Column(b.ivDefaultBlock, b.cbDefaultBlock, b.ivCustomBlock, b.cbCustomBlock, b.btnRemoveBlock)
        )

        columns.forEach { (kind, c) ->
            c.defCb.setOnClickListener { AvatarSource.setUseCustom(this, kind, false); refresh() }
            c.cusCb.setOnClickListener { AvatarSource.setUseCustom(this, kind, true); refresh() }
            c.defImg.setOnClickListener { c.defCb.performClick() }

            c.cusImg.setOnClickListener {
                pendingKind = kind
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            c.remove.setOnClickListener { AvatarSource.resetToDefault(this, kind); refresh() }
        }
        refresh()
    }

    override fun onResume() {
        customNavBarLightIcons = shouldUseLightIcons(getProperBackgroundColor())
        super.onResume()
        setupTopAppBar(
            topAppBar = b.avatarSettingsAppbar,
            navigationIcon = NavigationIcon.Arrow,
            onNavigationClick = { finish() }
        )
        updateTextColors(b.avatarSettingsCoordinator)
        refresh()

        columns.values.forEach { c ->
            c.remove.apply {
                applyColorFilter(getProperPrimaryColor())
                background?.applyColorFilter(getProperTextColor())
            }
        }
    }

    private fun refresh() {
        columns.forEach { (kind, c) ->
            val customDrawable = AvatarSource.customDrawable(this, kind)
            if (customDrawable == null && AvatarSource.hasCustomFile(this, kind)) {
                AvatarSource.resetToDefault(this, kind)   // soubor nejde dekódovat
            }
            val hasFile = AvatarSource.hasCustomFile(this, kind)
            val custom = AvatarSource.isCustomActive(this, kind)

            c.defImg.setImageDrawable(AvatarSource.defaultDrawable(this, kind))
            if (customDrawable != null) {
                c.cusImg.apply {
                    clearColorFilter()
                    background?.clearColorFilter()
                    setImageDrawable(customDrawable)
                }
            } else {
                c.cusImg.apply {
                    setImageResource(R.drawable.ic_plus_vector)
                    applyColorFilter(getProperPrimaryColor())
                    background?.applyColorFilter(getProperTextColor())
                }
            }

            c.defCb.isChecked = !custom
            c.cusCb.isChecked = custom
            c.cusCb.isEnabled = hasFile
            c.remove.visibility = if (hasFile) View.VISIBLE else View.INVISIBLE
        }
    }
}
