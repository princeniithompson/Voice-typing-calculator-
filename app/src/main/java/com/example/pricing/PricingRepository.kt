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
            PriceItem(id = 1, name = "Banku", price = 5.0, category = "Main Dishes", aliases = listOf("banku", "bangu", "bankoo", "bankye")),
            PriceItem(id = 2, name = "Eba", price = 5.0, category = "Main Dishes", aliases = listOf("eba", "garri", "pinon")),
            PriceItem(id = 3, name = "Konkonte", price = 5.0, category = "Main Dishes", aliases = listOf("konkonte", "kokonte", "face the wall", "abitie")),
            PriceItem(id = 4, name = "Kelewele", price = 5.0, category = "Sides", aliases = listOf("kelewele", "spicy plantain")),
            PriceItem(id = 5, name = "Rice ball", price = 5.0, category = "Main Dishes", aliases = listOf("rice ball", "rice balls", "omo tuo", "omotuo")),
            PriceItem(id = 6, name = "Okro", price = 2.0, category = "Soups & Stews", aliases = listOf("okro", "okra", "okro soup", "okro stew", "okro scoop")),
            PriceItem(id = 7, name = "Meat", price = 10.0, category = "Proteins", aliases = listOf("meat", "beef", "cow meat")),
            PriceItem(id = 8, name = "Goat", price = 20.0, category = "Proteins", aliases = listOf("goat", "goat meat", "chevon")),
            PriceItem(id = 9, name = "Turkey", price = 20.0, category = "Proteins", aliases = listOf("turkey", "turkey wing", "turkey wings")),
            PriceItem(id = 10, name = "Chicken", price = 15.0, category = "Proteins", aliases = listOf("chicken", "fried chicken", "grilled chicken", "chiken")),
            PriceItem(id = 11, name = "Chicken in stew", price = 15.0, category = "Proteins", aliases = listOf("chicken in stew", "stew chicken", "chicken stew")),
            PriceItem(id = 12, name = "Salmon", price = 15.0, category = "Proteins", aliases = listOf("salmon", "smoked salmon", "samon")),
            PriceItem(id = 13, name = "Amani", price = 10.0, category = "Proteins", aliases = listOf("amani", "anchovy", "anchovies", "amane")),
            PriceItem(id = 14, name = "Tuna", price = 10.0, category = "Proteins", aliases = listOf("tuna", "tuna fish", "fried tuna")),
            PriceItem(id = 15, name = "Rice", price = 6.0, category = "Main Dishes", aliases = listOf("rice", "cup of rice", "plain rice", "white rice")),
            PriceItem(id = 16, name = "Gizzard", price = 5.0, category = "Sides", aliases = listOf("gizzard", "fried gizzard")),
            PriceItem(id = 17, name = "Beef sauce", price = 5.0, category = "Soups & Stews", aliases = listOf("beef sauce", "beef gravy", "sauce")),
            PriceItem(id = 18, name = "Dry Salad", price = 5.0, category = "Sides", aliases = listOf("dry salad", "salad")),
            PriceItem(id = 19, name = "Mix Salad", price = 5.0, category = "Sides", aliases = listOf("mix salad", "mixed salad", "coleslaw")),
            PriceItem(id = 20, name = "Spaghetti", price = 5.0, category = "Sides", aliases = listOf("spaghetti", "spag", "pasta", "noodles")),
            PriceItem(id = 21, name = "Beans", price = 5.0, category = "Main Dishes", aliases = listOf("beans", "red red", "gob3", "gobe")),
            PriceItem(id = 22, name = "Yam", price = 3.0, category = "Sides", aliases = listOf("yam", "fried yam")),
            PriceItem(id = 23, name = "Pack (takeaway)", price = 3.0, category = "Supplies", aliases = listOf("pack", "takeaway", "take away", "takeaway pack", "pack takeaway")),
            PriceItem(id = 24, name = "Fried Fish", price = 20.0, category = "Proteins", aliases = listOf("fried fish", "fish", "tilapia", "fried tilapia")),
            PriceItem(id = 25, name = "Plantain", price = 2.0, category = "Sides", aliases = listOf("plantain", "fried plantain", "dodo")),
            PriceItem(id = 26, name = "Sausage", price = 4.0, category = "Sides", aliases = listOf("sausage", "fried sausage", "hotdog")),
            PriceItem(id = 27, name = "Tilapia", price = 35.0, category = "Proteins", aliases = listOf("tilapia", "grilled tilapia")),
            PriceItem(id = 28, name = "Jollof Rice", price = 25.0, category = "Main Dishes", aliases = listOf("jollof", "jollof rice")),
            PriceItem(id = 29, name = "Waakye", price = 25.0, category = "Main Dishes", aliases = listOf("waakye", "wake"))
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
