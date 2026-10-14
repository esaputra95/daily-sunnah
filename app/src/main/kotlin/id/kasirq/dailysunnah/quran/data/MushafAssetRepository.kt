package id.kasirq.dailysunnah.quran.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class MushafAssetRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "quran-mushaf/$ASSET_VERSION")
    private val images = File(root, "webp").apply { mkdirs() }
    private val layouts = File(root, "layouts").apply { mkdirs() }
    private val ready = File(root, "ready").apply { mkdirs() }
    private val manifest by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MushafAssetManifest.load(appContext) }

    suspend fun loadPage(page: Int): MushafPageAsset = withContext(Dispatchers.IO) {
        requireValidPage(page)
        val pair = ensurePagePair(page)
        val layout = MushafLayoutParser.parse(pair.layout.readText(), page)
        val bitmap = BitmapFactory.decodeFile(pair.image.absolutePath)
            ?: throw IllegalStateException("WebP page $page could not be decoded")
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap.recycle()
            throw IllegalStateException("WebP page $page has invalid dimensions")
        }
        MushafPageAsset(page, bitmap, layout)
    }

    suspend fun preparePage(page: Int) = withContext(Dispatchers.IO) {
        requireValidPage(page)
        ensurePagePair(page)
        Unit
    }

    private suspend fun ensurePagePair(page: Int): PageFiles = PAGE_LOCKS.computeIfAbsent(page) { Mutex() }.withLock {
        val pageId = page.toString().padStart(3, '0')
        val image = File(images, "$pageId.webp")
        val layout = File(layouts, "$pageId.json")
        val marker = File(ready, "$pageId.ready")
        val expected = manifest[page] ?: throw IllegalStateException("Mushaf manifest has no page $page")
        if (isReady(page, image, layout, marker, expected)) return@withLock PageFiles(image, layout)
        marker.delete()
        if (page == MIN_PAGE && installBundledFirstPage(image, layout, marker, expected)) {
            return@withLock PageFiles(image, layout)
        }

        root.mkdirs()
        val requestId = UUID.randomUUID().toString()
        val imageTemp = File(images, "$pageId.$requestId.part")
        val layoutTemp = File(layouts, "$pageId.$requestId.part")
        val markerTemp = File(ready, "$pageId.$requestId.part")

        try {
            val (imageInfo, layoutInfo) = coroutineScope {
                val imageJob = async(Dispatchers.IO) {
                    downloadToFile("$CDN_BASE/webp/$pageId.webp", imageTemp, MAX_IMAGE_BYTES)
                }
                val layoutJob = async(Dispatchers.IO) {
                    downloadToFile("$CDN_BASE/layouts/$pageId.json", layoutTemp, MAX_LAYOUT_BYTES)
                }
                imageJob.await() to layoutJob.await()
            }

            require(imageInfo.bytes == expected.imageBytes && imageInfo.sha256 == expected.imageSha256) {
                "CDN WebP does not match the bundled manifest on page $page"
            }
            require(layoutInfo.bytes == expected.layoutBytes && layoutInfo.sha256 == expected.layoutSha256) {
                "CDN layout does not match the bundled manifest on page $page"
            }
            val parsedLayout = MushafLayoutParser.parse(layoutTemp.readText(), page)
            require(parsedLayout.renderViewBox.width > 0f)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageTemp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Downloaded WebP page $page is invalid" }
            require(bounds.outWidth == expected.imageWidth && bounds.outHeight == expected.imageHeight) {
                "CDN WebP dimensions differ from the bundled manifest on page $page"
            }
            val imageAspect = bounds.outWidth.toFloat() / bounds.outHeight
            val layoutAspect = parsedLayout.renderViewBox.width / parsedLayout.renderViewBox.height
            require(abs(imageAspect - layoutAspect) / layoutAspect < MAX_ASPECT_ERROR) {
                "WebP image and layout geometry do not match on page $page"
            }

            replaceFile(imageTemp, image)
            replaceFile(layoutTemp, layout)
            markerTemp.writeText("$ASSET_VERSION\n$page\n${imageInfo.sha256}\n${layoutInfo.sha256}\n")
            replaceFile(markerTemp, marker)
            return@withLock PageFiles(image, layout)
        } catch (error: Throwable) {
            imageTemp.delete()
            layoutTemp.delete()
            markerTemp.delete()
            throw error
        }
    }

    private fun isReady(page: Int, image: File, layout: File, marker: File, expected: MushafManifestEntry): Boolean {
        if (!image.isFile || !layout.isFile || !marker.isFile) return false
        return runCatching {
            val markerLines = marker.readLines()
            if (markerLines.size < 4 || markerLines[0] != ASSET_VERSION || markerLines[1].toInt() != page) {
                return@runCatching false
            }
            if (image.length() != expected.imageBytes || layout.length() != expected.layoutBytes) return@runCatching false
            if (markerLines[2] != expected.imageSha256 || markerLines[3] != expected.layoutSha256) return@runCatching false
            if (markerLines[2] != sha256(image) || markerLines[3] != sha256(layout)) return@runCatching false
            MushafLayoutParser.parse(layout.readText(), page)
            true
        }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun installBundledFirstPage(
        image: File,
        layout: File,
        marker: File,
        expected: MushafManifestEntry,
    ): Boolean {
        val requestId = UUID.randomUUID().toString()
        val imageTemp = File(images, "001.$requestId.bootstrap")
        val layoutTemp = File(layouts, "001.$requestId.bootstrap")
        val markerTemp = File(ready, "001.$requestId.bootstrap")
        return try {
            val imageInfo = copyAssetToFile("quran/bootstrap/001.webp", imageTemp)
            val layoutInfo = copyAssetToFile("quran/bootstrap/001.json", layoutTemp)
            if (imageInfo.bytes != expected.imageBytes || imageInfo.sha256 != expected.imageSha256 ||
                layoutInfo.bytes != expected.layoutBytes || layoutInfo.sha256 != expected.layoutSha256
            ) return false

            val parsedLayout = MushafLayoutParser.parse(layoutTemp.readText(), MIN_PAGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageTemp.absolutePath, bounds)
            if (bounds.outWidth != expected.imageWidth || bounds.outHeight != expected.imageHeight) return false
            val imageAspect = bounds.outWidth.toFloat() / bounds.outHeight
            val layoutAspect = parsedLayout.renderViewBox.width / parsedLayout.renderViewBox.height
            if (abs(imageAspect - layoutAspect) / layoutAspect >= MAX_ASPECT_ERROR) return false

            replaceFile(imageTemp, image)
            replaceFile(layoutTemp, layout)
            markerTemp.writeText("$ASSET_VERSION\n$MIN_PAGE\n${imageInfo.sha256}\n${layoutInfo.sha256}\n")
            replaceFile(markerTemp, marker)
            true
        } catch (_: Exception) {
            false
        } finally {
            imageTemp.delete()
            layoutTemp.delete()
            markerTemp.delete()
        }
    }

    private fun copyAssetToFile(assetPath: String, destination: File): DownloadInfo {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        appContext.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    total += count
                }
                output.fd.sync()
            }
        }
        return DownloadInfo(digest.digest().joinToString("") { byte -> "%02x".format(byte) }, total)
    }

    private fun downloadToFile(url: String, destination: File, maximumBytes: Long): DownloadInfo {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val message = "CDN request failed ($responseCode) for $url"
                if (responseCode == 408 || responseCode == 429 || responseCode >= 500) {
                    throw IOException(message)
                }
                throw IllegalStateException(message)
            }
            val advertisedLength = connection.contentLengthLong
            require(advertisedLength <= maximumBytes || advertisedLength < 0) { "Asset exceeds size limit: $url" }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maximumBytes) { "Asset exceeds size limit: $url" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(total > 0) { "CDN returned an empty asset: $url" }
            return DownloadInfo(digest.digest().joinToString("") { byte -> "%02x".format(byte) }, total)
        } finally {
            connection.disconnect()
        }
    }

    private fun replaceFile(source: File, destination: File) {
        if (destination.exists() && !destination.delete()) {
            throw IllegalStateException("Could not replace ${destination.name}")
        }
        if (!source.renameTo(destination)) {
            throw IllegalStateException("Could not install ${destination.name}")
        }
    }

    private fun requireValidPage(page: Int) {
        require(page in MIN_PAGE..MAX_PAGE) { "Page must be between $MIN_PAGE and $MAX_PAGE" }
    }

    private data class PageFiles(val image: File, val layout: File)
    private data class DownloadInfo(val sha256: String, val bytes: Long)

    companion object {
        const val ASSET_VERSION = "v1"
        const val MIN_PAGE = 1
        const val MAX_PAGE = 604
        const val CDN_BASE = "https://quran-cdn.kasirq.id/quran-mushaf/$ASSET_VERSION"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
        private const val MAX_LAYOUT_BYTES = 2L * 1024 * 1024
        private const val MAX_ASPECT_ERROR = 0.02f
        private val PAGE_LOCKS = ConcurrentHashMap<Int, Mutex>()
    }
}

