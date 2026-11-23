package me.rerere.rikkahub.ui.pages.mediacreation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.SlidersHorizontal
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.mediagen.model.ImageRole
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationCapabilities
import me.rerere.mediagen.provider.IagentMediaProtocol
import me.rerere.mediagen.provider.MediaGenerationParameter
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.capabilities
import me.rerere.mediagen.provider.iagentGptImageAnimatedFormats
import me.rerere.mediagen.provider.iagentGptImageGeneration
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MediaCreationAsset
import me.rerere.rikkahub.data.model.IagentImageOptions
import me.rerere.rikkahub.data.model.MediaCreationAssetType
import me.rerere.rikkahub.data.model.MediaCreationDraft
import me.rerere.rikkahub.data.model.MediaCreationOutput
import me.rerere.rikkahub.data.model.MediaCreationParams
import me.rerere.rikkahub.data.model.canSubmit
import me.rerere.rikkahub.data.model.mixesFramesWithReferences
import me.rerere.rikkahub.data.model.withRequired
import me.rerere.rikkahub.ui.components.ai.PickerHeader
import me.rerere.ui.components.FormItem
import me.rerere.ui.sketch.SketchDialog
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.pages.setting.components.label
import me.rerere.rikkahub.ui.pages.setting.components.typeName
import java.io.File
import kotlin.uuid.Uuid

private const val MAX_PICKED_IMAGES = 30

/**
 * 创作表单。模型、提示词、素材和参数按填写顺序呈现。
 */
