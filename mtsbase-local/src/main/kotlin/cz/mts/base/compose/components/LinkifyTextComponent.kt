package cz.mts.base.compose.components

import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cz.mts.base.R
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme
import cz.mts.base.extensions.fromHtml
import cz.mts.base.extensions.removeUnderlines

@Composable
fun LinkifyTextComponent(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    removeUnderlines: Boolean = true,
    textAlignment: Int = TextView.TEXT_ALIGNMENT_TEXT_START,
    text: () -> Spanned
) {
    val context = LocalContext.current
    val customLinkifyTextView = remember {
        TextView(context)
    }
    val textColor = SimpleTheme.colorScheme.onSurface
    val linkTextColor = SimpleTheme.colorScheme.primary
    AndroidView(modifier = modifier, factory = { customLinkifyTextView }) { textView ->
        textView.setTextColor(textColor.toArgb())
        textView.setLinkTextColor(linkTextColor.toArgb())
        textView.text = text()
        textView.textAlignment = textAlignment
        textView.textSize = fontSize.value
        textView.movementMethod = LinkMovementMethod.getInstance()
        if (removeUnderlines) {
            customLinkifyTextView.removeUnderlines()
        }
    }
}

@Composable
@MyDevices
private fun LinkifyTextComponentPreview() = AppThemeSurface {
    val source = ""
    LinkifyTextComponent {
        source.fromHtml()
    }
}
