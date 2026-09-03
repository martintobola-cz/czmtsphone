package cz.mts.base.compose.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.mts.base.compose.alert_dialog.dialogTextColor
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.extensions.rememberMutableInteractionSource
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme

@Composable
fun RadioButtonDialogComponent(
    modifier: Modifier = Modifier,
    setSelected: (selected: String) -> Unit,
    item: String,
    selected: String?
) {
    val interactionSource = rememberMutableInteractionSource()
    val indication = LocalIndication.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { setSelected(item) },
                interactionSource = interactionSource,
                indication = indication
            )
            .then(modifier)
    ) {
        RadioButton(
            selected = selected == item,
            onClick = null,
            enabled = true,
            colors = RadioButtonDefaults.colors(
                selectedColor = SimpleTheme.colorScheme.primary
            ),
        )
        Text(
            text = item,
            modifier = Modifier.padding(start = SimpleTheme.dimens.padding.medium),
            color = dialogTextColor
        )
    }
}

@Composable
@MyDevices
private fun RadioButtonDialogComponentPreview() {
    AppThemeSurface {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            RadioButtonDialogComponent(
                setSelected = {}, item = "item", selected = "item",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )
        }
    }
}
