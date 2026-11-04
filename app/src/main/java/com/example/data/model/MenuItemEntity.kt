package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Double,
    val unitLabel: String = "portion",
    val category: String = "General",
    val aliases: String = "",
    val isAvailable: Boolean = true,
    val displayOrder: Int = 0
)