@Composable
internal fun MediaCreationComposer(
    vm: MediaCreationVM,
    draft: MediaCreationDraft,
    selection: MediaCreationModelSelection?,
    providers: List<MediaGenerationProviderSetting>,
    recentOutputs: List<MediaCreationRecentOutput>,
    uploadConfigured: Boolean,
    onOpenMediaSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModels by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }

    // 正在为哪个角色挑选素材，非空时显示素材选择面板
    var pickingRole by rememberSaveable { mutableStateOf<ImageRole?>(null) }

    // 系统相册返回时面板已经关闭，这里记住选中的文件该放进哪个角色
    var importRole by rememberSaveable { mutableStateOf(ImageRole.REFERENCE) }

    // 非空时显示画板
    var sketching by remember { mutableStateOf<SketchRequest?>(null) }
    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_IMAGES)
    ) { uris ->
        vm.importAssets(uris, MediaCreationAssetType.IMAGE, importRole)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.importAssets(listOfNotNull(uri), MediaCreationAssetType.IMAGE, importRole)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.importAssets(listOfNotNull(uri), MediaCreationAssetType.VIDEO, ImageRole.REFERENCE)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importAssets(uris, MediaCreationAssetType.AUDIO, ImageRole.REFERENCE)
    }

    val kind = draft.kind ?: selection?.model?.kind ?: MediaKind.IMAGE
    val capabilities = selection?.capabilities
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val hasContent = vm.promptState.text.isNotEmpty() || draft.assets.isNotEmpty()
    val hasReference = draft.assets.any { it.type == MediaCreationAssetType.IMAGE && it.role == ImageRole.REFERENCE }
    val protocol = (selection?.provider as? MediaGenerationProviderSetting.Iagent)?.protocol
    val refsRequired = protocol == IagentMediaProtocol.GROK_EDIT || protocol == IagentMediaProtocol.VIDEO_LT_H3
    val needsFirstFrame = draft.assets.any { it.role == ImageRole.LAST_FRAME } &&
        draft.assets.none { it.role == ImageRole.FIRST_FRAME }
    val canSubmit = capabilities != null && !submitting && !needsFirstFrame && (!refsRequired || hasReference) &&
        draft.copy(prompt = vm.promptState.text.toString()).canSubmit(capabilities)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioChoice(
            label = stringResource(R.string.iagent_studio_model),
            value = selection?.model?.name ?: stringResource(R.string.iagent_studio_models_empty),
            detail = selection?.provider?.name,
            icon = { AutoAIIcon(name = selection?.model?.modelId.orEmpty(), modifier = Modifier.size(23.dp)) },
            onClick = { showModels = true },
        )

        Surface(
            shape = RoundedCornerShape(23.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.iagent_studio_prompt),
                        style = type.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasContent) {
                        IconButton(onClick = vm::clearDraft) {
                            Icon(HugeIcons.Eraser, stringResource(R.string.media_creation_page_clear_input), Modifier.size(18.dp))
                        }
                    } else {
                        Spacer(Modifier.height(36.dp))
                    }
                }
                if (draft.editingNodeId != null) {
                    EditingBar(onStop = vm::stopEditing, modifier = Modifier.padding(horizontal = 16.dp))
                }
                TextField(
                    state = vm.promptState,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(if (kind == MediaKind.VIDEO) R.string.iagent_studio_video_prompt else R.string.iagent_studio_image_prompt),
                            style = type.bodyMedium,
                        )
                    },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 7),
                    textStyle = type.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }

        if (capabilities?.acceptsAssets == true) {
            Surface(
                color = colors.surfaceContainerLow,
                shape = RoundedCornerShape(19.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 3.dp)) {
                            Text(stringResource(R.string.iagent_studio_refs), style = type.titleSmall)
                            Text(
                                stringResource(if (refsRequired) R.string.iagent_studio_required else R.string.iagent_studio_optional),
                                style = type.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (capabilities.frameRoles.isEmpty()) {
                            IconButton(onClick = { pickingRole = ImageRole.REFERENCE }) {
                                Icon(HugeIcons.Add01, stringResource(R.string.media_creation_page_add_reference_asset), tint = colors.primary)
                            }
                        }
                    }
                    if (draft.assets.isNotEmpty() || capabilities.frameRoles.isNotEmpty()) {
                        AssetRow(
                            assets = draft.assets,
                            capabilities = capabilities,
                            resolve = vm::resolve,
                            onPick = { pickingRole = it },
                            onRemove = vm::removeAsset,
                            onSetRole = vm::setAssetRole,
                            onDraw = { sketching = SketchRequest(it.role, it) },
                        )
                    }
                    val warning = when {
                        needsFirstFrame -> R.string.iagent_studio_first_frame_required
                        draft.assets.mixesFramesWithReferences(capabilities) -> R.string.media_creation_page_frames_with_references
                        else -> null
                    }
                    warning?.let {
                        Text(stringResource(it), style = type.bodySmall, color = colors.error)
                    }
                    if (capabilities.requiresRemoteInputs && draft.assets.isNotEmpty() &&
                        selection?.provider !is MediaGenerationProviderSetting.Iagent && !uploadConfigured
                    ) {
                        Text(
                            stringResource(R.string.media_creation_page_upload_not_configured_hint),
                            modifier = Modifier.clickable(onClick = onOpenMediaSettings),
                            style = type.bodySmall,
                            color = colors.error,
                        )
                    }
                }
            }
        }

        if (selection != null && capabilities != null && capabilities.parameters.isNotEmpty()) {
            val summary = draft.params.withRequired(capabilities, selection.provider.presets(kind).defaults).summary(kind)
            StudioChoice(
                label = stringResource(R.string.media_creation_page_params),
                value = summary.take(4).joinToString(" · ").ifEmpty {
                    stringResource(if (kind == MediaKind.VIDEO) R.string.iagent_studio_video_default else R.string.iagent_studio_image_default)
                },
                icon = { Icon(HugeIcons.SlidersHorizontal, null, Modifier.size(20.dp), tint = colors.primary) },
                onClick = { showParams = true },
            )
        }

        Button(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                vm.generate()
            },
            enabled = canSubmit,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(HugeIcons.Sparkles, null, Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (kind == MediaKind.VIDEO) R.string.iagent_studio_generate_video else R.string.iagent_studio_generate_image),
                style = type.titleSmall,
            )
        }

        if (!hasContent && selection != null) {
            val example = stringResource(
                if (kind == MediaKind.VIDEO) R.string.iagent_studio_example_video else R.string.iagent_studio_example_image
            )
            Surface(
                onClick = { vm.useExamplePrompt(example) },
                color = colors.surfaceContainerLow,
                shape = RoundedCornerShape(17.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Image(
                        painterResource(R.drawable.iagent_studio_example),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 68.dp, height = 45.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.iagent_studio_first_image), style = type.labelLarge)
                        Text(example, style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(HugeIcons.ArrowRight01, null, Modifier.padding(end = 5.dp).size(17.dp), tint = colors.primary)
                }
            }
        }
    }

    if (showModels) {
        ModelSheet(
            providers = providers,
            kind = kind,
            selectedModelId = draft.modelId,
            onSelect = { provider, model ->
                vm.selectModel(provider, model)
                showModels = false
            },
            onManage = {
                showModels = false
                onOpenMediaSettings()
            },
            onDismiss = { showModels = false },
        )
    }

    if (showParams && selection != null) {
        ParamsSheet(
            selection = selection,
            params = draft.params,
            onParamsChange = vm::updateParams,
            onDismiss = { showParams = false },
        )
    }

    val role = pickingRole
    if (role != null && capabilities != null) {
        // 视频只能作为参考素材
        val acceptsVideo = capabilities.videoInput && role == ImageRole.REFERENCE
        AssetPickerSheet(
            role = role,
            acceptsVideo = acceptsVideo,
            acceptsAudio = capabilities.audioInput && role == ImageRole.REFERENCE,
            recentOutputs = recentOutputs.filter { acceptsVideo || !it.output.isVideo },
            resolve = vm::resolve,
            onPickImages = {
                pickingRole = null
                importRole = role
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                // 首帧和尾帧各只有一张
                if (role == ImageRole.REFERENCE) imagesPicker.launch(request) else imagePicker.launch(request)
            },
            onPickVideo = {
                pickingRole = null
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onPickAudio = {
                pickingRole = null
                audioPicker.launch(arrayOf("audio/*"))
            },
            onSketch = {
                pickingRole = null
                sketching = SketchRequest(role = role)
            },
            onSelect = { output ->
                pickingRole = null
                if (output.isVideo) vm.useVideo(output) else vm.useImage(output, role)
            },
            onDismiss = { pickingRole = null },
        )
    }

    sketching?.let { request ->
        SketchDialog(
            image = request.asset?.let { vm.resolve(it.path).toUri() },
            // 白纸默认用参数里选的比例，画出来的首帧、参考图和要生成的画面对得上
            aspectRatio = if (selection != null && capabilities != null) {
                draft.params
                    .withRequired(capabilities, selection.provider.presets(selection.model.kind).defaults)
                    .aspectRatio
                    ?.let(::parseAspectRatio)
            } else {
                null
            },
            onDismiss = { sketching = null },
            onConfirm = { result ->
                sketching = null
                vm.addSketch(result, request.role, replacing = request.asset)
            },
        )
    }
}

