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

    @Test
    fun handlesEmptyButPresentNextSignal() {
        assertEquals("2", SharePagingPolicy.nextPageFromSignal(1, 100, "", 50))
        assertEquals("3", SharePagingPolicy.nextPageFromSignal(2, 1, "next", 50))
        assertNull(SharePagingPolicy.nextPageFromSignal(1, 100, null, 50))
        assertNull(SharePagingPolicy.nextPageFromSignal(1, 0, "", 50))
        assertNull(SharePagingPolicy.nextPageFromSignal(50, 100, "", 50))
    }

    @Test
    fun encodesOpaqueTokenWithPageLimit() {
        assertEquals(ShareTokenPagingPolicy.Cursor(1, ""), ShareTokenPagingPolicy.decode(null))
        assertEquals(
            ShareTokenPagingPolicy.Cursor(3, "opaque:token"),
            ShareTokenPagingPolicy.decode("3:opaque:token")
        )
        assertEquals("2:next-token", ShareTokenPagingPolicy.nextCursor(1, "", "next-token", 100))
        assertNull(ShareTokenPagingPolicy.nextCursor(1, "", "", 100))
        assertNull(ShareTokenPagingPolicy.nextCursor(2, "same", "same", 100))
        assertNull(ShareTokenPagingPolicy.nextCursor(100, "current", "next-token", 100))
    }

    @Test
    fun advancesRangeWhenEitherCategoryIsFull() {
        assertEquals(1, ShareRangePagingPolicy.begin(null))
        assertEquals(1, ShareRangePagingPolicy.begin("invalid"))
        assertEquals("201", ShareRangePagingPolicy.nextCursor(1, 200, listOf(200, 0), 20_000))
        assertEquals("401", ShareRangePagingPolicy.nextCursor(201, 200, listOf(0, 200), 20_000))
        assertNull(ShareRangePagingPolicy.nextCursor(1, 200, listOf(199, 199), 20_000))
        assertNull(ShareRangePagingPolicy.nextCursor(19_801, 200, listOf(200, 0), 20_000))
    }
}
