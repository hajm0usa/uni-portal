package ir.hajmousa.uniportal.data

import androidx.room.Entity

@Entity(tableName = "agenda_observed", primaryKeys = ["providerId", "remoteId"])
data class AgendaObservedEntity(val providerId: String, val remoteId: String, val isNew: Boolean)
