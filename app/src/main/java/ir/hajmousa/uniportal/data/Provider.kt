package ir.hajmousa.uniportal.data

interface Provider {
    val id: String
    val session: ProviderSession
    val sources: List<DataSource<*>>
}

fun interface DataSource<T> {
    suspend fun fetch(): Result<List<T>>
}
