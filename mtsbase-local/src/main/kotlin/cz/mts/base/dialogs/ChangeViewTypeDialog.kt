package cz.mts.base.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import cz.mts.base.R
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.compose.alert_dialog.AlertDialogState
import cz.mts.base.compose.alert_dialog.DialogSurface
import cz.mts.base.compose.alert_dialog.rememberAlertDialogState
import cz.mts.base.compose.components.RadioGroupDialogComponent
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme
import cz.mts.base.databinding.DialogChangeViewTypeBinding
import cz.mts.base.extensions.baseConfig
import cz.mts.base.extensions.getAlertDialogBuilder
import cz.mts.base.extensions.setupDialogStuff
import cz.mts.base.helpers.VIEW_TYPE_GRID
import cz.mts.base.helpers.VIEW_TYPE_LIST
import androidx.appcompat.app.AlertDialog

class ChangeViewTypeDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    private var view: DialogChangeViewTypeBinding
    private var config = activity.baseConfig
    private var dialog: AlertDialog? = null

    init {
        view = DialogChangeViewTypeBinding.inflate(activity.layoutInflater, null, false).apply {
            val viewToCheck = when (config.viewType) {
                VIEW_TYPE_GRID -> changeViewTypeDialogRadioGrid.id
                else -> changeViewTypeDialogRadioList.id
            }

            changeViewTypeDialogRadio.check(viewToCheck)

            changeViewTypeDialogRadio.setOnCheckedChangeListener { _, checkedId ->
                config.viewType = if (checkedId == changeViewTypeDialogRadioGrid.id) {
                    VIEW_TYPE_GRID
                } else {
                    VIEW_TYPE_LIST
                }
                callback()
                dialog?.dismiss()
            }
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(view.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}

@Immutable
data class ViewType(val title: String, val type: Int)

@Composable
fun ChangeViewTypeAlertDialog(
    alertDialogState: AlertDialogState,
    selectedViewType: Int,
    modifier: Modifier = Modifier,
    onTypeChosen: (type: Int) -> Unit
) {
    val context = LocalContext.current
    val items = remember {
        listOf(
            ViewType(title = context.getString(R.string.grid), type = VIEW_TYPE_GRID),
            ViewType(title = context.getString(R.string.list), type = VIEW_TYPE_LIST)
        ).toImmutableList()
    }

    val groupTitles by remember {
        derivedStateOf { items.map { it.title } }
    }
    val (selected, setSelected) = remember { mutableStateOf(items.firstOrNull { it.type == selectedViewType }?.title) }
    BasicAlertDialog(onDismissRequest = alertDialogState::hide) {
        DialogSurface {
            Column(
                modifier = modifier
                    .padding(bottom = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                RadioGroupDialogComponent(
                    items = groupTitles,
                    selected = selected,
                    setSelected = { selectedTitle ->
                        setSelected(selectedTitle)
                    },
                    modifier = Modifier.padding(
                        vertical = SimpleTheme.dimens.padding.extraLarge,
                    ),
                    verticalPadding = SimpleTheme.dimens.padding.extraLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = SimpleTheme.dimens.padding.extraLarge)
                ) {
                    TextButton(onClick = {
                        alertDialogState.hide()
                    }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }

                    TextButton(onClick = {
                        alertDialogState.hide()
                        onTypeChosen(getSelectedValue(items, selected))
                    }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}

private fun getSelectedValue(
    items: ImmutableList<ViewType>,
    selected: String?
) = items.first { it.title == selected }.type

@MyDevices
@Composable
private fun ChangeViewTypeAlertDialogPreview() {
    AppThemeSurface {
        ChangeViewTypeAlertDialog(alertDialogState = rememberAlertDialogState(), selectedViewType = VIEW_TYPE_GRID) {}
    }
}
