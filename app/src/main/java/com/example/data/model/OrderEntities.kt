package com.example.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class OrderType {
    DINE_IN,
    TAKEOUT
}

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val orderType: String = OrderType.DINE_IN.name,
    val subtotal: Double,
    val grandTotal: Double,
    val paymentMethod: String = "CASH",
    val status: String = "COMPLETED"
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long,
    val menuItemId: Long,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val subtotal: Double,
    val unitLabel: String = ""
)

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)
