package ir.hajmousa.uniportal.data

data class AgendaItem(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val startEpoch: Long, // Epoch seconds, matching course cache timestamps.
    val kind: Kind,
    val courseRemoteId: String? = null,
    val deepLink: String? = null,
) {
    enum class Kind { DEADLINE, EXAM, EVENT, OTHER }
}
