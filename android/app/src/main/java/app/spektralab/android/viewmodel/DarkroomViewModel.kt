package app.spektralab.android.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.spektralab.android.i18n.AppLanguage
import app.spektralab.android.model.DarkroomParams
import app.spektralab.android.model.ExportConfig
import app.spektralab.android.model.HistogramData
import app.spektralab.android.model.ParityGateItem
import app.spektralab.android.model.ParitySuiteReport
import app.spektralab.android.model.PhotoMetadata
import app.spektralab.android.model.SpektraCatalog
import app.spektralab.android.nativebridge.SpektraNativeBridge
import app.spektralab.android.ui.theme.ThemeMode
import app.spektralab.android.util.FilmFrameCompositor
import app.spektralab.android.util.MediaStoreExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class RecentSessionItem(
    val filePath: String,
    val displayName: String,
    val subtitle: String,
    val thumbnail: Bitmap?,
    val savedParams: DarkroomParams
)

data class DarkroomUiState(
    val engineReady: Boolean = false,
    val engineError: String? = null,
    val gpuDeviceName: String = "",
    val engineBuildStamp: String = "",
    val activeScreen: ScreenRoute = ScreenRoute.LIBRARY,

    // Appearance & Language
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.ZH,

    // Active Photo & Rendering
    val isBusyLoadingPhoto: Boolean = false,
    val isRenderingFrame: Boolean = false,
    val photoMetadata: PhotoMetadata = PhotoMetadata(),
    val params: DarkroomParams = DarkroomParams(),
    val renderedBitmap: Bitmap? = null,
    val originalBitmap: Bitmap? = null,
    val isComparingOriginal: Boolean = false,
    val showHistogramOverlay: Boolean = true,
    val histogram: HistogramData = HistogramData(),
    val lastRenderElapsedMs: Double = 0.0,
    val lastRenderUsedReprint: Boolean = false,

    // History & Recipe
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasCopiedRecipe: Boolean = false,
    val recentSessions: List<RecentSessionItem> = emptyList(),

    // Export
    val isExporting: Boolean = false,
    val exportConfig: ExportConfig = ExportConfig(),
    val snackbarMessage: String? = null,

    // Parity Self-Test
    val isRunningParity: Boolean = false,
    val parityReport: ParitySuiteReport? = null
)

enum class ScreenRoute {
    LIBRARY,
    EDITOR
}

class DarkroomViewModel(application: Application) : AndroidViewModel(application) {

    // Single-threaded native engine dispatcher so all Vulkan/C++ calls are strictly serialized
    private val engineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SpektraEngineThread").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(DarkroomUiState())
    val uiState: StateFlow<DarkroomUiState> = _uiState.asStateFlow()

    private var resourcesDir: File? = null
    private var reusableNativeBitmap: Bitmap? = null
    private var lastRenderedParams: DarkroomParams? = null
    private var renderJob: Job? = null

