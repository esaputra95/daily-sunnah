package id.kasirq.dailysunnah.quran.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MushafPrefetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val page = inputData.getInt(KEY_PAGE, 0)
        if (page !in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE) return Result.failure()
        return try {
            MushafAssetRepository(applicationContext).preparePage(page)
            Result.success()
        } catch (error: java.io.IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (error: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_PAGE = "page"
        const val MAX_RETRIES = 5
    }
}