/**
 * 打开画板的一次请求：给 [role] 画一张新的，或者在已有的 [asset] 上画。
 */
private class SketchRequest(val role: ImageRole, val asset: MediaCreationAsset? = null)

/**
 * 输入区的内容是从时间线上的一条记录填回来的：生成的结果会成为它的新版本。退出后内容保留，生成时另起一条。
 */
@Composable
private fun EditingBar(onStop: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HugeIcons.PencilEdit01,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.media_creation_page_editing_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = HugeIcons.Cancel01,
            contentDescription = stringResource(R.string.media_creation_page_stop_editing),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onStop)
                .padding(4.dp)
                .size(16.dp),
        )
    }
}

private val MediaGenerationCapabilities.acceptsAssets: Boolean
    get() = imageRoles.isNotEmpty() || videoInput || audioInput

/** 模型区分的槽位：首帧、尾帧各放一张图。 */
private val MediaGenerationCapabilities.frameRoles: List<ImageRole>
    get() = listOf(ImageRole.FIRST_FRAME, ImageRole.LAST_FRAME).filter { it in imageRoles }

private val MediaGenerationModel.name: String
    get() = displayName.ifBlank { modelId }

@Composable
private fun StudioChoice(
    label: String,
    value: String,
    detail: String? = null,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer, modifier = Modifier.size(38.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, style = type.labelMedium, color = colors.onSurfaceVariant)
                Text(value, style = type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant)
        }
    }
}

