package com.example.data

import com.example.data.model.MenuItemEntity

object DefaultMenuData {
    val BASELINE_MENU = listOf(
        MenuItemEntity(
            name = "Banku",
            price = 5.0,
            unitLabel = "portion",
            category = "Main Dishes",
            aliases = "banku, bangu, bankoo, bankye",
            displayOrder = 1
        ),
        MenuItemEntity(
            name = "Fried Fish",
            price = 20.0,
            unitLabel = "piece",
            category = "Proteins",
            aliases = "fried fish, fish, tilapia, fried tilapia",
            displayOrder = 2
        ),
        MenuItemEntity(
            name = "Okro (1 scoop)",
            price = 2.0,
            unitLabel = "1 scoop",
            category = "Soups & Stews",
            aliases = "okro, okra, okro scoop, okro soup, okro stew",
            displayOrder = 3
        ),
        MenuItemEntity(
            name = "Chicken",
            price = 15.0,
            unitLabel = "piece",
            category = "Proteins",
            aliases = "chicken, fried chicken, grilled chicken, chiken",
            displayOrder = 4
        ),
        MenuItemEntity(
            name = "Rice (1 cup)",
            price = 6.0,
            unitLabel = "1 cup",
            category = "Main Dishes",
            aliases = "rice, cup of rice, plain rice, jollof, jollof rice",
            displayOrder = 5
        ),
        MenuItemEntity(
            name = "Plantain (1 piece)",
            price = 2.0,
            unitLabel = "1 piece",
            category = "Sides",
            aliases = "plantain, planten, fried plantain, dodo, kelewele",
            displayOrder = 6
        ),
        MenuItemEntity(
            name = "Sausage (1 piece)",
            price = 4.0,
            unitLabel = "1 piece",
            category = "Sides",
            aliases = "sausage, fried sausage, sosag, hotdog",
            displayOrder = 7
        ),
        MenuItemEntity(
            name = "Spaghetti (1 scoop)",
            price = 5.0,
            unitLabel = "1 scoop",
            category = "Sides",
            aliases = "spaghetti, spag, scoop of spaghetti, pasta, noodles",
            displayOrder = 8
        ),
        MenuItemEntity(
            name = "Goat Meat",
            price = 10.0,
            unitLabel = "portion",
            category = "Proteins",
            aliases = "goat meat, goat, chevon, mutton",
            displayOrder = 9
        ),
        MenuItemEntity(
            name = "Goat Feet",
            price = 15.0,
            unitLabel = "portion",
            category = "Proteins",
            aliases = "goat feet, goat foot, wampe, cow leg",
            displayOrder = 10
        ),
        MenuItemEntity(
            name = "Salmon",
            price = 15.0,
            unitLabel = "portion",
            category = "Proteins",
            aliases = "salmon, samon, smoked salmon",
            displayOrder = 11
        ),
        MenuItemEntity(
            name = "Tuna",
            price = 10.0,
            unitLabel = "portion",
            category = "Proteins",
            aliases = "tuna, tuna fish, tuna steak",
            displayOrder = 12
        ),
        MenuItemEntity(
            name = "Rice Balls",
            price = 5.0,
            unitLabel = "portion",
            category = "Main Dishes",
            aliases = "rice balls, rice ball, omo tuo, omotuo",
            displayOrder = 13
        )
    )
}
