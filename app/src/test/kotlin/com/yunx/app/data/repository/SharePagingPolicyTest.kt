package com.yunx.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharePagingPolicyTest {
    @Test
    fun parsesPageCursorDefensively() {
        assertEquals(1, SharePagingPolicy.pageNumber(null))
        assertEquals(1, SharePagingPolicy.pageNumber("invalid"))
        assertEquals(1, SharePagingPolicy.pageNumber("-3"))
        assertEquals(7, SharePagingPolicy.pageNumber("7"))
    }

    @Test
    fun continuesOnlyAfterFullPage() {
        assertEquals("2", SharePagingPolicy.nextPageCursor(1, 100, 100, 100))
        assertEquals("4", SharePagingPolicy.nextPageCursor(3, 50, 50, 100))
        assertNull(SharePagingPolicy.nextPageCursor(1, 99, 100, 100))
        assertNull(SharePagingPolicy.nextPageCursor(3, 49, 50, 100))
        assertNull(SharePagingPolicy.nextPageCursor(1, 0, 100, 100))
    }

    @Test
    fun stopsAtMaximumPage() {
        assertNull(SharePagingPolicy.nextPageCursor(100, 100, 100, 100))
    }
}
