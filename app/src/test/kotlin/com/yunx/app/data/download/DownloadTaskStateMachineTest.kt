package com.yunx.app.data.download

import com.yunx.app.data.db.DownloadTaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskStateMachineTest {
    @Test
    fun allowsNormalDownloadLifecycle() {
        assertTrue(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_PENDING, DownloadTaskEntity.STATUS_DOWNLOADING
        ))
        assertTrue(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_DOWNLOADING, DownloadTaskEntity.STATUS_COMPLETED
        ))
    }

    @Test
    fun allowsPauseAndResume() {
        assertTrue(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_DOWNLOADING, DownloadTaskEntity.STATUS_PAUSED
        ))
        assertTrue(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_PAUSED, DownloadTaskEntity.STATUS_DOWNLOADING
        ))
    }

    @Test
    fun allowsRetryFromFailed() {
        assertTrue(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_FAILED, DownloadTaskEntity.STATUS_DOWNLOADING
        ))
    }

    @Test
    fun rejectsTerminalAndBackwardTransitions() {
        assertFalse(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_COMPLETED, DownloadTaskEntity.STATUS_DOWNLOADING
        ))
        assertFalse(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_FAILED, DownloadTaskEntity.STATUS_COMPLETED
        ))
        assertFalse(DownloadTaskStateMachine.canTransition(
            DownloadTaskEntity.STATUS_PAUSED, DownloadTaskEntity.STATUS_COMPLETED
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireTransitionRejectsInvalidTransition() {
        DownloadTaskStateMachine.requireTransition(
            DownloadTaskEntity.STATUS_COMPLETED, DownloadTaskEntity.STATUS_PAUSED
        )
    }
}
