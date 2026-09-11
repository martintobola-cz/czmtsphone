package cz.mts.base.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.mts.base.R
import cz.mts.base.compose.alert_dialog.*
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface

@Composable
fun PermissionRequiredAlertDialog(
    alertDialogState: AlertDialogState,
    text: String,
    modifier: Modifier = Modifier,
    negativeActionCallback: (() -> Unit)? = null,
    positiveActionCallback: () -> Unit
) {
    AlertDialog(
        containerColor = dialogContainerColor,
        modifier = modifier
            .dialogBorder,
        onDismissRequest = alertDialogState::hide,
        shape = dialogShape,
        tonalElevation = dialogElevation,
        dismissButton = {
            TextButton(onClick = {
                alertDialogState.hide()
                negativeActionCallback?.invoke()
            }) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alertDialogState.hide()
                positiveActionCallback()
            }) {
                Text(text = stringResource(id = R.string.grant_permission))
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.permission_required),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                fontSize = 16.sp,
                text = text
            )
        }
    )
}

@Composable
@MyDevices
private fun PermissionRequiredAlertDialogPreview() {
    AppThemeSurface {
        PermissionRequiredAlertDialog(
            alertDialogState = rememberAlertDialogState(),
            text = "Test",
            negativeActionCallback = {}
        ) {}
    }
}