    private val undoStack = ArrayDeque<DarkroomParams>()
    private val redoStack = ArrayDeque<DarkroomParams>()
    private var copiedRecipe: DarkroomParams? = null

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch(engineDispatcher) {
            try {
                val context = getApplication<Application>()
                val extracted = SpektraNativeBridge.ensureResourcesExtracted(context)
                resourcesDir = extracted

                val initJsonStr = SpektraNativeBridge.nativeInitEngine(extracted.absolutePath)
                val initObj = JSONObject(initJsonStr)
                if (!initObj.optBoolean("ok", false)) {
                    val err = initObj.optString("error", "Unknown engine init error")
                    _uiState.update { it.copy(engineError = err) }
                    return@launch
                }

                val capsObj = initObj.optJSONObject("capabilities")
                val gpuName = capsObj?.optString("device_name", "Vulkan Compute GPU") ?: "Vulkan Compute GPU"
                val buildStamp = capsObj?.optString("build", "SpektraLab C++20/Vulkan") ?: "SpektraLab C++20/Vulkan"

                _uiState.update {
                    it.copy(
                        engineReady = true,
                        gpuDeviceName = gpuName,
                        engineBuildStamp = buildStamp
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(engineError = t.message ?: t.toString()) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.update { it.copy(dynamicColor = enabled) }
    }

    fun setAppLanguage(language: AppLanguage) {
        _uiState.update { it.copy(appLanguage = language) }
    }

    fun navigateToLibrary() {
        saveCurrentToRecentSessions()
        _uiState.update { it.copy(activeScreen = ScreenRoute.LIBRARY, isComparingOriginal = false) }
    }

    fun setComparingOriginal(comparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginal = comparing) }
    }

    fun toggleHistogramOverlay() {
        _uiState.update { it.copy(showHistogramOverlay = !it.showHistogramOverlay) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun loadGoldenSample(presetIndex: Int) {
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isBusyLoadingPhoto = true) }
            try {
                val context = getApplication<Application>()
                val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
                val fileName = if (presetIndex == 0) {
                    "Spektra_Manhattan_Sunset_16bit.tif"
                } else {
                    "Spektra_ColorChecker_StepWedge_16bit.tif"
                }
                val targetFile = File(sampleDir, fileName)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    val genJson = JSONObject(
                        SpektraNativeBridge.nativeGenerateSamplePhoto(targetFile.absolutePath, presetIndex)
                    )
                    if (!genJson.optBoolean("ok", false)) {
                        _uiState.update {
                            it.copy(
                                isBusyLoadingPhoto = false,
                                snackbarMessage = genJson.optString("error", "Sample generation failed")
                            )
                        }
                        return@launch
                    }
                }

                val defaultParams = if (presetIndex == 0) {
                    DarkroomParams(
                        filmStock = "cinestill_800t",
                        printStock = "kodak_endura_premier",
                        antihalationRemoved = true,
                        halationAmount = 1.6,
                        halationBoostEv = 1.5
                    )
                } else {
                    DarkroomParams(
                        filmStock = "kodak_portra_400",
                        printStock = "kodak_endura_premier"
                    )
                }

                val title = if (presetIndex == 0) "Manhattan Sunset & Neon" else "24-Patch ColorChecker"
                openPhotoInternal(
                    file = targetFile,
                    displayName = title,
                    rawMode = "compatible16",
                    wbTempK = 0.0,
                    wbTint = 0.0,
                    initialParams = defaultParams
                )
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isBusyLoadingPhoto = false, snackbarMessage = t.message ?: "Failed to load sample")
                }
            }
        }
    }

    fun importPhotoFromUri(uri: Uri) {
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isBusyLoadingPhoto = true) }
            try {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                var displayName = "Imported_Photo.jpg"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                    }
                }

                val ext = displayName.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
                val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
                val destFile = File(importDir, "import_${System.currentTimeMillis()}.$ext")
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }

                openPhotoInternal(
                    file = destFile,
                    displayName = displayName,
                    rawMode = "compatible16",
                    wbTempK = 0.0,
                    wbTint = 0.0,
                    initialParams = DarkroomParams()
                )
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isBusyLoadingPhoto = false, snackbarMessage = t.message ?: "Import failed")
                }
            }
        }
    }

    fun reopenRecentSession(item: RecentSessionItem) {
        val file = File(item.filePath)
        if (!file.exists()) return
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isBusyLoadingPhoto = true) }
            openPhotoInternal(
                file = file,
                displayName = item.displayName,
                rawMode = "compatible16",
                wbTempK = 0.0,
                wbTint = 0.0,
                initialParams = item.savedParams
            )
        }
    }

    fun redecodeWithWhiteBalance(rawMode: String, tempK: Double, tint: Double) {
        val currentMeta = _uiState.value.photoMetadata
        val file = File(currentMeta.filePath)
        if (!file.exists()) return
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isBusyLoadingPhoto = true) }
            openPhotoInternal(
                file = file,
                displayName = currentMeta.displayName,
                rawMode = rawMode,
                wbTempK = tempK,
                wbTint = tint,
                initialParams = _uiState.value.params
            )
        }
    }

    private suspend fun openPhotoInternal(
        file: File,
        displayName: String,
        rawMode: String,
        wbTempK: Double,
        wbTint: Double,
        initialParams: DarkroomParams
    ) = withContext(engineDispatcher) {
        val respStr = SpektraNativeBridge.nativeOpenPhoto(
            filePath = file.absolutePath,
            rawMode = rawMode,
            temperatureK = wbTempK,
            tint = wbTint,
            initialParamsJson = initialParams.toEngineJson()
        )
        val resp = JSONObject(respStr)
        if (!resp.optBoolean("ok", false)) {
            _uiState.update {
                it.copy(
                    isBusyLoadingPhoto = false,
                    snackbarMessage = resp.optString("error", "Failed to open photo")
                )
            }
            return@withContext
        }

        val probeObj = resp.optJSONObject("probe") ?: resp
        val w = probeObj.optInt("width", 1200)
        val h = probeObj.optInt("height", 800)
        val kindStr = probeObj.optString("kind", "")
        val meta = PhotoMetadata(
            filePath = file.absolutePath,
            displayName = displayName,
            width = w,
            height = h,
            isRaw = kindStr.equals("raw", ignoreCase = true) || resp.optBoolean("is_raw", false),
            make = probeObj.optString("make", ""),
            model = probeObj.optString("model", ""),
            lens = probeObj.optString("lens", ""),
            iso = probeObj.optDouble("iso", 0.0),
            shutterS = probeObj.optDouble("shutter_s", 0.0),
            fNumber = probeObj.optDouble("aperture", probeObj.optDouble("f_number", 0.0)),
            focalLengthMm = probeObj.optDouble("focal_mm", 0.0),
            wbTemperatureK = if (wbTempK > 0.0) wbTempK else probeObj.optDouble("as_shot_temperature_k", 5500.0),
            wbTint = if (wbTempK > 0.0) wbTint else probeObj.optDouble("as_shot_tint", 0.0),
            rawMode = rawMode
        )

        // Build original Linear ProPhoto -> sRGB preview bitmap for instant Before/After comparison
        val maxOrigEdge = 1280
        val scale = minOf(1.0, maxOrigEdge.toDouble() / maxOf(w, h).toDouble())
        val origW = (w * scale).toInt().coerceAtLeast(1)
        val origH = (h * scale).toInt().coerceAtLeast(1)
        val origBmp = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        SpektraNativeBridge.nativeRenderOriginalPreview(origBmp)

        undoStack.clear()
        redoStack.clear()
        lastRenderedParams = null

        _uiState.update {
            it.copy(
                isBusyLoadingPhoto = false,
                activeScreen = ScreenRoute.EDITOR,
                photoMetadata = meta,
                params = initialParams,
                originalBitmap = origBmp,
                isComparingOriginal = false,
                canUndo = false,
                canRedo = false
            )
        }

        renderFrameInternal(initialParams, preferReprint = false)
    }

    fun selectFilmStock(filmId: String, autoPairDefaultPaper: Boolean = true) {
        val current = _uiState.value.params
        val spec = SpektraCatalog.findFilm(filmId)
        val nextPaper = if (autoPairDefaultPaper) spec.defaultPaperId else current.printStock
        val nextParams = current.copy(
            filmStock = filmId,
            printStock = nextPaper,
            antihalationRemoved = filmId == "cinestill_800t"
        )
        updateParams(nextParams, recordUndo = true)
    }

    fun updateParams(newParams: DarkroomParams, recordUndo: Boolean = true) {
        val current = _uiState.value.params
        if (newParams == current) return

        if (recordUndo) {
            if (undoStack.size >= 30) undoStack.removeFirst()
            undoStack.addLast(current)
            redoStack.clear()
        }

        val onlyOverlayChanged = newParams.copy(
            filmEdgeActive = current.filmEdgeActive,
            filmEdgeFrameNumber = current.filmEdgeFrameNumber,
            dateBackActive = current.dateBackActive,
            dateBackText = current.dateBackText
        ) == current

        _uiState.update {
            it.copy(
                params = newParams,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }

        renderJob?.cancel()
        renderJob = viewModelScope.launch(engineDispatcher) {
            if (onlyOverlayChanged && reusableNativeBitmap != null) {
                val composited = FilmFrameCompositor.applyOverlaysIfNeeded(reusableNativeBitmap!!, newParams)
                _uiState.update { it.copy(renderedBitmap = composited) }
                return@launch
            }
            delay(16) // Coalesce high-frequency slider touch events (~60fps)
            val canReprint = newParams.onlyPrintLayerChangedComparedTo(lastRenderedParams)
            renderFrameInternal(newParams, preferReprint = canReprint)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.params
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                params = prev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch(engineDispatcher) {
            val canReprint = prev.onlyPrintLayerChangedComparedTo(lastRenderedParams)
            renderFrameInternal(prev, preferReprint = canReprint)
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.params
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                params = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch(engineDispatcher) {
            val canReprint = next.onlyPrintLayerChangedComparedTo(lastRenderedParams)
            renderFrameInternal(next, preferReprint = canReprint)
        }
    }

    fun copyRecipe() {
        copiedRecipe = _uiState.value.params
        _uiState.update {
            it.copy(
                hasCopiedRecipe = true,
                snackbarMessage = if (it.appLanguage.resolveIsChinese()) "已复制当前暗房冲印配方" else "Darkroom recipe copied"
            )
        }
    }

    fun pasteRecipe() {
        val recipe = copiedRecipe ?: return
        updateParams(recipe, recordUndo = true)
    }

    fun resetParams() {
        updateParams(DarkroomParams(), recordUndo = true)
    }

    fun autoSolveNeutralPrint() {
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isRenderingFrame = true) }
            try {
                val resp = JSONObject(SpektraNativeBridge.nativeAutoSolve("both"))
                if (resp.optBoolean("ok", true) && !resp.has("error")) {
                    val solvedObj = resp.optJSONObject("solved") ?: resp
                    val cur = _uiState.value.params
                    val next = cur.copy(
                        printExposure = solvedObj.optDouble("print_exposure", cur.printExposure),
                        yFilterShift = solvedObj.optDouble("y_filter_shift", cur.yFilterShift),
                        mFilterShift = solvedObj.optDouble("m_filter_shift", cur.mFilterShift)
                    )
                    updateParams(next, recordUndo = true)
                } else {
                    _uiState.update {
                        it.copy(
                            isRenderingFrame = false,
                            snackbarMessage = resp.optString("error", "Auto solve failed")
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isRenderingFrame = false) }
            }
        }
    }

    private suspend fun renderFrameInternal(params: DarkroomParams, preferReprint: Boolean) =
        withContext(engineDispatcher) {
            _uiState.update { it.copy(isRenderingFrame = true) }
            try {
                val jsonDelta = params.toEngineJson(previewLongEdge = 1440)
                var bmp = reusableNativeBitmap
                var resp = JSONObject(
                    SpektraNativeBridge.nativeRenderFrame(jsonDelta, "live", preferReprint, bmp)
                )
                if (!resp.optBoolean("ok", false)) {
                    _uiState.update {
                        it.copy(
                            isRenderingFrame = false,
                            snackbarMessage = resp.optString("error", "Render failed")
                        )
                    }
                    return@withContext
                }

                val outW = resp.optInt("width", 0)
                val outH = resp.optInt("height", 0)
                val filled = resp.optBoolean("bitmap_filled", false)

                if ((!filled || bmp == null || bmp.width != outW || bmp.height != outH) && outW > 0 && outH > 0) {
                    bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    reusableNativeBitmap = bmp
                    resp = JSONObject(
                        SpektraNativeBridge.nativeRenderFrame("", "live", true, bmp)
                    )
                }

                val finalBase = reusableNativeBitmap
                val composited = if (finalBase != null) {
                    FilmFrameCompositor.applyOverlaysIfNeeded(finalBase, params)
                } else {
                    null
                }

                val histObj = resp.optJSONObject("histogram")
                val hist = if (histObj != null) {
                    HistogramData(
                        r = jsonArrayToIntArray(histObj.optJSONArray("r")),
                        g = jsonArrayToIntArray(histObj.optJSONArray("g")),
                        b = jsonArrayToIntArray(histObj.optJSONArray("b")),
                        l = jsonArrayToIntArray(histObj.optJSONArray("l"))
                    )
                } else {
                    HistogramData()
                }

                lastRenderedParams = params
                _uiState.update {
                    it.copy(
                        isRenderingFrame = false,
                        renderedBitmap = composited,
                        histogram = hist,
                        lastRenderElapsedMs = resp.optDouble("elapsed_ms", 0.0),
                        lastRenderUsedReprint = resp.optBoolean("used_reprint", false)
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isRenderingFrame = false) }
            }
        }

    fun updateExportConfig(config: ExportConfig) {
        _uiState.update { it.copy(exportConfig = config) }
    }

    fun exportPrint(onCompleted: () -> Unit = {}) {
        val state = _uiState.value
        val config = state.exportConfig
        val params = state.params
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val context = getApplication<Application>()
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val stockSlug = params.filmStock.replace('_', '-')

                val (nativeFormat, bits, ext, mimeType) = when (config.format) {
                    "tiff16" -> Quadruple("tiff", 16, "tif", "image/tiff")
                    "tiff8" -> Quadruple("tiff", 8, "tif", "image/tiff")
                    "png" -> Quadruple("png", 16, "png", "image/png")
                    else -> Quadruple("jpeg", 8, "jpg", "image/jpeg")
                }

                val displayName = "Spektra_${stockSlug}_${timestamp}.$ext"
                val tempOutFile = File(exportDir, displayName)

                val resp = JSONObject(
                    SpektraNativeBridge.nativeExportImage(
                        destPath = tempOutFile.absolutePath,
                        format = nativeFormat,
                        colorSpace = config.colorSpace,
                        quality = config.jpegQuality,
                        bitsPerSample = bits,
                        renderTier = config.resolutionTier
                    )
                )

                if (!resp.optBoolean("ok", false)) {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            snackbarMessage = resp.optString("error", "Export failed")
                        )
                    }
                    return@launch
                }

                // If Film Edge or Date Back is active and format is JPEG or PNG, burn in overlays
                if (config.includeFilmEdgeAndDate &&
                    (params.filmEdgeActive || params.dateBackActive) &&
                    (nativeFormat == "jpeg" || nativeFormat == "png")
                ) {
                    val decoded = BitmapFactory.decodeFile(tempOutFile.absolutePath)
                    if (decoded != null) {
                        val composited = FilmFrameCompositor.applyOverlaysIfNeeded(decoded, params)
                        FileOutputStream(tempOutFile).use { out ->
                            val compressFormat = if (nativeFormat == "png") {
                                Bitmap.CompressFormat.PNG
                            } else {
                                Bitmap.CompressFormat.JPEG
                            }
                            composited.compress(compressFormat, config.jpegQuality, out)
                        }
                    }
                }

                val publishedUri = MediaStoreExporter.publishFileToGallery(
                    context = context,
                    sourceFile = tempOutFile,
                    displayName = displayName,
                    mimeType = mimeType
                )

                val isZh = _uiState.value.appLanguage.resolveIsChinese()
                val msg = if (publishedUri != null) {
                    if (isZh) {
                        "冲印完成：$displayName (${config.colorSpace}) 已保存至相册"
                    } else {
                        "Exported $displayName (${config.colorSpace}) to Gallery"
                    }
                } else {
                    if (isZh) "导出失败：无法写入系统相册" else "Export failed: could not write to MediaStore"
                }

                _uiState.update {
                    it.copy(isExporting = false, snackbarMessage = msg)
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onCompleted()
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isExporting = false, snackbarMessage = t.message ?: "Export error")
                }
            }
        }
    }

    fun runParitySelfTest() {
        val dir = resourcesDir ?: return
        viewModelScope.launch(engineDispatcher) {
            _uiState.update { it.copy(isRunningParity = true) }
            try {
                val jsonStr = SpektraNativeBridge.nativeRunParitySelfTest(dir.absolutePath)
                val obj = JSONObject(jsonStr)
                val gatesArr = obj.optJSONArray("checks") ?: obj.optJSONArray("gates")
                val gates = mutableListOf<ParityGateItem>()
                var allGatesPassed = true
                if (gatesArr != null) {
                    for (i in 0 until gatesArr.length()) {
                        val g = gatesArr.getJSONObject(i)
                        val passed = g.optBoolean("passed", false)
                        if (!passed) allGatesPassed = false
                        gates.add(
                            ParityGateItem(
                                id = g.optString("id", "gate_$i"),
                                title = g.optString("name", g.optString("title", "Gate $i")),
                                passed = passed,
                                maxError = g.optDouble("max_error", g.optDouble("max_err", 0.0)),
                                tolerance = g.optDouble("tolerance", 0.0),
                                elapsedMs = g.optDouble("elapsed_ms", 0.0),
                                detail = g.optString("detail", "")
                            )
                        )
                    }
                }
                val report = ParitySuiteReport(
                    overallPassed = obj.optBoolean("ok", false) && allGatesPassed && gates.isNotEmpty(),
                    gpuDeviceName = obj.optString("gpu_name", obj.optString("gpu_device", _uiState.value.gpuDeviceName)),
                    buildStamp = obj.optString("build_info", obj.optString("build", _uiState.value.engineBuildStamp)),
                    gates = gates
                )
                _uiState.update {
                    it.copy(isRunningParity = false, parityReport = report)
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isRunningParity = false,
                        snackbarMessage = t.message ?: "Parity self-test error"
                    )
                }
            }
        }
    }

    private fun saveCurrentToRecentSessions() {
        val state = _uiState.value
        val path = state.photoMetadata.filePath
        if (path.isBlank()) return
        val stockName = SpektraCatalog.findFilm(state.params.filmStock)
            .displayName(state.appLanguage.resolveIsChinese())
        val item = RecentSessionItem(
            filePath = path,
            displayName = state.photoMetadata.displayName.ifBlank { File(path).name },
            subtitle = stockName,
            thumbnail = state.renderedBitmap,
            savedParams = state.params
        )
        _uiState.update { cur ->
            val filtered = cur.recentSessions.filterNot { it.filePath == path }
            cur.copy(recentSessions = listOf(item) + filtered.take(9))
        }
    }

    private fun jsonArrayToIntArray(arr: org.json.JSONArray?): IntArray {
        if (arr == null) return IntArray(64)
        return IntArray(arr.length()) { idx -> arr.optInt(idx, 0) }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
