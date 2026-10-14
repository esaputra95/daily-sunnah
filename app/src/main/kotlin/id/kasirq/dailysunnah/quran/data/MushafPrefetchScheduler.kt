package id.kasirq.dailysunnah.quran.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

class MushafPrefetchScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun prepareNeighborhood(activePage: Int) {
        val pages = (activePage - NEIGHBOR_COUNT..activePage + NEIGHBOR_COUNT)
            .filter { it in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE }
        enqueue(pages, prioritize = true)
    }

    fun prepareSurahStarts(pages: List<Int>) {
        enqueue(pages.distinct(), prioritize = false)
    }

    private fun enqueue(pages: List<Int>, prioritize: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        pages.forEach { page ->
            val builder = OneTimeWorkRequestBuilder<MushafPrefetchWorker>()
                .setInputData(workDataOf(MushafPrefetchWorker.KEY_PAGE to page))
                .setConstraints(constraints)
                .addTag("mushaf-prefetch")
            if (prioritize) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            val request = builder.build()
            workManager.enqueueUniqueWork(
                "mushaf-${MushafAssetRepository.ASSET_VERSION}-$page",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    companion object {
        const val NEIGHBOR_COUNT = 5
    }
}

