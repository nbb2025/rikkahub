package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen

internal enum class IagentDestination(val label: Int, val icon: ImageVector) {
    CHAT(R.string.iagent_nav_chat, HugeIcons.Message01),
    STUDIO(R.string.iagent_nav_studio, HugeIcons.ImageToVideo),
    ACCOUNT(R.string.iagent_nav_account, HugeIcons.UserCircle);

    companion object {
        fun forScreen(screen: Any?): IagentDestination? = when (screen) {
            is Screen.Chat -> CHAT
            Screen.IagentStudio -> STUDIO
            Screen.IagentAccount -> ACCOUNT
            else -> null
        }
    }
}

@Composable
internal fun IagentWorkbench(
    active: IagentDestination?,
    onNavigate: (IagentDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val showNavigation = active != null &&
        WindowInsets.ime.getBottom(density) <= WindowInsets.navigationBars.getBottom(density)

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f)
                .then(if (showNavigation) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            content()
        }
        if (showNavigation && active != null) IagentNavigation(active, onNavigate)
    }
}

@Composable
private fun IagentNavigation(
    active: IagentDestination,
    onNavigate: (IagentDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IagentDestination.entries.forEach { destination ->
                    val selected = active == destination
                    val tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                    val shape = RoundedCornerShape(17.dp)
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clip(shape).selectable(selected, role = Role.Tab) { onNavigate(destination) },
                        shape = shape,
                        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                        contentColor = tint,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(destination.icon, contentDescription = null, Modifier.size(18.dp))
                            Text(
                                stringResource(destination.label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun IagentMark(modifier: Modifier = Modifier, size: Int = 44) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(size.dp),
        color = colors.surface,
        shape = RoundedCornerShape((size * .29f).dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Image(
            painterResource(R.drawable.iagent_brand_mark),
            contentDescription = null,
            modifier = Modifier.padding((size * .2f).dp),
        )
    }
}
