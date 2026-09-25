package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frontUri: String,
    val backUri: String,
    val layoutStyle: String = "STACKED", // "STACKED" or "SIDE_BY_SIDE"
    val filterType: String = "COLOR", // "COLOR", "BW", "GRAYSCALE"
    val createdAt: Long = System.currentTimeMillis()
)
