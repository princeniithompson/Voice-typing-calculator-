package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderWithItemsById(orderId: Long): OrderWithItems?

    @Query("SELECT MAX(orderNumber) FROM orders")
    suspend fun getMaxOrderNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    @Query("SELECT SUM(grandTotal) FROM orders WHERE timestamp >= :sinceTimestamp")
    fun getTotalSalesSince(sinceTimestamp: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM orders WHERE timestamp >= :sinceTimestamp")
    fun getOrderCountSince(sinceTimestamp: Long): Flow<Int>
}
