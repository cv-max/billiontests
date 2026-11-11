package com.billiontests.app.analysis

import android.content.Context
import android.util.Log
import org.opencv.dnn.Net
import java.io.File

/**
 * OpenCV DNN's `readNetFromONNX` takes a filesystem path, so the model has to be materialised out
 * of the APK before it can be loaded. The copy happens once and is keyed on size so a shipped model
 * update replaces the cached one.
 */
object ModelStore {

    private const val TAG = "ModelStore"
    const val ASSET_NAME = "urinalysis_seg.onnx"

    /** Bump whenever a new model is shipped so the cached copy in filesDir is replaced. */
    private const val MODEL_VERSION = 1

    @Volatile
    private var net: Net? = null

    /** Loads (and caches) the segmentation net. Safe to call repeatedly; blocking on first call. */
    fun segNet(context: Context): Net = net ?: synchronized(this) {
        net ?: loadNet(context).also { net = it }
    }

    private fun loadNet(context: Context): Net {
        val path = ensureExtracted(context, ASSET_NAME)
        val t = System.currentTimeMillis()
        val loaded = SegDecoder.loadNet(path)
        Log.i(TAG, "loaded $ASSET_NAME in ${System.currentTimeMillis() - t} ms")
        return loaded
    }

    /**
     * Copies [assetName] into filesDir if missing or stale, and returns its absolute path.
     *
     * Staleness is tracked with a version stamp file rather than by comparing against the asset's
     * length: `assets.openFd` only works for uncompressed entries, and relying on it would couple
     * this to the `noCompress` build setting.
     */
    fun ensureExtracted(context: Context, assetName: String): String {
        val dst = File(context.filesDir, assetName)
        val stamp = File(context.filesDir, "$assetName.v")
        val fresh = dst.exists() &&
            dst.length() > 0 &&
            stamp.exists() &&
            stamp.readText().trim() == MODEL_VERSION.toString()
        if (fresh) return dst.absolutePath

        val tmp = File(context.filesDir, "$assetName.tmp")
        context.assets.open(assetName).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        dst.delete()
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            error("failed to move $assetName into ${dst.absolutePath}")
        }
        stamp.writeText(MODEL_VERSION.toString())
        Log.i(TAG, "extracted $assetName (${dst.length()} bytes)")
        return dst.absolutePath
    }
}
