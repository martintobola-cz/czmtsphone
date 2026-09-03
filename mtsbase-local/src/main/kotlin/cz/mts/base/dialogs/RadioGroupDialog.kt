package cz.mts.base.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import cz.mts.base.R
import cz.mts.base.compose.alert_dialog.AlertDialogState
import cz.mts.base.compose.alert_dialog.DialogSurface
import cz.mts.base.compose.alert_dialog.dialogTextColor
import cz.mts.base.compose.alert_dialog.rememberAlertDialogState
import cz.mts.base.compose.components.RadioGroupDialogComponent
import cz.mts.base.compose.extensions.BooleanPreviewParameterProvider
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme
import cz.mts.base.databinding.DialogRadioGroupBinding
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.onGlobalLayout
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.models.RadioItem

class RadioGroupDialog(
    private val activity: Activity,
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
     *     itemTextSize = { item ->
     *         when (item.id) {
     *             FONT_SIZE_SMALL       -> resources.getDimension(R.dimen.smaller_text_size)  / resources.displayMetrics.scaledDensity
     *             FONT_SIZE_MEDIUM      -> resources.getDimension(R.dimen.bigger_text_size)   / resources.displayMetrics.scaledDensity
     *             FONT_SIZE_LARGE       -> resources.getDimension(R.dimen.big_text_size)      / resources.displayMetrics.scaledDensity
     *             else                  -> resources.getDimension(R.dimen.extra_big_text_size) / resources.displayMetrics.scaledDensity
     *         }
     *     }
     * ) { config.fontSize = it as Int }
     * ```
     */
    private val itemTextSize: ((RadioItem) -> Float)? = null,
    private val callback: (newValue: Any) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var wasInit = false
    private var selectedItemId = -1

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

                    setOnClickListener { itemSelected(i) }
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

        val builder = activity.getAlertDialogBuilder()
            .setOnCancelListener { cancelCallback?.invoke() }

        if (selectedItemId != -1 && showOKButton) {
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

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog?.dismiss()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioGroupAlertDialog(
    alertDialogState: AlertDialogState,
    items: ImmutableList<RadioItem>,
    modifier: Modifier = Modifier,
    selectedItemId: Int = -1,
    titleId: Int = 0,
    showOKButton: Boolean = false,
    cancelCallback: (() -> Unit)? = null,
    callback: (newValue: Any) -> Unit
) {
    val groupTitles by remember {
        derivedStateOf { items.map { it.title } }
    }
    val (selected, setSelected) = remember {
        mutableStateOf(items.firstOrNull { it.id == selectedItemId }?.title)
    }
    val shouldShowOkButton = selectedItemId != -1 && showOKButton

    BasicAlertDialog(
        onDismissRequest = {
            cancelCallback?.invoke()
            alertDialogState.hide()
        }
    ) {
        DialogSurface {
            Box {
                Column(
                    modifier = modifier
                        .padding(bottom = if (shouldShowOkButton) 64.dp else 18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (titleId != 0) {
                        Text(
                            text = stringResource(id = titleId),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = SimpleTheme.dimens.padding.medium)
                                .padding(horizontal = 24.dp),
                            color = dialogTextColor,
                            fontSize = 21.sp
                        )
                    }
                    RadioGroupDialogComponent(
                        items = groupTitles,
                        selected = selected,
                        setSelected = { selectedTitle ->
                            setSelected(selectedTitle)
                            callback(getSelectedValue(items, selectedTitle))
                            alertDialogState.hide()
                        },
                        modifier = Modifier.padding(
                            vertical = SimpleTheme.dimens.padding.extraLarge,
                        )
                    )
                }
                if (shouldShowOkButton) {
                    TextButton(
                        onClick = {
                            callback(getSelectedValue(items, selected))
                            alertDialogState.hide()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                top = SimpleTheme.dimens.padding.extraLarge,
                                bottom = SimpleTheme.dimens.padding.extraLarge,
                                end = SimpleTheme.dimens.padding.extraLarge
                            )
                    ) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}

private fun getSelectedValue(
    items: ImmutableList<RadioItem>,
    selected: String?
) = items.first { it.title == selected }.value

@Composable
@MyDevices
private fun RadioGroupDialogAlertDialogPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) showOKButton: Boolean
) {
    AppThemeSurface {
        RadioGroupAlertDialog(
            alertDialogState = rememberAlertDialogState(),
            items = listOf(
                RadioItem(1, "Test"),
                RadioItem(2, "Test 2"),
                RadioItem(3, "Test 3"),
            ).toImmutableList(),
            selectedItemId = 1,
            titleId = R.string.title,
            showOKButton = showOKButton,
            cancelCallback = {}
        ) {}
    }
}
