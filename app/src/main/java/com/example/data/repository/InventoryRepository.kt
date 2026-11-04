package com.example.data.repository

import com.example.data.DefaultMenuData
import com.example.data.dao.MenuItemDao
import com.example.data.model.MenuItemEntity
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val menuItemDao: MenuItemDao) {
    val allMenuItems: Flow<List<MenuItemEntity>> = menuItemDao.getAllMenuItems()
    val availableMenuItems: Flow<List<MenuItemEntity>> = menuItemDao.getAvailableMenuItems()

    suspend fun checkAndSeedDefaults() {
        if (menuItemDao.getCount() == 0) {
            menuItemDao.insertAll(DefaultMenuData.BASELINE_MENU)
        }
    }

    suspend fun insertItem(item: MenuItemEntity): Long = menuItemDao.insert(item)

    suspend fun updateItem(item: MenuItemEntity) = menuItemDao.update(item)

    suspend fun deleteItem(item: MenuItemEntity) = menuItemDao.delete(item)

    suspend fun resetToDefaults() {
        menuItemDao.deleteAll()
        menuItemDao.insertAll(DefaultMenuData.BASELINE_MENU)
    }

    suspend fun getItemById(id: Long): MenuItemEntity? = menuItemDao.getItemById(id)
}
