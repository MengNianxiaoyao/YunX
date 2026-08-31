package com.yunx.app.data.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTaskRunnerTest {
    @Test
    fun runsSequentiallyAndReportsFailures() = runBlocking {
        val order = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = BatchTaskRunner.runSequentially(
            items = listOf(1, 2, 3),
            onProgress = { completed, total -> progress += completed to total }
        ) { item ->
            order += item
            item != 2
        }

        assertEquals(listOf(1, 2, 3), order)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertFalse(result.cancelled)
    }

    @Test
    fun stopsBeforeNextItemWhenCancelled() = runBlocking {
        var cancel = false
        val result = BatchTaskRunner.runSequentially(
            items = listOf(1, 2, 3),
            shouldCancel = { cancel }
        ) {
            cancel = true
            true
        }

        assertEquals(1, result.processed)
        assertTrue(result.cancelled)
    }

    @Test
    fun canCancelBeforeFirstItem() = runBlocking {
        val result = BatchTaskRunner.runSequentially(
            items = listOf(1, 2),
            shouldCancel = { true }
        ) { true }

        assertEquals(0, result.processed)
        assertEquals(2, result.total)
        assertTrue(result.cancelled)
    }

    @Test
    fun convertsItemExceptionToFailure() = runBlocking {
        val result = BatchTaskRunner.runSequentially(listOf(1, 2)) { item ->
            if (item == 1) throw IllegalStateException("failed")
            true
        }

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
    }

    @Test
    fun propagatesCoroutineCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                BatchTaskRunner.runSequentially(listOf(1)) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
