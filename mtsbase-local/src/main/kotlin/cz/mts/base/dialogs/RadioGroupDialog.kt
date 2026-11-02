package cz.mts.base.dialogs

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.databinding.DialogRadioGroupBinding
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.extensions.styleDialogButtons
import cz.mts.base.models.RadioItem

class RadioGroupDialog(
    private val activity: BaseSimpleActivity,
    private val items: ArrayList<RadioItem>,
    private val checkedItemId: Int = -1,
    private val titleId: Int = 0,
    showOKButton: Boolean = false,
    private val cancelCallback: (() -> Unit)? = null,
    /**
     * Volitelný callback pro nastavení velikosti textu jednotlivých položek.
     * Vrací požadovanou velikost v SP pro daný [RadioItem].
     *
     * Použití – náhled velikosti písma v setupFontSize():
     * ```
     * RadioGroupDialog(
     *     activity = this,
     *     items    = items,
     *     checkedItemId = config.fontSize,
     *     titleId = R.string.dialog_font_size_title,
     *     checkboxTextId = R.string.bold_dialog_buttons,
     *     checkboxChecked = config.boldDialogButtons,
     *     onCheckboxChanged = { config.boldDialogButtons = it },
     *     checkboxLivePreview = true,
     *     confirmButtons = true,
     *     itemTextSize = { item ->
     *         when (item.id) {
     *             FONT_SIZE_SMALL  -> pxToSp(resources.getDimension(R.dimen.smaller_text_size))
     *             FONT_SIZE_MEDIUM -> pxToSp(resources.getDimension(R.dimen.bigger_text_size))
     *             FONT_SIZE_LARGE  -> pxToSp(resources.getDimension(R.dimen.big_text_size))
     *             else             -> pxToSp(resources.getDimension(R.dimen.extra_big_text_size))
     *         }
     *     }
     * ) { config.fontSize = it as Int }
     * ```
     */
    private val itemTextSize: ((RadioItem) -> Float)? = null,
    /**
     * Volitelný checkbox pod seznamem položek (odděleno čárou).
     * [checkboxTextId] = 0 znamená bez checkboxu.
     * Bez [confirmButtons] se [onCheckboxChanged] volá okamžitě při přepnutí
     * (klik na položku dialog hned zavře). S [confirmButtons] se volá až po „Uložit“,
     * pokud není zapnuto [checkboxLivePreview].
     */
    private val checkboxTextId: Int = 0,
    private val checkboxChecked: Boolean = false,
    private val onCheckboxChanged: ((Boolean) -> Unit)? = null,
    /**
     * true = při přepnutí checkboxu se hned zavolá [onCheckboxChanged] a znovu se
     * ostylují tlačítka dialogu (obarvení / tučnost). S [confirmButtons] se při „Zrušit“
     * (i zpětném gestu a kliku mimo dialog) vrátí původní hodnota [checkboxChecked].
     */
    private val checkboxLivePreview: Boolean = false,
    /**
     * true = tlačítka Uložit / Zrušit. Klik na položku ji jen vybere,
     * hodnoty (položka i checkbox) se předají až po „Uložit“.
     */
    private val confirmButtons: Boolean = false,
    private val callback: (newValue: Any) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var wasInit = false
    private var selectedItemId = -1
    private var checkboxValue = checkboxChecked

    init {
        val view = DialogRadioGroupBinding.inflate(activity.layoutInflater, null, false)
        view.dialogRadioGroup.apply {
            for (i in items.indices) {
                val item = items[i]
                val radioButton = (activity.layoutInflater.inflate(R.layout.radio_button, null) as RadioButton).apply {
                    text = item.title
                    isChecked = item.id == checkedItemId
                    id = i

                    // Nastav velikost textu pokud ji volající definoval
                    itemTextSize?.invoke(item)?.let { textSize = it }

                    setOnClickListener {
                        if (confirmButtons) {
                            selectedItemId = i
                        } else {
                            itemSelected(i)
                        }
                    }
                }

                if (item.id == checkedItemId) {
                    selectedItemId = i
                }

                addView(
                    radioButton,
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
        }

        // Volitelný checkbox s čárou nad ním
        if (checkboxTextId != 0) {
            view.dialogRadioExtraHolder.visibility = View.VISIBLE
            view.dialogRadioCheckbox.apply {
                setText(checkboxTextId)
                isChecked = checkboxChecked
                setOnCheckedChangeListener { _, checked ->
                    checkboxValue = checked
                    if (!confirmButtons || checkboxLivePreview) {
                        onCheckboxChanged?.invoke(checked)
                    }
                    if (checkboxLivePreview) {
                        dialog?.let { activity.styleDialogButtons(it, checkboxValue) }
                    }
                }
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setOnCancelListener {
                revertCheckbox()
                cancelCallback?.invoke()
            }

        if (confirmButtons) {
            builder.setPositiveButton(R.string.save) { _, _ ->
                if (checkboxTextId != 0 && !checkboxLivePreview) {
                    onCheckboxChanged?.invoke(checkboxValue)
                }
                if (selectedItemId != -1) {
                    itemSelected(selectedItemId)
                }
            }
            builder.setNegativeButton(R.string.cancel) { _, _ ->
                revertCheckbox()
                cancelCallback?.invoke()
            }
        } else if (selectedItemId != -1 && showOKButton) {
            builder.setPositiveButton(R.string.ok) { _, _ -> itemSelected(selectedItemId) }
        }

        builder.apply {
            activity.setupDialogStuff(view.root, this, titleId) { alertDialog ->
                dialog = alertDialog
            }
        }

        if (selectedItemId != -1) {
            view.dialogRadioHolder.apply {
                onGlobalLayout {
                    scrollY = view.dialogRadioGroup.findViewById<View>(selectedItemId).bottom - height
                }
            }
        }

        wasInit = true
    }

    /** Vrátí původní hodnotu checkboxu, pokud byla při živém náhledu změněna a dialog se zrušil. */
    private fun revertCheckbox() {
        if (confirmButtons && checkboxLivePreview && checkboxTextId != 0 && checkboxValue != checkboxChecked) {
            onCheckboxChanged?.invoke(checkboxChecked)
        }
    }

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog?.dismiss()
        }
    }
}
