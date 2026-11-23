package me.rerere.rikkahub.ui.pages.mediacreation

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ui.toast.ToastType
import me.rerere.ui.toast.ToasterState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.MediaCreationNode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.heroContainer
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.components.Tooltip
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/**
 * 创作表单与作品视图共享会话草稿和媒体任务。作品可以放回表单继续创作。
 */
@Composable
fun MediaCreationPage(id: String, workspace: Boolean = false) {
    val vm: MediaCreationVM = koinViewModel(parameters = { parametersOf(id) })
    val navController = LocalNavController.current
    val toaster = LocalToaster.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val uploadConfigured by vm.uploadConfigured.collectAsStateWithLifecycle()
    val recentOutputs by vm.recentOutputs.collectAsStateWithLifecycle()
    val selectedNodes by vm.selectedNodes.collectAsStateWithLifecycle()
    val nodes = vm.nodes.collectAsLazyPagingItems()

    var showUploadDialog by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectedNodes != null || showResults) {
        if (selectedNodes != null) vm.endSelection() else showResults = false
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MediaCreationEvent.Error -> toaster.show(event.message, type = ToastType.Error)
                MediaCreationEvent.UploadNotConfigured -> showUploadDialog = true
                MediaCreationEvent.SessionGone -> navController.popBackStack()
            }
        }
    }
    LaunchedEffect(vm) {
        vm.submitted.collect { showResults = true }
    }
    var previousAssets by remember { mutableStateOf(draft.assets.toSet()) }
    var previousEdit by remember { mutableStateOf(draft.editingNodeId) }
    LaunchedEffect(draft.assets, draft.editingNodeId) {
        if (draft.assets.any { it !in previousAssets } || draft.editingNodeId != null && draft.editingNodeId != previousEdit) {
            showResults = false
        }
        previousAssets = draft.assets.toSet()
        previousEdit = draft.editingNodeId
    }

    val heroCorner by LocalNavAnimatedContentScope.current.transition.animateDp(label = "heroCorner") { state ->
        if (state == EnterExitState.Visible) 0.dp else 24.dp
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        modifier = if (workspace) Modifier else Modifier.heroContainer(mediaCreationHeroKey(id), RoundedCornerShape(heroCorner)),
        topBar = {
            Surface(color = colors.background) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 15.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!workspace) BackButton()
                    val selected = selectedNodes
                    val title = when {
                        selected != null -> stringResource(R.string.media_creation_page_selected_count, selected.size)
                        showResults -> stringResource(R.string.iagent_studio_results)
                        else -> session?.title?.takeUnless { workspace || it.isBlank() }
                            ?: stringResource(R.string.iagent_studio_title)
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f).then(
                            if (!workspace && session != null && !showResults && selected == null) {
                                Modifier.clickable { renaming = true }
                            } else Modifier
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected == null) {
                        FilledTonalButton(
                            onClick = { showResults = !showResults },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                        ) {
                            Icon(if (showResults) HugeIcons.Add01 else HugeIcons.Image02, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (showResults) R.string.iagent_studio_create else R.string.iagent_studio_results))
                        }
                        if (showResults && nodes.itemCount > 0) {
                            IconButton(onClick = vm::startSelection) {
                                Icon(HugeIcons.CheckList, stringResource(R.string.media_creation_page_select))
                            }
                        } else if (workspace) {
                            IconButton(onClick = { navController.navigate(Screen.MediaCreationSessions) }) {
                                Icon(HugeIcons.Clock01, stringResource(R.string.iagent_studio_sessions))
                            }
                        }
                    }
                }
            }
        },
    ) { insets ->
        val direction = LocalLayoutDirection.current
        Column(
            Modifier.padding(
                top = insets.calculateTopPadding(),
                start = insets.calculateStartPadding(direction),
                end = insets.calculateEndPadding(direction),
            ).fillMaxSize()
        ) {
            if (showResults || selectedNodes != null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    MediaCreationTimeline(
                        vm = vm,
                        nodes = nodes,
                        editingNodeId = draft.editingNodeId,
                        selectedNodes = selectedNodes,
                        onCreate = { showResults = false },
                    )
                }
                if (selectedNodes != null) {
                    MediaCreationSelectionBar(
                        vm = vm,
                        selectedCount = selectedNodes?.size ?: 0,
                        allSelected = nodes.itemCount > 0 && (selectedNodes?.size ?: 0) >= nodes.itemCount,
                        onDelete = { confirmDeleteSelected = true },
                    )
                }
            } else {
                val kind = draft.kind ?: selection?.model?.kind ?: MediaKind.IMAGE
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 660.dp).fillMaxWidth().fillMaxHeight()
                            .imePadding().navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "kind") {
                            StudioModeSelector(kind, onSelect = vm::selectKind)
                        }
                        item(key = "editor") {
                            MediaCreationComposer(
                                vm = vm,
                                draft = draft,
                                selection = selection,
                                providers = settings.mediaGenerationProviders,
                                recentOutputs = recentOutputs,
                                uploadConfigured = uploadConfigured,
                                onOpenMediaSettings = { navController.navigate(Screen.IagentAccount) },
                            )
                        }
                    }
                }
            }
        }
    }

    session?.takeIf { renaming }?.let { current ->
        RenameSessionDialog(
            initialTitle = current.title,
            onConfirm = { title ->
                vm.rename(title)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    RikkaConfirmDialog(
        show = confirmDeleteSelected,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            confirmDeleteSelected = false
            vm.deleteSelected()
        },
        onDismiss = { confirmDeleteSelected = false },
    ) {
        Text(stringResource(R.string.media_creation_page_delete_selected_text, selectedNodes?.size ?: 0))
    }

    RikkaConfirmDialog(
        show = showUploadDialog,
        title = stringResource(R.string.media_creation_page_upload_dialog_title),
        confirmText = stringResource(R.string.media_creation_page_go_configure),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showUploadDialog = false
            navController.navigate(Screen.SettingMedia)
        },
        onDismiss = { showUploadDialog = false },
    ) {
        Text(stringResource(R.string.media_creation_page_upload_dialog_text))
    }
}