// ---- 素材 ----

/**
 * 视频模型按用途分槽位：首帧、尾帧各一张，其后是任意数量的参考素材。图像模型只有参考图，
 * 这时添加入口在输入框左边，这一行只列出已经放进来的素材。
 */
@Composable
private fun AssetRow(
    assets: List<MediaCreationAsset>,
    capabilities: MediaGenerationCapabilities,
    resolve: (String) -> File,
    onPick: (ImageRole) -> Unit,
    onRemove: (MediaCreationAsset) -> Unit,
    onSetRole: (MediaCreationAsset, ImageRole) -> Unit,
    onDraw: (MediaCreationAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameRoles = capabilities.frameRoles
    val showLabels = frameRoles.isNotEmpty()

    @Composable
    fun Tile(asset: MediaCreationAsset) {
        val isImage = asset.type == MediaCreationAssetType.IMAGE
        AssetTile(
            file = resolve(asset.path),
            isVideo = asset.type == MediaCreationAssetType.VIDEO,
            isAudio = asset.type == MediaCreationAssetType.AUDIO,
            label = when (asset.type) {
                MediaCreationAssetType.VIDEO -> stringResource(R.string.video)
                MediaCreationAssetType.AUDIO -> stringResource(R.string.audio)
                MediaCreationAssetType.IMAGE -> asset.role.label.takeIf { showLabels }
            },
            roleOptions = if (isImage) capabilities.imageRoles.filter { it != asset.role } else emptyList(),
            onSetRole = { onSetRole(asset, it) },
            onDraw = if (isImage) ({ onDraw(asset) }) else null,
            onRemove = { onRemove(asset) },
        )
    }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        frameRoles.forEach { role ->
            val asset = assets.lastOrNull { it.type == MediaCreationAssetType.IMAGE && it.role == role }
            if (asset != null) Tile(asset) else EmptyAssetTile(label = role.label, onClick = { onPick(role) })
        }
        assets
            .filter { it.type != MediaCreationAssetType.IMAGE || it.role == ImageRole.REFERENCE }
            .forEach { Tile(it) }
        if (showLabels && (ImageRole.REFERENCE in capabilities.imageRoles || capabilities.videoInput || capabilities.audioInput)) {
            EmptyAssetTile(label = ImageRole.REFERENCE.label, onClick = { onPick(ImageRole.REFERENCE) })
        }
    }
}

private val AssetTileSize = 60.dp
private val AssetTileShape = RoundedCornerShape(12.dp)

