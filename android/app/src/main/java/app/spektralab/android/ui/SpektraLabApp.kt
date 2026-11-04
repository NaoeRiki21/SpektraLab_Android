package app.spektralab.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spektralab.android.i18n.AppLanguage
import app.spektralab.android.i18n.EnStrings
import app.spektralab.android.i18n.LocalAppStrings
import app.spektralab.android.i18n.ZhStrings
import app.spektralab.android.model.DarkroomParams
import app.spektralab.android.model.ExportConfig
import app.spektralab.android.model.FilmCategory
import app.spektralab.android.model.HistogramData
import app.spektralab.android.model.SpektraCatalog
import app.spektralab.android.ui.theme.SpektraLabTheme
import app.spektralab.android.ui.theme.ThemeMode
import app.spektralab.android.viewmodel.DarkroomUiState
import app.spektralab.android.viewmodel.DarkroomViewModel
import app.spektralab.android.viewmodel.ScreenRoute
import java.util.Locale

@Composable
fun SpektraLabApp(viewModel: DarkroomViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isZh = uiState.appLanguage.resolveIsChinese()
    val strings = if (isZh) ZhStrings else EnStrings

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showParityDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoFromUri(uri)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        SpektraLabTheme(
            themeMode = uiState.themeMode,
            dynamicColor = uiState.dynamicColor
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (uiState.activeScreen) {
                        ScreenRoute.LIBRARY -> {
                            LibraryWorkbenchScreen(
                                uiState = uiState,
                                onImportClick = {
                                    filePickerLauncher.launch(arrayOf("image/*", "application/octet-stream"))
                                },
                                onLoadSample = { idx -> viewModel.loadGoldenSample(idx) },
                                onReopenSession = { item -> viewModel.reopenRecentSession(item) },
                                onOpenParity = {
                                    showParityDialog = true
                                    if (uiState.parityReport == null && !uiState.isRunningParity) {
                                        viewModel.runParitySelfTest()
                                    }
                                },
                                onOpenSettings = { showSettingsDialog = true }
                            )
                        }
                        ScreenRoute.EDITOR -> {
                            BackHandler {
                                viewModel.navigateToLibrary()
                            }
                            DarkroomEditorScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { viewModel.navigateToLibrary() },
                                onOpenExport = { showExportSheet = true },
                                onOpenParity = {
                                    showParityDialog = true
                                    if (uiState.parityReport == null && !uiState.isRunningParity) {
                                        viewModel.runParitySelfTest()
                                    }
                                },
                                onOpenSettings = { showSettingsDialog = true }
                            )
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsAndThemeDialog(
                            uiState = uiState,
                            onThemeChange = viewModel::setThemeMode,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onLanguageChange = viewModel::setAppLanguage,
                            onDismiss = { showSettingsDialog = false }
                        )
                    }

                    if (showParityDialog) {
                        ParitySelfTestDialog(
                            uiState = uiState,
                            onRunAgain = { viewModel.runParitySelfTest() },
                            onDismiss = { showParityDialog = false }
                        )
                    }

                    if (showExportSheet) {
                        ExportBottomSheet(
                            uiState = uiState,
                            onConfigChange = viewModel::updateExportConfig,
                            onStartExport = {
                                viewModel.exportPrint(onCompleted = { showExportSheet = false })
                            },
                            onDismiss = { showExportSheet = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryWorkbenchScreen(
    uiState: DarkroomUiState,
    onImportClick: () -> Unit,
    onLoadSample: (Int) -> Unit,
    onReopenSession: (app.spektralab.android.viewmodel.RecentSessionItem) -> Unit,
    onOpenParity: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = s.appTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = s.appSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenParity) {
                    Icon(Icons.Default.Science, contentDescription = s.parityCheckBtn)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Palette, contentDescription = s.themeAndLangBtn)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (uiState.isBusyLoadingPhoto) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.engineReady) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                        Column {
                            Text(
                                text = if (uiState.engineReady) s.engineReadyBadge else s.engineInitializing,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.gpuDeviceName.isNotBlank()) {
                                Text(
                                    text = "${uiState.gpuDeviceName} · 380–780nm (5nm) · LibRaw 0.22.2",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    AssistChip(
                        onClick = onOpenParity,
                        label = { Text(s.parityCheckBtn) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Science,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Primary Import Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column {
                            Text(
                                text = s.importPhoto,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = s.importRawOrImageDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onImportClick,
                        enabled = uiState.engineReady && !uiState.isBusyLoadingPhoto,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.importPhoto, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Built-in Spectral Reference Scenes
                    FilledTonalButton(
                        onClick = { onLoadSample(0) },
                        enabled = uiState.engineReady && !uiState.isBusyLoadingPhoto,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraRoll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.loadGoldenSampleSunset)
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(1) },
                        enabled = uiState.engineReady && !uiState.isBusyLoadingPhoto,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.loadGoldenSampleChart)
                    }
                }
            }

            // Recent Darkroom Filmstrip
            Text(
                text = s.recentDarkroomSessions,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (uiState.recentSessions.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = s.emptyLibraryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.emptyLibrarySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(uiState.recentSessions) { session ->
                        ElevatedCard(
                            onClick = { onReopenSession(session) },
                            modifier = Modifier.width(190.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .background(Color(0xFF0E0F12)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (session.thumbnail != null) {
                                        Image(
                                            bitmap = session.thumbnail.asImageBitmap(),
                                            contentDescription = session.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = session.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = session.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkroomEditorScreen(
    uiState: DarkroomUiState,
    viewModel: DarkroomViewModel,
    onBack: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenParity: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s = LocalAppStrings.current
    var activeTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Compact Darkroom Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backToLibrary)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = uiState.photoMetadata.displayName.ifBlank { s.appTitle },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val exifLine = uiState.photoMetadata.formatExifSummary()
                        if (exifLine.isNotBlank()) {
                            Text(
                                text = exifLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.toggleHistogramOverlay() }) {
                        Icon(
                            Icons.Default.Equalizer,
                            contentDescription = "Histogram",
                            tint = if (uiState.showHistogramOverlay) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setComparingOriginal(!uiState.isComparingOriginal) }
                    ) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = s.compareOriginal,
                            tint = if (uiState.isComparingOriginal) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.undo() }, enabled = uiState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undo)
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = uiState.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = s.redo)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Palette, contentDescription = s.themeAndLangBtn)
                    }
                    Button(
                        onClick = onOpenExport,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.exportBtn, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (uiState.isRenderingFrame || uiState.isBusyLoadingPhoto) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }

        // 2. Interactive Zoomable Darkroom Viewport (Upper 52%)
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .background(Color(0xFF0B0C0E))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (zoomScale * zoom).coerceIn(1f, 6f)
                        zoomScale = nextScale
                        panOffset = if (nextScale > 1.01f) {
                            panOffset + pan
                        } else {
                            Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoomScale = if (zoomScale > 1.2f) 1f else 2.5f
                            panOffset = Offset.Zero
                        },
                        onPress = {
                            // Hold on canvas to preview original, release to return
                            // Only trigger on long press via compare button or tap
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val activeBitmap = if (uiState.isComparingOriginal) {
                uiState.originalBitmap ?: uiState.renderedBitmap
            } else {
                uiState.renderedBitmap
            }

            if (activeBitmap != null) {
                Image(
                    bitmap = activeBitmap.asImageBitmap(),
                    contentDescription = "Darkroom Viewport",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                )
            } else {
                CircularProgressIndicator()
            }

            // Top-Left Active Stock + Render Telemetry Badge
            val activeFilm = SpektraCatalog.findFilm(uiState.params.filmStock)
            val activePaper = SpektraCatalog.findPaper(uiState.params.printStock)
            Surface(
                color = Color(0xB814161A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = if (uiState.isComparingOriginal) {
                            s.comparingOriginalBadge
                        } else {
                            "${activeFilm.displayName(s.isZh)} → ${activePaper.displayName(s.isZh)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFCA7A),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.lastRenderElapsedMs > 0) {
                        val modeLabel = if (uiState.lastRenderUsedReprint) "Reprint Cache" else "Full 81-WL"
                        Text(
                            text = String.format(Locale.US, "%.1f ms · %s", uiState.lastRenderElapsedMs, modeLabel),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFD0D0D5)
                        )
                    }
                }
            }

            // Top-Right Collapsible RGB + Luminance Histogram
            AnimatedVisibility(
                visible = uiState.showHistogramOverlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                HistogramOverlayCard(histogram = uiState.histogram)
            }

            // Bottom-Right Quick Recipe Actions Pill
            Surface(
                color = Color(0xB814161A),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.copyRecipe() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = s.copySettings,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.pasteRecipe() },
                        enabled = uiState.hasCopiedRecipe,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = s.pasteSettings,
                            tint = if (uiState.hasCopiedRecipe) Color(0xFFFFCA7A) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.resetParams() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = s.resetAll,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. Lower Control Deck (48% height)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        0 -> FilmAndPaperTab(
                            params = uiState.params,
                            onSelectFilm = { viewModel.selectFilmStock(it) },
                            onUpdateParams = { viewModel.updateParams(it) }
                        )
                        1 -> CameraAndExposureTab(
                            uiState = uiState,
                            onUpdateParams = { viewModel.updateParams(it) },
                            onRedecodeWb = { mode, tempK, tint ->
                                viewModel.redecodeWithWhiteBalance(mode, tempK, tint)
                            }
                        )
                        2 -> OpticsAndGrainTab(
                            params = uiState.params,
                            onUpdateParams = { viewModel.updateParams(it) }
                        )
                        3 -> EnlargerAndPrintTab(
                            params = uiState.params,
                            onAutoSolve = { viewModel.autoSolveNeutralPrint() },
                            onUpdateParams = { viewModel.updateParams(it) }
                        )
                        4 -> FrameAndGeometryTab(
                            params = uiState.params,
                            onUpdateParams = { viewModel.updateParams(it) },
                            onOpenParity = onOpenParity
                        )
                    }
                }

                // Bottom MD3 Navigation Bar
                NavigationBar(
                    modifier = Modifier.height(68.dp),
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.CameraRoll, contentDescription = null) },
                        label = { Text(s.tabFilmPaper, maxLines = 1, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.WbSunny, contentDescription = null) },
                        label = { Text(s.tabCameraExposure, maxLines = 1, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        icon = { Icon(Icons.Default.BlurOn, contentDescription = null) },
                        label = { Text(s.tabOpticsGrain, maxLines = 1, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        label = { Text(s.tabEnlargerGrade, maxLines = 1, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        icon = { Icon(Icons.Default.CropRotate, contentDescription = null) },
                        label = { Text(s.tabFrameGeometry, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistogramOverlayCard(histogram: HistogramData) {
    Surface(
        color = Color(0xC4121418),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(136.dp)
            .height(68.dp)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val w = size.width
            val h = size.height
            val maxVal = maxOf(
                histogram.r.maxOrNull() ?: 1,
                histogram.g.maxOrNull() ?: 1,
                histogram.b.maxOrNull() ?: 1,
                histogram.l.maxOrNull() ?: 1,
                1
            ).toFloat()

            fun drawChannel(bins: IntArray, strokeColor: Color) {
                if (bins.isEmpty()) return
                val path = Path()
                for (i in bins.indices) {
                    val x = (i.toFloat() / (bins.size - 1).coerceAtLeast(1)) * w
                    val norm = (bins[i].toFloat() / maxVal).coerceIn(0f, 1f)
                    val y = h - norm * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = strokeColor, style = Stroke(width = 1.6f))
            }

            drawChannel(histogram.r, Color(0xDDFF5252))
            drawChannel(histogram.g, Color(0xDD69F0AE))
            drawChannel(histogram.b, Color(0xDD448AFF))
            drawChannel(histogram.l, Color(0xCCFFFFFF))
        }
    }
}

@Composable
private fun FilmAndPaperTab(
    params: DarkroomParams,
    onSelectFilm: (String) -> Unit,
    onUpdateParams: (DarkroomParams) -> Unit
) {
    val s = LocalAppStrings.current
    var selectedCategory by remember { mutableStateOf<FilmCategory?>(null) }

    val filteredFilms = remember(selectedCategory) {
        if (selectedCategory == null) SpektraCatalog.filmStocks
        else SpektraCatalog.filmStocks.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Film Category Filter Row
        Text(
            text = s.sectionFilmStock,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text(if (s.isZh) "全部 (25)" else "All (25)") }
            )
            FilterChip(
                selected = selectedCategory == FilmCategory.COLOR_NEGATIVE,
                onClick = { selectedCategory = FilmCategory.COLOR_NEGATIVE },
                label = { Text(s.catColorNegative) }
            )
            FilterChip(
                selected = selectedCategory == FilmCategory.CINE_FILM,
                onClick = { selectedCategory = FilmCategory.CINE_FILM },
                label = { Text(s.catCineFilm) }
            )
            FilterChip(
                selected = selectedCategory == FilmCategory.SLIDE_POSITIVE,
                onClick = { selectedCategory = FilmCategory.SLIDE_POSITIVE },
                label = { Text(s.catSlidePositive) }
            )
            FilterChip(
                selected = selectedCategory == FilmCategory.BLACK_AND_WHITE,
                onClick = { selectedCategory = FilmCategory.BLACK_AND_WHITE },
                label = { Text(s.catBlackAndWhite) }
            )
        }

        // Horizontal Film Stock Cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(filteredFilms, key = { it.id }) { stock ->
                val isSelected = params.filmStock == stock.id
                val accent = Color(stock.accentColorHex)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .width(175.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectFilm(stock.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = accent.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ISO ${stock.iso}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = stock.edgeCode.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stock.displayName(s.isZh),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stock.displaySubtitle(s.isZh),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Print Paper Stock Selector
        Text(
            text = s.sectionPaperStock,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpektraCatalog.paperStocks, key = { it.id }) { paper ->
                FilterChip(
                    selected = params.printStock == paper.id,
                    onClick = { onUpdateParams(params.copy(printStock = paper.id)) },
                    label = { Text(paper.displayName(s.isZh)) }
                )
            }
        }

        // Film Gate Format (mm)
        Text(
            text = s.sectionFilmFormat,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpektraCatalog.filmFormats, key = { it.id }) { fmt ->
                val selected = kotlin.math.abs(params.filmFormatMm - fmt.widthMm) < 0.5
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateParams(params.copy(filmFormatMm = fmt.widthMm)) },
                    label = { Text(fmt.displayLabel(s.isZh)) }
                )
            }
        }
    }
}

@Composable
private fun CameraAndExposureTab(
    uiState: DarkroomUiState,
    onUpdateParams: (DarkroomParams) -> Unit,
    onRedecodeWb: (String, Double, Double) -> Unit
) {
    val s = LocalAppStrings.current
    val params = uiState.params
    var customTempK by remember(uiState.photoMetadata.wbTemperatureK) {
        mutableFloatStateOf(uiState.photoMetadata.wbTemperatureK.toFloat().coerceIn(2200f, 10000f))
    }
    var customTint by remember(uiState.photoMetadata.wbTint) {
        mutableFloatStateOf(uiState.photoMetadata.wbTint.toFloat().coerceIn(-1f, 1f))
    }
    var selectedRawMode by remember(uiState.photoMetadata.rawMode) {
        mutableStateOf(uiState.photoMetadata.rawMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Auto Exposure & Metering Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.autoExposure, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = params.autoExposure,
                onCheckedChange = { onUpdateParams(params.copy(autoExposure = it)) }
            )
        }

        ParameterSliderRow(
            label = s.exposureCompEv,
            value = params.exposureCompensationEv.toFloat(),
            valueRange = -4.0f..4.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%+.2f EV", it) },
            onValueChange = { onUpdateParams(params.copy(exposureCompensationEv = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.densityGamma,
            value = params.densityCurveGamma.toFloat(),
            valueRange = 0.5f..2.0f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2f", it) },
            onValueChange = { onUpdateParams(params.copy(densityCurveGamma = it.toDouble())) }
        )

        // B&W Camera Wratten Lens Filter
        Text(
            text = s.bwCameraFilter,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpektraCatalog.cameraFilters, key = { it.id }) { filter ->
                FilterChip(
                    selected = params.cameraFilter == filter.id,
                    onClick = { onUpdateParams(params.copy(cameraFilter = filter.id)) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(filter.swatchHex))
                        )
                    },
                    label = { Text(filter.displayLabel(s.isZh)) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // White Balance & RAW Highlight Mode
        Text(
            text = s.wbModeTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedRawMode == "compatible16",
                onClick = { selectedRawMode = "compatible16" },
                label = { Text(s.rawModeCompatible16) }
            )
            FilterChip(
                selected = selectedRawMode == "headroom",
                onClick = { selectedRawMode = "headroom" },
                label = { Text(s.rawModeHeadroom) }
            )
        }

        ParameterSliderRow(
            label = s.wbTemperature,
            value = customTempK,
            valueRange = 2400f..9500f,
            defaultValue = 5500f,
            formatValue = { "${it.toInt()} K" },
            onValueChange = { customTempK = it }
        )

        ParameterSliderRow(
            label = s.wbTint,
            value = customTint,
            valueRange = -1.0f..1.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%+.2f", it) },
            onValueChange = { customTint = it }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    onRedecodeWb(selectedRawMode, customTempK.toDouble(), customTint.toDouble())
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(s.applyWhiteBalance)
            }
            OutlinedButton(
                onClick = {
                    onRedecodeWb(selectedRawMode, 0.0, 0.0)
                }
            ) {
                Text(s.wbAsShot)
            }
        }
    }
}

@Composable
private fun OpticsAndGrainTab(
    params: DarkroomParams,
    onUpdateParams: (DarkroomParams) -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Halation Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s.halationTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Switch(
                checked = params.halationActive,
                onCheckedChange = { onUpdateParams(params.copy(halationActive = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.removeAntihalationRemjet, style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = params.antihalationRemoved,
                onCheckedChange = { onUpdateParams(params.copy(antihalationRemoved = it)) }
            )
        }

        ParameterSliderRow(
            label = s.halationAmount,
            value = params.halationAmount.toFloat(),
            valueRange = 0.0f..3.0f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2f", it) },
            onValueChange = { onUpdateParams(params.copy(halationAmount = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.halationScatter,
            value = params.halationScatterAmount.toFloat(),
            valueRange = 0.0f..1.0f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2f", it) },
            onValueChange = { onUpdateParams(params.copy(halationScatterAmount = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.halationBoostEv,
            value = params.halationBoostEv.toFloat(),
            valueRange = 0.0f..6.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "+%.1f EV", it) },
            onValueChange = { onUpdateParams(params.copy(halationBoostEv = it.toDouble())) }
        )

        HorizontalDivider()

        // DIR Dye Couplers
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s.dirCouplersTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Switch(
                checked = params.dirCouplersActive,
                onCheckedChange = { onUpdateParams(params.copy(dirCouplersActive = it)) }
            )
        }

        ParameterSliderRow(
            label = s.dirCouplersAmount,
            value = params.dirCouplersAmount.toFloat(),
            valueRange = 0.0f..2.5f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2f", it) },
            onValueChange = { onUpdateParams(params.copy(dirCouplersAmount = it.toDouble())) }
        )

        HorizontalDivider()

        // Silver-Halide Film Grain
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s.filmGrainTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Switch(
                checked = params.grainActive,
                onCheckedChange = { onUpdateParams(params.copy(grainActive = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.grainSublayersMode, style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = params.grainSublayersActive,
                onCheckedChange = { onUpdateParams(params.copy(grainSublayersActive = it)) }
            )
        }

        ParameterSliderRow(
            label = s.grainAmount,
            value = params.grainAmount.toFloat(),
            valueRange = 0.0f..2.0f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2f", it) },
            onValueChange = { onUpdateParams(params.copy(grainAmount = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.cameraLensBlurUm,
            value = params.lensBlurUm.toFloat(),
            valueRange = 0.0f..80.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%.1f µm", it) },
            onValueChange = { onUpdateParams(params.copy(lensBlurUm = it.toDouble())) }
        )
    }
}

@Composable
private fun EnlargerAndPrintTab(
    params: DarkroomParams,
    onAutoSolve: () -> Unit,
    onUpdateParams: (DarkroomParams) -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onAutoSolve,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(s.autoNeutralizeBtn, fontWeight = FontWeight.Bold)
        }

        ParameterSliderRow(
            label = s.printExposure,
            value = params.printExposure.toFloat(),
            valueRange = 0.25f..3.50f,
            defaultValue = 1.0f,
            formatValue = { String.format(Locale.US, "%.2fx", it) },
            onValueChange = { onUpdateParams(params.copy(printExposure = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.yellowFilterShift,
            value = params.yFilterShift.toFloat(),
            valueRange = -1.0f..1.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%+.2f Y", it) },
            onValueChange = { onUpdateParams(params.copy(yFilterShift = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.magentaFilterShift,
            value = params.mFilterShift.toFloat(),
            valueRange = -1.0f..1.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%+.2f M", it) },
            onValueChange = { onUpdateParams(params.copy(mFilterShift = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.filterShiftScale,
            value = params.filterShiftScale.toFloat(),
            valueRange = 1.0f..40.0f,
            defaultValue = 10.0f,
            formatValue = { String.format(Locale.US, "%.0f CC", it) },
            onValueChange = { onUpdateParams(params.copy(filterShiftScale = it.toDouble())) }
        )

        ParameterSliderRow(
            label = s.preflashExposure,
            value = params.preflashExposure.toFloat(),
            valueRange = 0.0f..0.40f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%.3f", it) },
            onValueChange = { onUpdateParams(params.copy(preflashExposure = it.toDouble())) }
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.enlargerGlare, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = params.glareActive,
                onCheckedChange = { onUpdateParams(params.copy(glareActive = it)) }
            )
        }

        if (params.glareActive) {
            ParameterSliderRow(
                label = s.glareAmount,
                value = params.glareAmount.toFloat(),
                valueRange = 0.0f..30.0f,
                defaultValue = 4.0f,
                formatValue = { String.format(Locale.US, "%.1f", it) },
                onValueChange = { onUpdateParams(params.copy(glareAmount = it.toDouble())) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = params.scannerBlackCorrection,
                onClick = { onUpdateParams(params.copy(scannerBlackCorrection = !params.scannerBlackCorrection)) },
                label = { Text(s.scannerBlackCorr) }
            )
            FilterChip(
                selected = params.scannerWhiteCorrection,
                onClick = { onUpdateParams(params.copy(scannerWhiteCorrection = !params.scannerWhiteCorrection)) },
                label = { Text(s.scannerWhiteCorr) }
            )
        }
    }
}

@Composable
private fun FrameAndGeometryTab(
    params: DarkroomParams,
    onUpdateParams: (DarkroomParams) -> Unit,
    onOpenParity: () -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Rotate & Flip Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val nextTurns = (params.geometryQuarterTurns + 1) % 4
                    onUpdateParams(params.copy(geometryQuarterTurns = nextTurns))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.rotation90)
            }
            OutlinedButton(
                onClick = { onUpdateParams(params.copy(geometryFlipH = !params.geometryFlipH)) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.flipHorizontal)
            }
            OutlinedButton(
                onClick = { onUpdateParams(params.copy(geometryFlipV = !params.geometryFlipV)) },
                modifier = Modifier.weight(1f)
            ) {
                Text(s.flipVertical)
            }
        }

        ParameterSliderRow(
            label = s.fineRotationDeg,
            value = params.geometryRotationDeg.toFloat(),
            valueRange = -15.0f..15.0f,
            defaultValue = 0.0f,
            formatValue = { String.format(Locale.US, "%+.1f°", it) },
            onValueChange = { onUpdateParams(params.copy(geometryRotationDeg = it.toDouble())) }
        )

        // Aspect Ratio Crop Presets
        Text(
            text = s.aspectCropTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("original" to "Original", "3:2" to "3:2", "4:3" to "4:3", "1:1" to "1:1 (6×6)", "65:24" to "65:24 (XPan)")
                .forEach { (id, label) ->
                    FilterChip(
                        selected = params.aspectCropPreset == id,
                        onClick = { onUpdateParams(params.copy(aspectCropPreset = id)) },
                        label = { Text(label) }
                    )
                }
        }

        HorizontalDivider()

        // Film Strip Rebate & Sprockets
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.filmEdgeRebate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = params.filmEdgeActive,
                onCheckedChange = { onUpdateParams(params.copy(filmEdgeActive = it)) }
            )
        }

        if (params.filmEdgeActive) {
            ParameterSliderRow(
                label = s.filmEdgeNumber,
                value = params.filmEdgeFrameNumber.toFloat(),
                valueRange = 1f..36f,
                defaultValue = 24f,
                formatValue = { "▶ ${it.toInt()}A" },
                onValueChange = { onUpdateParams(params.copy(filmEdgeFrameNumber = it.toInt())) }
            )
        }

        // Quartz Date Back Imprint
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(s.dateBackImprint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = params.dateBackActive,
                onCheckedChange = { onUpdateParams(params.copy(dateBackActive = it)) }
            )
        }

        if (params.dateBackActive) {
            OutlinedTextField(
                value = params.dateBackText,
                onValueChange = { onUpdateParams(params.copy(dateBackText = it.take(16))) },
                label = { Text(s.dateBackText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedButton(
            onClick = onOpenParity,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Science, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(s.parityCheckBtn)
        }
    }
}

@Composable
private fun ParameterSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    formatValue: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onValueChange(defaultValue) }
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportBottomSheet(
    uiState: DarkroomUiState,
    onConfigChange: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val cfg = uiState.exportConfig

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = s.exportTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Format Selector
            Text(
                text = s.exportFormat,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "jpeg" to "JPEG (8-bit)",
                    "png" to "PNG (16-bit Lossless)",
                    "tiff16" to "TIFF (16-bit Archival)",
                    "tiff8" to "TIFF (8-bit)"
                ).forEach { (id, title) ->
                    FilterChip(
                        selected = cfg.format == id,
                        onClick = { onConfigChange(cfg.copy(format = id)) },
                        label = { Text(title) }
                    )
                }
            }

            // 2. Color Space Selector
            Text(
                text = s.exportColorSpace,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("sRGB", "Display P3", "ProPhoto RGB").forEach { cs ->
                    FilterChip(
                        selected = cfg.colorSpace == cs,
                        onClick = { onConfigChange(cfg.copy(colorSpace = cs)) },
                        label = { Text(cs) }
                    )
                }
            }

            // 3. Resolution Tier
            Text(
                text = s.exportResolutionTier,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = cfg.resolutionTier == "full",
                    onClick = { onConfigChange(cfg.copy(resolutionTier = "full")) },
                    label = {
                        Text(
                            if (s.isZh) "全尺寸母版 (${uiState.photoMetadata.width}×${uiState.photoMetadata.height})"
                            else "Full Resolution (${uiState.photoMetadata.width}×${uiState.photoMetadata.height})"
                        )
                    }
                )
                FilterChip(
                    selected = cfg.resolutionTier == "preview",
                    onClick = { onConfigChange(cfg.copy(resolutionTier = "preview")) },
                    label = { Text(if (s.isZh) "快速预览尺寸" else "Preview Tier") }
                )
            }

            if (cfg.format == "jpeg") {
                ParameterSliderRow(
                    label = s.exportJpegQuality,
                    value = cfg.jpegQuality.toFloat(),
                    valueRange = 70f..100f,
                    defaultValue = 95f,
                    formatValue = { "${it.toInt()}%" },
                    onValueChange = { onConfigChange(cfg.copy(jpegQuality = it.toInt())) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(s.exportIncludeFilmEdge, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = cfg.includeFilmEdgeAndDate,
                    onCheckedChange = { onConfigChange(cfg.copy(includeFilmEdgeAndDate = it)) }
                )
            }

            if (uiState.isExporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(s.exportingProgress, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onStartExport,
                enabled = !uiState.isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.exportStartBtn, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ParitySelfTestDialog(
    uiState: DarkroomUiState,
    onRunAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val report = uiState.parityReport

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(s.parityTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    s.paritySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.isRunningParity) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(s.parityRunning)
                    }
                } else if (report != null) {
                    Surface(
                        color = if (report.overallPassed) Color(0x264CAF50) else Color(0x26F44336),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (report.overallPassed) s.parityAllPassed else "SOME GATES FAILED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (report.overallPassed) Color(0xFF81C784) else Color(0xFFE57373)
                            )
                            Text(
                                text = "${s.parityGpuDevice}: ${report.gpuDeviceName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    report.gates.forEach { gate ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (gate.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (gate.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = gate.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%.1f ms", gate.elapsedMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "max_err = %.3e (tol <= %.1e)",
                                        gate.maxError,
                                        gate.tolerance
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = gate.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRunAgain, enabled = !uiState.isRunningParity) {
                Text(s.parityRunBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.closeBtn)
            }
        }
    )
}

@Composable
private fun SettingsAndThemeDialog(
    uiState: DarkroomUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.settingsTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = s.themeModeTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        label = { Text(s.themeDark) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        label = { Text(s.themeLight) }
                    )
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeChange(ThemeMode.SYSTEM) },
                        label = { Text(s.themeSystem) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = s.dynamicColorTitle,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }

                HorizontalDivider()

                Text(
                    text = s.languageTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.appLanguage == AppLanguage.ZH,
                        onClick = { onLanguageChange(AppLanguage.ZH) },
                        label = { Text("简体中文") }
                    )
                    FilterChip(
                        selected = uiState.appLanguage == AppLanguage.EN,
                        onClick = { onLanguageChange(AppLanguage.EN) },
                        label = { Text("English") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.closeBtn)
            }
        }
    )
}