@Composable
private fun MediaCreationTimeline(
    vm: MediaCreationVM,
    nodes: LazyPagingItems<MediaCreationNode>,
    editingNodeId: Uuid?,
    selectedNodes: Set<Uuid>?,
    onCreate: () -> Unit,
) {
    val listState = rememberLazyListState()

    // 最新任务排在最前。浏览较早作品时不移动当前位置。
    val newestId = nodes.newestId()
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    // 自己提交的记录无论当前翻到哪里都滚过去；新版本出现在原来那一项上，它还在屏幕里时不动
    LaunchedEffect(vm) {
        vm.submitted.collect { submitted ->
            val key = submitted.nodeId.toString()
            if (listState.layoutInfo.visibleItemsInfo.none { it.key == key }) {
                listState.scrollToItem(submitted.position)
            }
        }
    }

    if (nodes.loadState.refresh is LoadState.NotLoading && nodes.itemCount == 0) {
        TimelineEmpty(onCreate = onCreate)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 660.dp).fillMaxHeight().fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = nodes.itemCount,
            // 以项为单位：切换版本时原地换内容，不会被当成另一项
            key = nodes.itemKey { it.id.toString() },
        ) { index ->
            val node = nodes[index]
            if (node != null) {
                MediaCreationRecordItem(
                    node = node,
                    editing = node.id == editingNodeId,
                    selected = selectedNodes?.let { node.id in it },
                    vm = vm,
                )
            } else {
                // 还没加载到的记录先占位，保证列表总长度和滚动位置稳定
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {}
            }
        }
    }
}

/** 会话列表项和创作页共用的容器变换 key。 */
internal fun mediaCreationHeroKey(sessionId: String) = "media_creation_session_$sessionId"

private fun LazyPagingItems<MediaCreationNode>.newestId(): Uuid? =
    if (itemCount > 0) peek(0)?.id else null

@Composable
private fun TimelineEmpty(onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(21.dp), color = colors.primaryContainer) {
            Icon(
                HugeIcons.ImageToVideo,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.padding(16.dp).size(29.dp),
            )
        }
        Text(
            stringResource(R.string.iagent_studio_results_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCreate) { Text(stringResource(R.string.iagent_studio_create)) }
    }
}

/** 多选时代替输入区：退出多选、全选，以及对选中各项的批量操作。 */
@Composable
private fun MediaCreationSelectionBar(
    vm: MediaCreationVM,
    selectedCount: Int,
    allSelected: Boolean,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    // 和输入区占同一个位置、同一种外观
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.largeIncreased,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val exitLabel = stringResource(R.string.media_creation_page_exit_selection)
            Tooltip(tooltip = { Text(exitLabel) }) {
                IconButton(onClick = { vm.endSelection() }) {
                    Icon(HugeIcons.Cancel01, contentDescription = exitLabel)
                }
            }
            TextButton(
                onClick = { if (allSelected) vm.clearSelection() else vm.selectAll() },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(
                        if (allSelected) R.string.media_creation_page_deselect_all else R.string.media_creation_page_select_all
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            saveSelected(context, toaster, vm)
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = selectedCount > 0 && !saving,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(HugeIcons.Download01, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(if (saving) R.string.media_creation_page_saving else R.string.media_creation_page_save)
                )
            }
            Button(
                onClick = onDelete,
                enabled = selectedCount > 0 && !saving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(HugeIcons.Delete01, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.delete))
            }
        }
    }
}

/** 把选中各项当前显示的版本的产出逐个存进相册，一个失败不影响其余的。 */
private suspend fun saveSelected(context: Context, toaster: ToasterState, vm: MediaCreationVM) {
    val outputs = vm.getSelectedOutputs()
    if (outputs.isEmpty()) {
        toaster.show(context.getString(R.string.media_creation_page_nothing_to_save), type = ToastType.Info)
        return
    }
    val failures = outputs.mapNotNull { output ->
        try {
            saveMediaToGallery(context, vm.resolve(output.path), output.mimeType)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
    }
    when {
        failures.isEmpty() -> toaster.show(
            context.getString(R.string.media_creation_page_saved_files, outputs.size),
            type = ToastType.Success,
        )
        else -> {
            val reason = failures.first().message ?: context.getString(R.string.media_creation_page_save_failed)
            toaster.show(
                if (failures.size == outputs.size) {
                    reason
                } else {
                    context.getString(
                        R.string.media_creation_page_saved_files_partial,
                        outputs.size - failures.size,
                        failures.size,
                        reason,
                    )
                },
                type = ToastType.Error,
            )
        }
    }
}