@Composable
private fun AssetTile(
    file: File,
    isVideo: Boolean,
    isAudio: Boolean,
    label: String?,
    roleOptions: List<ImageRole>,
    onSetRole: (ImageRole) -> Unit,
    onDraw: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.size(AssetTileSize)) {
        if (isAudio) {
            Box(Modifier.fillMaxSize().clip(AssetTileShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                Icon(HugeIcons.MusicNote03, contentDescription = null)
            }
        } else {
            MediaThumbnail(
                file = file,
                isVideo = isVideo,
                playIconSize = 10.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AssetTileShape)
                    .clickable(enabled = roleOptions.isNotEmpty() || onDraw != null) { showMenu = true },
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(vertical = 1.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.media_creation_page_remove),
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            roleOptions.forEach { role ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (role) {
                                    ImageRole.FIRST_FRAME -> R.string.media_creation_page_set_as_first_frame
                                    ImageRole.LAST_FRAME -> R.string.media_creation_page_set_as_last_frame
                                    ImageRole.REFERENCE -> R.string.media_creation_page_set_as_reference
                                }
                            )
                        )
                    },
                    onClick = {
                        showMenu = false
                        onSetRole(role)
                    },
                )
            }
            if (onDraw != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sketch_draw_on_image)) },
                    onClick = {
                        showMenu = false
                        onDraw()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyAssetTile(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(AssetTileSize),
        shape = AssetTileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 给某个槽位挑素材：之前生成过的内容排在前面，也可以从系统相册导入，或者现画一张草图。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetPickerSheet(
    role: ImageRole,
    acceptsVideo: Boolean,
    acceptsAudio: Boolean,
    recentOutputs: List<MediaCreationRecentOutput>,
    resolve: (String) -> File,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onSketch: () -> Unit,
    onSelect: (MediaCreationOutput) -> Unit,
    onDismiss: () -> Unit,
) {
    ComposerSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(
                when (role) {
                    ImageRole.FIRST_FRAME -> R.string.media_creation_page_add_first_frame_asset
                    ImageRole.LAST_FRAME -> R.string.media_creation_page_add_last_frame_asset
                    ImageRole.REFERENCE -> R.string.media_creation_page_add_reference_asset
                }
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        // 一行两个，放不下的换到下一行
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 2,
        ) {
            OutlinedButton(onClick = onPickImages, modifier = Modifier.weight(1f)) {
                Icon(HugeIcons.Image02, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.media_creation_page_gallery_image))
            }
            if (acceptsVideo) {
                OutlinedButton(onClick = onPickVideo, modifier = Modifier.weight(1f)) {
                    Icon(HugeIcons.Video01, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.media_creation_page_gallery_video))
                }
            }
            if (acceptsAudio) {
                OutlinedButton(onClick = onPickAudio, modifier = Modifier.weight(1f)) {
                    Icon(HugeIcons.MusicNote03, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.iagent_add_audio))
                }
            }
            OutlinedButton(onClick = onSketch, modifier = Modifier.weight(1f)) {
                Icon(HugeIcons.PaintBoard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.sketch))
            }
        }

        Text(
            text = stringResource(R.string.media_creation_page_recent_outputs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recentOutputs.isEmpty()) {
            Text(
                text = stringResource(R.string.media_creation_page_recent_outputs_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(recentOutputs, key = { it.output.path }) { item ->
                    MediaThumbnail(
                        file = resolve(item.output.path),
                        isVideo = item.output.isVideo,
                        poster = item.output.posterPath?.let(resolve),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(item.output) },
                    )
                }
            }
        }
    }
}

// ---- 模型 ----

@Composable
private fun ModelSheet(
    providers: List<MediaGenerationProviderSetting>,
    kind: MediaKind,
    selectedModelId: Uuid?,
    onSelect: (MediaGenerationProviderSetting, MediaGenerationModel) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(providers, kind, query) {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        providers.map { provider ->
            provider to provider.models.filter { model ->
                model.kind == kind && provider.capabilities(kind) != null && terms.all {
                    model.name.contains(it, ignoreCase = true) || model.modelId.contains(it, ignoreCase = true) ||
                        provider.name.contains(it, ignoreCase = true)
                }
            }
        }.filter { it.second.isNotEmpty() }
    }

    ComposerSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.media_creation_page_select_model),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.iagent_studio_search)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.media_creation_page_no_models),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                groups.forEach { (provider, models) ->
                    item(key = "provider-${provider.id}") {
                        Row(
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val name = provider.name.ifEmpty { provider.typeName }
                            AutoAIIcon(name = name, modifier = Modifier.size(20.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(models, key = { it.id.toString() }) { model ->
                        ModelRow(
                            model = model,
                            selected = model.id == selectedModelId,
                            onClick = { onSelect(provider, model) },
                        )
                    }
                }
            }
        }

        TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.media_creation_page_manage_providers))
        }
    }
}

