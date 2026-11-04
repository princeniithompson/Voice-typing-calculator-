package com.example.data.repository

import com.example.data.dao.OrderDao
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderWithItems
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class OrderRepository(private val orderDao: OrderDao) {
    val allOrdersWithItems: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()

    suspend fun createOrder(
        orderType: String,
        subtotal: Double,
        grandTotal: Double,
        paymentMethod: String,
        items: List<OrderItemEntity>
    ): Long {
        val lastNumber = orderDao.getMaxOrderNumber() ?: 1000
        val nextNumber = lastNumber + 1
        val order = OrderEntity(
            orderNumber = nextNumber,
            timestamp = System.currentTimeMillis(),
            orderType = orderType,
            subtotal = subtotal,
            grandTotal = grandTotal,
            paymentMethod = paymentMethod,
            status = "COMPLETED"
        )
        val orderId = orderDao.insertOrder(order)
        val itemsWithOrderId = items.map { it.copy(orderId = orderId) }
        orderDao.insertOrderItems(itemsWithOrderId)
        return orderId
    }

    suspend fun getOrderById(orderId: Long): OrderWithItems? =
        orderDao.getOrderWithItemsById(orderId)

    suspend fun deleteOrder(order: OrderEntity) = orderDao.deleteOrder(order)

    fun getTodaySales(): Flow<Double?> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return orderDao.getTotalSalesSince(calendar.timeInMillis)
    }

    fun getTodayOrderCount(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return orderDao.getOrderCountSince(calendar.timeInMillis)
    }
}
