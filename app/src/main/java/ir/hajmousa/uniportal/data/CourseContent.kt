package ir.hajmousa.uniportal.data

data class CourseSection(
    val providerId: String,
    val remoteId: String,
    val courseRemoteId: String,
    val title: String,
    val activities: List<CourseActivity>,
)

data class CourseActivity(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val kind: Kind,
    val deepLink: String?,
) {
    enum class Kind { FOLDER, ASSIGNMENT, LABEL, OTHER }
}

data class CourseFile(
    val providerId: String,
    val remoteId: String,
    val courseRemoteId: String,
    val activityRemoteId: String,
    val name: String,
    val url: String,
)
