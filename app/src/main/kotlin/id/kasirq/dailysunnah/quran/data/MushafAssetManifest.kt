package id.kasirq.dailysunnah.quran.data

import android.content.Context
import org.json.JSONObject

data class MushafManifestEntry(
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageBytes: Long,
    val imageSha256: String,
    val layoutBytes: Long,
    val layoutSha256: String,
)

object MushafAssetManifest {
    fun load(context: Context): Map<Int, MushafManifestEntry> {
        val root = context.assets.open("quran/mushaf-v1-manifest.json").bufferedReader().use { it.readText() }
            .let(::JSONObject)
        require(root.getInt("schemaVersion") == 1) { "Unsupported mushaf manifest schema" }
        require(root.getString("assetVersion") == MushafAssetRepository.ASSET_VERSION) { "Mushaf manifest targets another asset version" }
        require(root.getInt("pageCount") == MushafAssetRepository.MAX_PAGE) { "Mushaf manifest targets another edition" }
        val pages = root.getJSONArray("pages")
        require(pages.length() == MushafAssetRepository.MAX_PAGE) { "Manifest must cover every mushaf page" }
        return (0 until pages.length()).associate { index ->
            val item = pages.getJSONObject(index)
            val page = item.getInt("page")
            require(page == index + 1) { "Mushaf manifest pages are not sequential" }
            val entry = MushafManifestEntry(
                page = page,
                imageWidth = item.getInt("imageWidth"),
                imageHeight = item.getInt("imageHeight"),
                imageBytes = item.getLong("imageBytes"),
                imageSha256 = item.getString("imageSha256"),
                layoutBytes = item.getLong("layoutBytes"),
                layoutSha256 = item.getString("layoutSha256"),
            )
            require(entry.imageWidth > 0 && entry.imageHeight > 0 && entry.imageBytes > 0 && entry.layoutBytes > 0)
            require(SHA256.matches(entry.imageSha256) && SHA256.matches(entry.layoutSha256))
            page to entry
        }
    }

    private val SHA256 = Regex("^[a-f0-9]{64}$")
}
