package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.mediacreation.StudioModeSelector

@Composable
internal fun IagentGuestPage(studio: Boolean, onSignIn: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val nav = LocalNavController.current
    var kind by rememberSaveable { mutableStateOf(MediaKind.IMAGE) }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (studio) R.string.iagent_studio_title else R.string.iagent_app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = { onSignIn("") }) { Text(stringResource(R.string.iagent_login_heading)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                if (studio) {
                    StudioModeSelector(kind, onSelect = { kind = it })
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                painterResource(R.drawable.iagent_studio_example),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                stringResource(
                                    if (kind == MediaKind.IMAGE) R.string.iagent_guest_image_hint else R.string.iagent_guest_video_hint
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp),
                                style = type.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    IagentChatWelcome(onPromptStarter = onSignIn)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { onSignIn("") }) {
                        Text(stringResource(if (studio) R.string.iagent_guest_create else R.string.iagent_guest_chat))
                    }
                    TextButton(onClick = { nav.navigate(Screen.IagentCatalog) }) {
                        Text(stringResource(R.string.iagent_catalog_title))
                    }
                }
            }
        }
    }
}
