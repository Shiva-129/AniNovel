package tachiyomi.data.novel.model

enum class ReleaseStatus {
    Ongoing, Completed, Paused, Dropped, Stubbed;
}

fun String.toReleaseStatus(): ReleaseStatus? = when (this.lowercase().trim()) {
    "ongoing", "on-going", "on_going", "releasing" -> ReleaseStatus.Ongoing
    "completed", "complete", "done" -> ReleaseStatus.Completed
    "hiatus", "paused", "pause" -> ReleaseStatus.Paused
    "dropped", "drop" -> ReleaseStatus.Dropped
    "stub", "stubbed" -> ReleaseStatus.Stubbed
    else -> null
}

fun Int.toReleaseStatus(): ReleaseStatus? = when (this) {
    1 -> ReleaseStatus.Ongoing
    2 -> ReleaseStatus.Completed
    3 -> ReleaseStatus.Paused
    4 -> ReleaseStatus.Dropped
    5 -> ReleaseStatus.Stubbed
    else -> null
}
