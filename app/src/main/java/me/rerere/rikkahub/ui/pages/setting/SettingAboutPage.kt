package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IAGENT_ORIGIN
import me.rerere.rikkahub.data.iagent.IAGENT_QQ_GROUP
import me.rerere.rikkahub.data.iagent.IAGENT_REPOSITORY
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.pages.iagent.IagentMark
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.icons.TencentQQIcon

@Composable
fun SettingAboutPage() {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_page_title)) },
                navigationIcon = { BackButton() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("brand") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IagentMark(size = 66)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.iagent_app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${BuildConfig.VERSION_NAME} · Android",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item("links") {
                CardGroup {
                    item(
                        onClick = { context.openUrl(IAGENT_ORIGIN) },
                        leadingContent = { CardGroupIcon(HugeIcons.Earth) },
                        headlineContent = { Text(stringResource(R.string.about_page_website)) },
                        supportingContent = { Text(IAGENT_ORIGIN) },
                    )
                    item(
                        onClick = { context.openUrl(IAGENT_REPOSITORY) },
                        leadingContent = { CardGroupIcon(HugeIcons.Github) },
                        headlineContent = { Text("GitHub") },
                        supportingContent = { Text("nbb2025/rikkahub") },
                    )
                    item(
                        onClick = {
                            context.writeClipboardText(IAGENT_QQ_GROUP)
                            Toast.makeText(context, R.string.iagent_qq_copied, Toast.LENGTH_SHORT).show()
                        },
                        leadingContent = { CardGroupIcon(TencentQQIcon) },
                        headlineContent = { Text(stringResource(R.string.iagent_qq_group)) },
                        supportingContent = { Text(IAGENT_QQ_GROUP) },
                    )
                    item(
                        onClick = { context.openUrl("$IAGENT_REPOSITORY/blob/master/LICENSE") },
                        leadingContent = { CardGroupIcon(HugeIcons.File02) },
                        headlineContent = { Text(stringResource(R.string.about_page_license)) },
                        supportingContent = { Text("GNU AGPL-3.0") },
                    )
                }
            }
            item("system") {
                CardGroup {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.Code) },
                        headlineContent = { Text(stringResource(R.string.about_page_version)) },
                        supportingContent = { Text("${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}") },
                    )
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.SmartPhone01) },
                        headlineContent = { Text(stringResource(R.string.about_page_system)) },
                        supportingContent = {
                            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                        },
                    )
                }
            }
        }
    }
}
