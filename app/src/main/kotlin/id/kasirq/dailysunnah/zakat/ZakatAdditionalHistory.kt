package id.kasirq.dailysunnah.zakat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ZakatAdditionalRecord(
    val id: String,
    val createdAt: Long,
    val category: String,
    val status: String,
    val summary: String,
)

class ZakatAdditionalHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<ZakatAdditionalRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECORDS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                ZakatAdditionalRecord(
                    id = item.getString("id"),
                    createdAt = item.getLong("createdAt"),
                    category = item.getString("category"),
                    status = item.getString("status"),
                    summary = item.getString("summary"),
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(category: String, status: String, summary: String): List<ZakatAdditionalRecord> {
        val record = ZakatAdditionalRecord(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            category = category,
            status = status,
            summary = summary,
        )
        val updated = (listOf(record) + all()).take(MAX_RECORDS)
        val array = JSONArray().apply {
            updated.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("createdAt", entry.createdAt)
                    put("category", entry.category)
                    put("status", entry.status)
                    put("summary", entry.summary)
                })
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
        return updated
    }

    @Synchronized
    fun delete(id: String): List<ZakatAdditionalRecord> {
        val updated = all().filterNot { it.id == id }
        val array = JSONArray().apply {
            updated.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("createdAt", entry.createdAt)
                    put("category", entry.category)
                    put("status", entry.status)
                    put("summary", entry.summary)
                })
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
        return updated
    }

    companion object {
        private const val FILE_NAME = "zakat-additional-history"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 50
    }
}
