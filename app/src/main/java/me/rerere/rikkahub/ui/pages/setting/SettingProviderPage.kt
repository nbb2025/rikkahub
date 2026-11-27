package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEEPSEEK_PROVIDER
import me.rerere.rikkahub.data.datastore.DEEPSEEK_PROVIDER_ID
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.iagent.IagentMark
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun SettingProviderPage() {
    val nav = LocalNavController.current
    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_provider_page_title)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
    ) { padding ->
        CardGroup(Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            item(
                onClick = { nav.navigate(Screen.IagentCatalog) },
                leadingContent = { IagentMark(size = 32) },
                supportingContent = { Text(stringResource(R.string.iagent_catalog_hint)) },
                trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.iagent_app_name)) },
            )
            item(
                onClick = { nav.navigate(Screen.SettingProviderDetail(DEEPSEEK_PROVIDER_ID.toString())) },
                leadingContent = { AutoAIIcon(DEEPSEEK_PROVIDER.name, modifier = Modifier.size(32.dp)) },
                supportingContent = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                headlineContent = { Text(DEEPSEEK_PROVIDER.name) },
            )
        }
    }
}
