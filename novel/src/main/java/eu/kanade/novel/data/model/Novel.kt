package eu.kanade.novel.data.model

import tachiyomi.data.novel.model.toReleaseStatus as _toReleaseStatus

typealias Novel = tachiyomi.data.novel.model.Novel
typealias ReleaseStatus = tachiyomi.data.novel.model.ReleaseStatus

fun Int.toReleaseStatus() = _toReleaseStatus()
