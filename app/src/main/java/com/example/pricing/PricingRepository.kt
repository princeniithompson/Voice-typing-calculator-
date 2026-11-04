package com.example.pricing

import com.example.data.dao.MenuItemDao
import com.example.data.model.MenuItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PricingRepository(private val menuItemDao: MenuItemDao) {

    val allPriceItems: Flow<List<PriceItem>> = menuItemDao.getAllMenuItems().map { list ->
        list.map { it.toPriceItem() }
    }

    val availablePriceItems: Flow<List<PriceItem>> = menuItemDao.getAvailableMenuItems().map { list ->
        list.map { it.toPriceItem() }
    }

    @Volatile
    var cachedItems: List<PriceItem> = DEFAULT_PRICES
        private set

    suspend fun getItemsSnapshot(): List<PriceItem> {
        val entities = menuItemDao.getAvailableMenuItemsSync()
        val items = if (entities.isNotEmpty()) {
            entities.map { it.toPriceItem() }
        } else {
            DEFAULT_PRICES
        }
        cachedItems = items
        return items
    }

    suspend fun saveItem(item: PriceItem) {
        val entity = MenuItemEntity(
            id = item.id,
            name = item.name,
            price = item.price,
            category = item.category,
            aliases = item.aliases.joinToString(", "),
            unitLabel = item.unitLabel
        )
        if (item.id == 0L) {
            menuItemDao.insert(entity)
        } else {
            menuItemDao.update(entity)
        }
    }

    suspend fun deleteItem(item: PriceItem) {
        val entity = MenuItemEntity(
            id = item.id,
            name = item.name,
            price = item.price,
            category = item.category,
            aliases = item.aliases.joinToString(", "),
            unitLabel = item.unitLabel
        )
        menuItemDao.delete(entity)
    }

    companion object {
        val DEFAULT_PRICES = listOf(
            PriceItem(id = 1, name = "Banku", price = 5.0, category = "Main Dishes", aliases = listOf("banku", "bangu", "bankoo")),
            PriceItem(id = 2, name = "Fried Fish", price = 20.0, category = "Proteins", aliases = listOf("fried fish", "fish", "tilapia", "fried tilapia")),
            PriceItem(id = 3, name = "Okro (1 scoop)", price = 2.0, category = "Soups & Stews", aliases = listOf("okro", "okra", "okro soup")),
            PriceItem(id = 4, name = "Chicken", price = 15.0, category = "Proteins", aliases = listOf("chicken", "fried chicken", "grilled chicken")),
            PriceItem(id = 5, name = "Rice (1 cup)", price = 6.0, category = "Main Dishes", aliases = listOf("rice", "cup of rice", "plain rice")),
            PriceItem(id = 6, name = "Plantain (1 piece)", price = 2.0, category = "Sides", aliases = listOf("plantain", "fried plantain", "dodo")),
            PriceItem(id = 7, name = "Sausage (1 piece)", price = 4.0, category = "Sides", aliases = listOf("sausage", "fried sausage")),
            PriceItem(id = 8, name = "Jollof Rice", price = 25.0, category = "Main Dishes", aliases = listOf("jollof", "jollof rice")),
            PriceItem(id = 9, name = "Red Red", price = 20.0, category = "Main Dishes", aliases = listOf("red red", "beans and plantain")),
            PriceItem(id = 10, name = "Tilapia", price = 35.0, category = "Proteins", aliases = listOf("tilapia", "grilled tilapia")),
            PriceItem(id = 11, name = "Waakye", price = 25.0, category = "Main Dishes", aliases = listOf("waakye", "wake")),
            PriceItem(id = 12, name = "Kelewele", price = 12.0, category = "Sides", aliases = listOf("kelewele", "spicy plantain"))
        )
    }
}

private fun MenuItemEntity.toPriceItem(): PriceItem = PriceItem(
    id = id,
    name = name,
    price = price,
    category = category,
    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    unitLabel = unitLabel
)
