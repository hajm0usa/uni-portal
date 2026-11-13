package ir.hajmousa.uniportal.data

internal class MemoryCourseContentDao(val courses: MemoryCourseDao = MemoryCourseDao()) : CourseContentDao() {
    override fun page(providerId: String, remoteId: String): CourseContentEntity? {
        check(!courses.failStorage)
        return courses.contentRows[providerId to remoteId]
    }
    override fun syncState(providerId: String, section: String) = courses.syncState(providerId, section)
    override fun savePage(page: CourseContentEntity) {
        check(!courses.failStorage)
        courses.contentRows[page.providerId to page.remoteId] = page
    }
    override fun saveSyncState(state: SyncStateEntity) = courses.saveSyncState(state)
}