@Composable
private fun ModelRow(
    model: MediaGenerationModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (model.kind == MediaKind.VIDEO) HugeIcons.Video01 else HugeIcons.Image02,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Tag { Text(model.kind.label) }
            if (selected) {
                Icon(HugeIcons.Tick02, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---- 参数 ----

/**
 * 只列出当前模型的适配器支持的公共参数。每一项都可以回到「默认」，即不下发、交给接口决定；
 * 接口必填的参数没有「默认」，不设置时使用第一个快捷选项。
 */
@Composable
private fun ParamsSheet(
    selection: MediaCreationModelSelection,
    params: MediaCreationParams,
    onParamsChange: (MediaCreationParams) -> Unit,
    onDismiss: () -> Unit,
) {
    // 面板打开期间以本地状态为准，输入框不用等草稿绕一圈回来
    var current by remember { mutableStateOf(params) }
    fun update(value: MediaCreationParams) {
        current = value
        onParamsChange(value)
    }

    val kind = selection.model.kind
    val supported = selection.capabilities.parameters
    val required = selection.capabilities.requiredParameters
    val presets = remember(selection.provider, kind) { selection.provider.presets(kind) }
    val defaults = presets.defaults

    ComposerSheet(onDismiss = onDismiss) {
        // 改过的参数，和输入区按钮上显示的是同一份
        val changed = current.summary(kind)
        PickerHeader(
            title = stringResource(R.string.media_creation_page_params_title),
            hint = changed.joinToString(" · ")
                .ifEmpty { stringResource(R.string.media_creation_page_param_default) },
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        ) {
            // 这里不放形状，参数多的时候要把高度留给下面的列表
            TextButton(
                onClick = { update(MediaCreationParams()) },
                enabled = current != MediaCreationParams(),
            ) {
                Text(stringResource(R.string.media_creation_page_params_reset))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (MediaGenerationParameter.ASPECT_RATIO in supported) {
                TextParam(
                    label = stringResource(R.string.media_creation_page_param_aspect_ratio),
                    value = current.aspectRatio,
                    presets = presets.aspectRatios,
                    default = defaults.aspectRatio.takeIf { MediaGenerationParameter.ASPECT_RATIO in required },
                    placeholder = stringResource(R.string.media_creation_page_param_aspect_ratio_hint),
                    presetIcon = { AspectRatioIcon(it) },
                    onValueChange = { update(current.copy(aspectRatio = it)) },
                )
            }
            if (MediaGenerationParameter.RESOLUTION in supported) {
                TextParam(
                    label = stringResource(R.string.media_creation_page_param_resolution),
                    value = current.resolution,
                    presets = presets.resolutions,
                    default = defaults.resolution.takeIf { MediaGenerationParameter.RESOLUTION in required },
                    placeholder = stringResource(
                        if (kind == MediaKind.VIDEO) {
                            R.string.media_creation_page_param_resolution_hint_video
                        } else {
                            R.string.media_creation_page_param_resolution_hint_image
                        }
                    ),
                    onValueChange = { update(current.copy(resolution = it)) },
                )
            }
            if (MediaGenerationParameter.DURATION in supported) {
                NumberParam(
                    label = stringResource(R.string.media_creation_page_param_duration),
                    value = current.durationSeconds?.toLong(),
                    presets = presets.durations.map { it.toLong() },
                    default = defaults.durationSeconds?.toLong()
                        .takeIf { MediaGenerationParameter.DURATION in required },
                    special = AUTO_DURATION.toLong() to
                        stringResource(R.string.media_creation_page_param_duration_auto),
                    onValueChange = { update(current.copy(durationSeconds = it?.toInt())) },
                )
            }
            if (MediaGenerationParameter.COUNT in supported) {
                NumberParam(
                    label = stringResource(R.string.media_creation_page_param_count),
                    value = current.count?.toLong(),
                    presets = presets.counts.map { it.toLong() },
                    default = defaults.count?.toLong().takeIf { MediaGenerationParameter.COUNT in required },
                    onValueChange = { update(current.copy(count = it?.toInt())) },
                )
            }
            if (selection.capabilities.iagentImageOptions && iagentGptImageGeneration(selection.model.modelId)) {
                IagentImageParameters(
                    options = current.iagentImage ?: IagentImageOptions(),
                    hasAnimatedFormats = iagentGptImageAnimatedFormats(selection.model.modelId),
                    onChange = { update(current.copy(iagentImage = it)) },
                )
            }
            if (MediaGenerationParameter.GENERATE_AUDIO in supported) {
                ToggleParam(
                    label = stringResource(R.string.media_creation_page_param_audio),
                    value = current.generateAudio,
                    onValueChange = { update(current.copy(generateAudio = it)) },
                )
            }
            if (MediaGenerationParameter.WATERMARK in supported) {
                ToggleParam(
                    label = stringResource(R.string.media_creation_page_param_watermark),
                    value = current.watermark,
                    onValueChange = { update(current.copy(watermark = it)) },
                )
            }
            if (MediaGenerationParameter.PROMPT_ENHANCEMENT in supported) {
                ToggleParam(
                    label = stringResource(R.string.media_creation_page_param_prompt_enhancement),
                    value = current.promptEnhancement,
                    onValueChange = { update(current.copy(promptEnhancement = it)) },
                )
            }
            if (MediaGenerationParameter.SEED in supported) {
                NumberParam(
                    label = stringResource(R.string.media_creation_page_param_seed),
                    value = current.seed,
                    presets = emptyList(),
                    allowZero = true,
                    maxDigits = 10,
                    onValueChange = { update(current.copy(seed = it)) },
                )
            }
        }
    }
}

@Composable
private fun IagentImageParameters(
    options: IagentImageOptions,
    hasAnimatedFormats: Boolean,
    onChange: (IagentImageOptions) -> Unit,
) {
    val formats = listOf("png", "jpeg", "webp") + if (hasAnimatedFormats) listOf("gif", "svg") else emptyList()
    FormItem(label = { Text(stringResource(R.string.iagent_image_quality)) }) {
        PresetChips(options.quality, listOf("low", "medium", "high"), null, { onChange(options.copy(quality = it)) })
    }
    FormItem(label = { Text(stringResource(R.string.iagent_image_format)) }) {
        PresetChips(
            options.format.takeIf { it in formats }, formats, "png",
            { onChange(options.copy(format = it)) }, { it.uppercase() },
        )
    }
    if (hasAnimatedFormats) {
        FormItem(label = { Text(stringResource(R.string.iagent_image_complexity)) }) {
            PresetChips(options.complexity, listOf("low", "medium", "high"), null, { onChange(options.copy(complexity = it)) })
        }
    }
    if (hasAnimatedFormats && options.format == "gif") {
        NumberParam(
            label = stringResource(R.string.iagent_gif_frames),
            value = options.frames?.toLong(),
            presets = listOf(6L, 9L, 12L, 16L),
            default = 9,
            onValueChange = { onChange(options.copy(frames = it?.toInt())) },
        )
        NumberParam(
            label = stringResource(R.string.iagent_gif_interval),
            value = options.intervalMs?.toLong(),
            presets = listOf(100L, 200L, 500L),
            default = 200,
            onValueChange = { onChange(options.copy(intervalMs = it?.toInt())) },
        )
    }
}

// [default] 不为空表示这一项必填：没有「默认」，还没设置时选中的是 [default]
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> PresetChips(
    value: T?,
    presets: List<T>,
    default: T?,
    onValueChange: (T?) -> Unit,
    label: (T) -> String = { it.toString() },
    icon: (@Composable (T) -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (default == null) {
            PresetButton(
                selected = value == null,
                onClick = { onValueChange(null) },
                text = stringResource(R.string.media_creation_page_param_default),
            )
        }
        presets.forEach { preset ->
            PresetButton(
                selected = (value ?: default) == preset,
                onClick = { onValueChange(preset) },
                text = label(preset),
                icon = icon?.let { { it(preset) } },
            )
        }
    }
}

// 选中时从胶囊变成方角
@Composable
private fun PresetButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    icon: (@Composable () -> Unit)? = null,
) {
    ToggleButton(
        checked = selected,
        // 再点一次已经选中的不会取消
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics { role = Role.RadioButton },
        buttonSize = ToggleButtonSize.ExtraSmall,
        icon = icon,
        colors = ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Text(text)
    }
}

/**
 * 比例选项前面的小图标：按「宽:高」画出对应形状的方框。adaptive 这类不是固定比例的取值画成虚线方框。
 */
@Composable
private fun AspectRatioIcon(value: String) {
    val ratio = remember(value) { parseAspectRatio(value) }
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        // 描边压在边线两侧，留出一个线宽才不会被裁掉
        val box = size.minDimension - strokeWidth
        // 太扁或太窄的比例收一收，保证看得出是个方框
        val shape = (ratio ?: 1f).coerceIn(0.4f, 2.5f)
        val frame = if (shape >= 1f) Size(box, box / shape) else Size(box * shape, box)
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - frame.width) / 2, (size.height - frame.height) / 2),
            size = frame,
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = if (ratio == null) {
                    PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                } else {
                    null
                },
            ),
        )
    }
}

