package com.example.ui.model

import com.example.data.model.MenuItemEntity

data class CartItem(
    val menuItem: MenuItemEntity,
    val quantity: Int
) {
    val subtotal: Double
        get() = menuItem.price * quantity
}
