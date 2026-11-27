package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IAGENT_ORIGIN
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentDifferentAccount
import me.rerere.rikkahub.data.iagent.IagentModels
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.openUrl
import org.koin.compose.koinInject
import java.text.NumberFormat

@Composable
internal fun IagentLoginPage(account: IagentAccount) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val differentAccount = stringResource(R.string.iagent_account_bound)
    val canSubmit = !working && (if (challenge == null) email.isNotBlank() && password.isNotEmpty() else code.length == 6)
    val submit: () -> Unit = {
        if (canSubmit) {
            working = true
            error = null
            scope.launch {
                try {
                    val current = challenge
                    if (current == null) {
                        challenge = account.login(email, password)
                        password = ""
                    } else {
                        account.confirmTwoFactor(current, code)
                    }
                } catch (caught: CancellationException) {
                    throw caught
                } catch (_: IagentDifferentAccount) {
                    error = differentAccount
                } catch (caught: Exception) {
                    error = caught.localizedMessage ?: caught.javaClass.simpleName
                } finally {
                    working = false
                }
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.iagent_app_name), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoginHeader()
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        stringResource(if (challenge == null) R.string.iagent_login_heading else R.string.iagent_verify),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.iagent_login_support),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (challenge == null) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, enabled = !working, singleLine = true,
                        label = { Text(stringResource(R.string.iagent_email)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, enabled = !working, singleLine = true,
                        label = { Text(stringResource(R.string.iagent_password)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    )
                } else {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(6) },
                        enabled = !working, singleLine = true,
                        label = { Text(stringResource(R.string.iagent_verify_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                Button(
                    onClick = submit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (challenge == null) R.string.iagent_sign_in else R.string.iagent_verify))
                    }
                }
                if (challenge != null) {
                    TextButton(onClick = { challenge = null; code = ""; error = null }, enabled = !working) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    TextButton(onClick = { context.openUrl(IAGENT_ORIGIN) }) {
                        Text(stringResource(R.string.iagent_login_website))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginHeader() {
    Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(24.dp))) {
        Image(
            painter = painterResource(R.drawable.iagent_studio_example),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x680E202B)))))
        IagentMark(modifier = Modifier.align(Alignment.BottomStart).padding(15.dp), size = 50)
    }
}

@Composable
internal fun IagentAccountPage(onSignIn: () -> Unit, onRecharge: () -> Unit) {
    val account = koinInject<IagentAccount>()
    val models = koinInject<IagentModels>()
    val user by account.user.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var updated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = user?.id
    LaunchedEffect(owner) {
        if (owner == null) return@LaunchedEffect
        try {
            account.refreshProfile(owner)
        } catch (caught: CancellationException) {
            throw caught
        } catch (_: Exception) {
            // The account page is also accessible with the previously loaded session.
        }
    }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.iagent_account), style = type.headlineSmall, fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(23.dp),
                    contentColor = colors.onPrimaryContainer,
                    color = colors.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                            .padding(19.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            IagentMark(size = 35)
                            Text(
                                user?.email ?: stringResource(R.string.iagent_guest),
                                style = type.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        user?.balance?.let {
                            Text(stringResource(R.string.iagent_account_balance_note), style = type.labelMedium)
                            Text(NumberFormat.getCurrencyInstance(java.util.Locale.US).format(it), style = type.headlineMedium)
                        }
                        FilledTonalButton(onClick = if (owner == null) onSignIn else onRecharge) {
                            Text(stringResource(if (owner == null) R.string.iagent_sign_in else R.string.iagent_recharge))
                        }
                    }
                }
                AccountAction(
                    title = stringResource(R.string.iagent_catalog_title),
                    detail = stringResource(R.string.iagent_catalog_hint),
                    icon = HugeIcons.Sparkles,
                    onClick = { nav.navigate(Screen.IagentCatalog) },
                )
                AccountAction(
                    title = stringResource(R.string.setting_page_providers),
                    detail = stringResource(R.string.setting_page_providers_desc),
                    icon = HugeIcons.Brain02,
                    onClick = { nav.navigate(Screen.SettingProvider) },
                )
                if (owner != null) {
                AccountAction(
                    title = stringResource(R.string.iagent_refresh_models),
                    detail = stringResource(R.string.iagent_account_refresh_hint),
                    icon = HugeIcons.Refresh,
                    enabled = !working && user != null,
                    onClick = {
                        val currentOwner = owner ?: return@AccountAction
                        working = true
                        updated = false
                        error = null
                        scope.launch {
                            try {
                                models.synchronize(currentOwner, force = true)
                                updated = true
                            } catch (caught: CancellationException) {
                                throw caught
                            } catch (caught: Exception) {
                                error = caught.localizedMessage ?: caught.javaClass.simpleName
                            } finally {
                                working = false
                            }
                        }
                    },
                )
                if (working) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                if (updated) Text(stringResource(R.string.iagent_models_updated), color = colors.primary, style = type.bodySmall)
                error?.let { Text(it, color = colors.error, style = type.bodySmall) }
                } else {
                    AccountAction(
                        title = stringResource(R.string.iagent_recharge),
                        icon = HugeIcons.ArrowRight01,
                        onClick = onRecharge,
                    )
                }
                AccountAction(
                    title = stringResource(R.string.skills_page_title),
                    icon = HugeIcons.Sparkles,
                    onClick = { nav.navigate(Screen.Skills) },
                )
                AccountAction(
                    title = stringResource(if (owner == null) R.string.about_page_title else R.string.iagent_account_preferences),
                    icon = HugeIcons.Settings03,
                    onClick = { nav.navigate(if (owner == null) Screen.SettingAbout else Screen.Setting) },
                )
                Spacer(Modifier.height(6.dp))
                if (owner != null) TextButton(onClick = { confirm = true }) {
                    Text(stringResource(R.string.iagent_sign_out), color = colors.error)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.iagent_sign_out)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { account.logout() }
                }) { Text(stringResource(R.string.iagent_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AccountAction(
    title: String,
    icon: ImageVector,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(21.dp), tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant)
        }
    }
}