private fun parseAspectRatio(value: String): Float? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val width = parts[0].trim().toFloatOrNull() ?: return null
    val height = parts[1].trim().toFloatOrNull() ?: return null
    return if (width > 0 && height > 0) width / height else null
}

@Composable
private fun TextParam(
    label: String,
    value: String?,
    presets: List<String>,
    default: String?,
    placeholder: String,
    onValueChange: (String?) -> Unit,
    presetIcon: (@Composable (String) -> Unit)? = null,
) {
    FormItem(label = { Text(label) }) {
        PresetChips(
            value = value,
            presets = presets,
            default = default,
            onValueChange = onValueChange,
            icon = presetIcon,
        )
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { text -> onValueChange(text.trim().ifEmpty { null }) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(default ?: placeholder) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

// 只接受正整数（[allowZero] 时含 0）；清空输入框等于回到默认。
// [special] 是额外接受的一个负数取值和它在快捷选项上的名字，输入框里打出负号就算选中它
@Composable
private fun NumberParam(
    label: String,
    value: Long?,
    presets: List<Long>,
    onValueChange: (Long?) -> Unit,
    default: Long? = null,
    allowZero: Boolean = false,
    maxDigits: Int = 3,
    special: Pair<Long, String>? = null,
) {
    FormItem(label = { Text(label) }) {
        if (presets.isNotEmpty()) {
            PresetChips(
                value = value,
                presets = presets,
                default = default,
                onValueChange = onValueChange,
                label = { preset -> special?.takeIf { it.first == preset }?.second ?: preset.toString() },
            )
        }
        OutlinedTextField(
            value = value?.toString().orEmpty(),
            onValueChange = { text ->
                val input = text.trim()
                val number = if (special != null && input.startsWith("-")) {
                    // 已经是特殊取值时内容变短，说明是在删除
                    special.first.takeUnless { value == it && input.length < it.toString().length }
                } else {
                    input.filter(Char::isDigit).take(maxDigits).toLongOrNull()?.takeIf { allowZero || it > 0 }
                }
                onValueChange(number)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(default?.toString() ?: stringResource(R.string.media_creation_page_param_default)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ToggleParam(
    label: String,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
) {
    val options = listOf<Pair<Boolean?, String>>(
        null to stringResource(R.string.media_creation_page_param_default),
        true to stringResource(R.string.media_creation_page_param_on),
        false to stringResource(R.string.media_creation_page_param_off),
    )
    FormItem(label = { Text(label) }) {
        // 连接式按钮组
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (option, text) ->
                ToggleButton(
                    checked = value == option,
                    onCheckedChange = { onValueChange(option) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ComposerSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
