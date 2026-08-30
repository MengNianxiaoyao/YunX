package com.yunx.app.data.network.adapters

import org.junit.Assert.assertEquals
import org.junit.Test

class Pan123PolicyTest {
    @Test
    fun encodesAndDecodesPagingCursor() {
        assertEquals(Pan123PagingPolicy.Page(1, "0"), Pan123PagingPolicy.decode(null))
        assertEquals(Pan123PagingPolicy.Page(2, "next-token"), Pan123PagingPolicy.decode("2|next-token"))
        assertEquals("3|next-token-2", Pan123PagingPolicy.encode(3, "next-token-2"))
    }

    @Test
    fun acceptsLegacyRawCursorAsFirstPage() {
        assertEquals(Pan123PagingPolicy.Page(1, "legacy-next"), Pan123PagingPolicy.decode("legacy-next"))
    }

    @Test
    fun mapsShareExpirationToDisplayType() {
        assertEquals(1, Pan123SharePolicy.expireType(null))
        assertEquals(2, Pan123SharePolicy.expireType(1))
        assertEquals(3, Pan123SharePolicy.expireType(7))
        assertEquals(4, Pan123SharePolicy.expireType(30))
    }
}
