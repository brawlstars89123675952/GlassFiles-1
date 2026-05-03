package com.glassfiles.data.ai

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Saves AI-generated media into the system gallery so the user sees them in
 * Photos / Gallery alongside their other pictures, videos, and audio files.
 *
 * On API 29+ we use scoped storage via `MediaStore` (`RELATIVE_PATH`) and let
 * the system place the file under `Pictures/GlassFiles_AI/`,
 * `Movies/GlassFiles_AI/`, or `Music/GlassFiles_AI/`. On API 28
 * (this app's `targetSdk`) we
 * fall back to the legacy file path under the public Pictures / Movies / Music dir
 * — the app already declares `MANAGE_EXTERNAL_STORAGE`, so the write is
 * permitted and the media scanner picks it up via `MediaStore.Images.Media`.
 */
object AiGallerySaver {

    private const val DIR = "GlassFiles_AI"

    /**
     * Copies [cacheFile] into `Pictures/GlassFiles_AI/[displayName]` and
     * returns the public Uri/path that the gallery can resolve.
     */
    fun saveImage(context: Context, cacheFile: File, displayName: String, mimeType: String = "image/png"): String {
        val targetName = sanitize(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "MediaStore openOutputStream returned null" }
                FileInputStream(cacheFile).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIR)
            .apply { if (!exists()) mkdirs() }
        val target = File(publicDir, targetName)
        cacheFile.copyTo(target, overwrite = true)
        // Tell MediaStore so the gallery indexes it.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, target.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        return target.absolutePath
    }

    /** Same as [saveImage] but stores under `Movies/GlassFiles_AI/`. */
    fun saveVideo(context: Context, cacheFile: File, displayName: String, mimeType: String = "video/mp4"): String {
        val targetName = sanitize(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$DIR")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "MediaStore openOutputStream returned null" }
                FileInputStream(cacheFile).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), DIR)
            .apply { if (!exists()) mkdirs() }
        val target = File(publicDir, targetName)
        cacheFile.copyTo(target, overwrite = true)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DATA, target.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return target.absolutePath
    }

    /** Same as [saveImage] but stores under `Music/GlassFiles_AI/`. */
    fun saveAudio(context: Context, cacheFile: File, displayName: String, mimeType: String = "audio/mpeg"): String {
        val targetName = sanitize(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$DIR")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "MediaStore openOutputStream returned null" }
                FileInputStream(cacheFile).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), DIR)
            .apply { if (!exists()) mkdirs() }
        val target = File(publicDir, targetName)
        cacheFile.copyTo(target, overwrite = true)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DATA, target.absolutePath)
            put(MediaStore.Audio.Media.DISPLAY_NAME, targetName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        }
        context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        return target.absolutePath
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._\\-]"), "_").take(120)
}
