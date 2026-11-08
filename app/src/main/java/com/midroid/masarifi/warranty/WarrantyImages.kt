package com.midroid.masarifi.warranty

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import java.io.File
import java.util.UUID

/** Invoice photos, copied into app-private storage (no storage permission needed), downscaled to keep backups small. */
class WarrantyImages(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "warranty_images"))

    fun file(name: String) = File(dir, name)

    /** Copies + downscales an image from a picker / camera uri. Returns the stored file name or null. */
    fun import(context: Context, uri: Uri, maxSide: Int = 2048): String? = runCatching {
        dir.mkdirs()
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = runCatching { cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1).let { o -> when (o) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 } } } ?: 0 }.getOrDefault(0)
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (rot != 0 || scale < 1f) {
            val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); postRotate(rot.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val name = "inv_${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun delete(names: Collection<String>) = names.forEach { runCatching { file(it).delete() } }

    fun read(name: String): ByteArray? = file(name).takeIf { it.isFile }?.readBytes()

    fun write(name: String, bytes: ByteArray) { dir.mkdirs(); file(safe(name)).writeBytes(bytes) }

    /** Removes files no warranty references any more. */
    fun prune(keep: Set<String>) = dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }

    companion object {
        fun safe(name: String) = name.substringAfterLast('/').substringAfterLast('\\')
    }
}
