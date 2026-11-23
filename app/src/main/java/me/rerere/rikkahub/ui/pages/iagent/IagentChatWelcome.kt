package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IagentChatWelcome(
    onPromptStarter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(
        modifier = modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IagentMark(size = 54)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.iagent_chat_hero),
                style = type.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.iagent_chat_hero_hint),
                style = type.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Triple(HugeIcons.PencilEdit01, R.string.iagent_chat_action_write, R.string.iagent_chat_draft_write),
                Triple(HugeIcons.CheckList, R.string.iagent_chat_action_plan, R.string.iagent_chat_draft_plan),
                Triple(HugeIcons.Code, R.string.iagent_chat_action_code, R.string.iagent_chat_draft_code),
            ).forEach { (icon, label, prompt) ->
                val starter = stringResource(prompt)
                Surface(
                    onClick = { onPromptStarter(starter) },
                    shape = RoundedCornerShape(15.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(icon, null, Modifier.size(17.dp), tint = colors.primary)
                        Text(stringResource(label), style = type.labelLarge)
                    }
                }
            }
        }
    }
}
