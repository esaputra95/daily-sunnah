package id.kasirq.dailysunnah.zakat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class ZakatSavingsRecord(
    val id: String,
    val createdAt: Long,
    val profileId: String,
    val savingsRupiah: BigDecimal,
    val goldPricePerGramRupiah: BigDecimal,
    val priceDate: String,
    val priceSource: String,
    val haulCompleted: Boolean,
    val ownershipConfirmed: Boolean,
    val status: ZakatSavingsStatus,
    val nisabRupiah: BigDecimal,
    val dueRupiah: BigDecimal,
) {
    companion object {
        fun from(
            savings: BigDecimal,
            goldPrice: BigDecimal,
            priceDate: String,
            priceSource: String,
            haulCompleted: Boolean,
            ownershipConfirmed: Boolean,
            result: ZakatSavingsResult,
        ) = ZakatSavingsRecord(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            profileId = ZakatSavingsCalculator.PROFILE_ID,
            savingsRupiah = savings,
            goldPricePerGramRupiah = goldPrice,
            priceDate = priceDate,
            priceSource = priceSource,
            haulCompleted = haulCompleted,
            ownershipConfirmed = ownershipConfirmed,
            status = result.status,
            nisabRupiah = result.nisabRupiah,
            dueRupiah = result.dueRupiah,
        )
    }
}

class ZakatSavingsHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<ZakatSavingsRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECORDS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching { item.toRecord() }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(record: ZakatSavingsRecord): List<ZakatSavingsRecord> {
        val updated = (listOf(record) + all().filterNot { it.id == record.id })
            .sortedByDescending { it.createdAt }
            .take(MAX_RECORDS)
        write(updated)
        return updated
    }

    @Synchronized
    fun delete(id: String): List<ZakatSavingsRecord> {
        val updated = all().filterNot { it.id == id }
        write(updated)
        return updated
    }

    private fun write(records: List<ZakatSavingsRecord>) {
        val array = JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().apply {
                    put("id", record.id)
                    put("createdAt", record.createdAt)
                    put("profileId", record.profileId)
                    put("savingsRupiah", record.savingsRupiah.toPlainString())
                    put("goldPricePerGramRupiah", record.goldPricePerGramRupiah.toPlainString())
                    put("priceDate", record.priceDate)
                    put("priceSource", record.priceSource)
                    put("haulCompleted", record.haulCompleted)
                    put("ownershipConfirmed", record.ownershipConfirmed)
                    put("status", record.status.name)
                    put("nisabRupiah", record.nisabRupiah.toPlainString())
                    put("dueRupiah", record.dueRupiah.toPlainString())
                })
            }
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun JSONObject.toRecord() = ZakatSavingsRecord(
        id = getString("id"),
        createdAt = getLong("createdAt"),
        profileId = getString("profileId"),
        savingsRupiah = BigDecimal(getString("savingsRupiah")),
        goldPricePerGramRupiah = BigDecimal(getString("goldPricePerGramRupiah")),
        priceDate = getString("priceDate"),
        priceSource = getString("priceSource"),
        haulCompleted = getBoolean("haulCompleted"),
        ownershipConfirmed = getBoolean("ownershipConfirmed"),
        status = ZakatSavingsStatus.valueOf(getString("status")),
        nisabRupiah = BigDecimal(getString("nisabRupiah")),
        dueRupiah = BigDecimal(getString("dueRupiah")),
    )

    companion object {
        private const val FILE_NAME = "zakat-savings-history"
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 50
    }
}
