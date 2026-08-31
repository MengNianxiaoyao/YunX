package com.yunx.app.data.task

import kotlinx.coroutines.CancellationException

data class BatchTaskResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val cancelled: Boolean
) {
    val processed: Int get() = succeeded + failed
}

object BatchTaskRunner {
    suspend fun <T> runSequentially(
        items: List<T>,
        shouldCancel: () -> Boolean = { false },
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        operation: suspend (T) -> Boolean
    ): BatchTaskResult {
        var succeeded = 0
        var failed = 0
        for (item in items) {
            if (shouldCancel()) break
            val success = try {
                operation(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (success) succeeded++ else failed++
            onProgress(succeeded + failed, items.size)
        }
        return BatchTaskResult(
            total = items.size,
            succeeded = succeeded,
            failed = failed,
            cancelled = succeeded + failed < items.size
        )
    }
}
