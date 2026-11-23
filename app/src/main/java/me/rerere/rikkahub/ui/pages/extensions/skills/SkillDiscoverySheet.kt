package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.skills.SkillDirectoryEntry

@Composable
internal fun SkillDiscoverySheet(
    vm: SkillsVM,
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
    onRequireAccount: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<Result<List<SkillDirectoryEntry>>?>(null) }
    var importing by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(submittedQuery, searchAttempt) {
        results = null
        results = try {
            Result.success(vm.discoverSkills(submittedQuery))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (importing == null) onDismiss() },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || importing == null },
        ),
    ) {
        val focusManager = LocalFocusManager.current
        fun search() {
            focusManager.clearFocus()
            importError = null
            submittedQuery = query.trim()
            searchAttempt++
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.skills_page_discovery_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                label = { Text(stringResource(R.string.skills_page_search_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = { search() }, enabled = importing == null) {
                        Icon(HugeIcons.Search01, stringResource(R.string.search_ability_search))
                    }
                },
                enabled = importing == null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            importError?.let { message ->
                Text(
                    stringResource(R.string.skills_page_import_failed, message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val response = results
            val entries = response?.getOrNull().orEmpty()
            when {
                response == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularWavyProgressIndicator() }

                response.isFailure -> Text(
                    stringResource(R.string.skills_page_discovery_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                entries.isEmpty() -> Text(
                    stringResource(R.string.skills_page_search_no_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                else -> {
                    if (submittedQuery.isBlank()) {
                        Text(
                            stringResource(R.string.skills_page_discovery_popular),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 416.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            ListItem(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(entry.source, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingContent = {
                                    if (importing == entry.key) {
                                        CircularWavyProgressIndicator(Modifier.size(24.dp))
                                    } else {
                                        val importLabel = stringResource(
                                            R.string.skills_page_discovery_import,
                                            entry.name,
                                            entry.source,
                                        )
                                        TextButton(
                                            modifier = Modifier.semantics { contentDescription = importLabel },
                                            enabled = importing == null,
                                            onClick = {
                                                if (onRequireAccount != null) {
                                                    onRequireAccount()
                                                    return@TextButton
                                                }
                                                focusManager.clearFocus()
                                                importing = entry.key
                                                importError = null
                                                vm.importDirectorySkill(entry) { success, nameOrError ->
                                                    importing = null
                                                    if (success) onImported(nameOrError) else importError = nameOrError
                                                }
                                            },
                                        ) { Text(stringResource(R.string.skills_page_import_confirm)) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
