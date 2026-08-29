package com.yunx.app.data.download

import com.yunx.app.data.db.DownloadTaskEntity

/**
 * 下载任务状态迁移规则的纯逻辑实现。
 * 所有持久化状态变更都应先经过这里，避免暂停/失败/完成之间出现非法覆盖。
 */
object DownloadTaskStateMachine {
    fun canTransition(from: Int, to: Int): Boolean {
        if (from == to) return true
        return when (from) {
            DownloadTaskEntity.STATUS_PENDING -> to == DownloadTaskEntity.STATUS_DOWNLOADING ||
                to == DownloadTaskEntity.STATUS_PAUSED || to == DownloadTaskEntity.STATUS_FAILED
            DownloadTaskEntity.STATUS_DOWNLOADING -> to == DownloadTaskEntity.STATUS_COMPLETED ||
                to == DownloadTaskEntity.STATUS_PAUSED || to == DownloadTaskEntity.STATUS_FAILED
            DownloadTaskEntity.STATUS_PAUSED -> to == DownloadTaskEntity.STATUS_DOWNLOADING ||
                to == DownloadTaskEntity.STATUS_FAILED
            DownloadTaskEntity.STATUS_FAILED -> to == DownloadTaskEntity.STATUS_DOWNLOADING
            DownloadTaskEntity.STATUS_COMPLETED -> false
            else -> false
        }
    }

    fun requireTransition(from: Int, to: Int) {
        require(canTransition(from, to)) {
            "非法下载状态迁移: ${DownloadTaskEntity.statusText(from)} -> ${DownloadTaskEntity.statusText(to)}"
        }
    }
}
