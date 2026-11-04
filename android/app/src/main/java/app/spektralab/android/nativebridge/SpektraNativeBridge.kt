package app.spektralab.android.nativebridge

import android.content.Context
import android.graphics.Bitmap
import app.spektralab.android.BuildConfig
import java.io.File

object SpektraNativeBridge {
    init {
        System.loadLibrary("spektralab_jni")
    }

    external fun nativeInitEngine(resourcesDir: String): String
    external fun nativeGetMemoryReport(): String
    external fun nativeGenerateSamplePhoto(destPath: String, presetIndex: Int): String
    external fun nativeOpenPhoto(
        filePath: String,
        rawMode: String,
        temperatureK: Double,
        tint: Double,
        initialParamsJson: String
    ): String
    external fun nativeRenderFrame(
        paramsDeltaJson: String,
        tier: String,
        preferReprint: Boolean,
        targetBitmap: Bitmap?
    ): String
    external fun nativeRenderOriginalPreview(targetBitmap: Bitmap): Boolean
    external fun nativeAutoSolve(target: String): String
    external fun nativeExportImage(
        destPath: String,
        format: String,
        colorSpace: String,
        quality: Int,
        bitsPerSample: Int,
        renderTier: String
    ): String
    external fun nativeRunParitySelfTest(resourcesDir: String): String

    /**
     * Extracts baked engine profiles, constants, ICC profiles, and SPIR-V shaders from APK assets
     * into internal storage using the build-time generated `asset_manifest.txt`.
     */
    fun ensureResourcesExtracted(context: Context): File {
        val targetRoot = File(context.filesDir, "engine")
        val stampFile = File(targetRoot, ".extracted_version")
        val currentStamp = "${BuildConfig.VERSION_CODE}_${BuildConfig.VERSION_NAME}"

        if (stampFile.exists() &&
            stampFile.readText().trim() == currentStamp &&
            File(targetRoot, "spektrafilm_constants.bin").exists()
        ) {
            return targetRoot
        }

        val assetManager = context.assets
        val manifestLines = assetManager.open("engine/asset_manifest.txt")
            .bufferedReader()
            .use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (relativePath in manifestLines) {
            val outFile = File(targetRoot, relativePath)
            outFile.parentFile?.mkdirs()
            assetManager.open("engine/$relativePath").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        }

        stampFile.writeText(currentStamp)
        return targetRoot
    }
}
