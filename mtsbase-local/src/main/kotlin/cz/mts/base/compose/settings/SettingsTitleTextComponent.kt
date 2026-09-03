package cz.mts.base.compose.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import cz.mts.base.compose.extensions.MyDevices
import cz.mts.base.compose.theme.AppThemeSurface
import cz.mts.base.compose.theme.SimpleTheme

@Composable
fun SettingsTitleTextComponent(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = SimpleTheme.colorScheme.primary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Box(modifier = Modifier.padding(top = SimpleTheme.dimens.padding.large)) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = 14.sp,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@MyDevices
@Composable
private fun SettingsTitleTextComponentPreview() = AppThemeSurface {
    SettingsTitleTextComponent(text = "Color customization")
}
