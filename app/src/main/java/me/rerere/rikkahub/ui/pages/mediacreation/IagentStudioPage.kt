package me.rerere.rikkahub.ui.pages.mediacreation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.data.repository.MediaCreationRepository
import me.rerere.rikkahub.ui.pages.setting.components.label
import org.koin.compose.koinInject

@Composable
internal fun IagentStudioPage() {
    val repo = koinInject<MediaCreationRepository>()
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(repo) {
        val current = sessionId
        if (current == null || repo.getSession(kotlin.uuid.Uuid.parse(current)) == null) {
            sessionId = (repo.getLatestSession() ?: repo.newSession()).id.toString()
        }
    }
    val current = sessionId
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    } else {
        MediaCreationPage(current, workspace = true)
    }
}

@Composable
internal fun StudioModeSelector(kind: MediaKind, onSelect: (MediaKind) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MediaKind.entries.forEach { option ->
                val selected = option == kind
                val selectionShape = RoundedCornerShape(14.dp)
                val icon = if (option == MediaKind.IMAGE) HugeIcons.Image02 else HugeIcons.Video01
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .clip(selectionShape).selectable(selected, role = Role.Tab) {
                            if (!selected) {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onSelect(option)
                            }
                        },
                    shape = selectionShape,
                    color = if (selected) colors.surface else colors.surfaceContainerHigh,
                    contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, Modifier.size(19.dp))
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
