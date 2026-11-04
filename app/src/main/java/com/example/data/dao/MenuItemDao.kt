package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items ORDER BY displayOrder ASC, id ASC")
    fun getAllMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE isAvailable = 1 ORDER BY displayOrder ASC, id ASC")
    fun getAvailableMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE isAvailable = 1 ORDER BY displayOrder ASC, id ASC")
    suspend fun getAvailableMenuItemsSync(): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): MenuItemEntity?

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MenuItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    @Update
    suspend fun update(item: MenuItemEntity)

    @Delete
    suspend fun delete(item: MenuItemEntity)

    @Query("DELETE FROM menu_items")
    suspend fun deleteAll()
}
