package com.ambiorix.pay.pos

data class ProductVariant(
    val id: String,
    val name: String,
    val priceDelta: Long,
    val weightGrams: Int = 0,
    val coneUnits: Int = 0,
    val cupUnits: Int = 0
)

data class ToppingOption(
    val id: String,
    val name: String,
    val price: Long,
    val ingredientId: String? = null,
    val gramsRequired: Int = 0
)

data class Product(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val basePrice: Long,
    val baseCost: Long,
    val emoji: String,
    val badge: String? = null,
    val variants: List<ProductVariant>,
    val allowedToppings: Boolean = true,
    val baseIngredientId: String
)

data class CartItem(
    val cartItemId: String,
    val productId: String,
    val productName: String,
    val variantId: String,
    val variantName: String,
    val basePrice: Long,
    val variantPriceDelta: Long,
    val unitPrice: Long,
    val quantity: Int,
    val toppings: List<ToppingOption> = emptyList(),
    val totalPrice: Long,
    val estimatedCost: Long
)

data class Ingredient(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val currentStock: Double,
    val minStockAlert: Double,
    val unitCostCOP: Long,
    val batchCode: String,
    val expiryDate: String
)

data class SaleRecord(
    val id: String,
    val saleNumber: String,
    val items: List<CartItem>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paymentMethod: String, // CASH, NEQUI, SPLIT
    val cashAmount: Long = 0,
    val nequiAmount: Long = 0,
    val nequiSenderName: String? = null,
    val nequiChannel: String? = null,
    val timestamp: String,
    val dateStr: String,
    val cashierName: String,
    val status: String = "COMPLETED",
    val cogsCost: Long = 0
)

data class CashMovement(
    val id: String,
    val concept: String,
    val amount: Long,
    val type: String, // EXPENSE, IN, OUT
    val time: String
)

data class CashShift(
    val id: String,
    val cashierName: String,
    val openedAt: String,
    val initialCash: Long,
    val status: String = "OPEN",
    val cashIn: Long = 0,
    val cashOut: Long = 0,
    val expenses: List<CashMovement> = emptyList(),
    val expectedCash: Long? = null,
    val countedCash: Long? = null,
    val difference: Long? = null,
    val closedAt: String? = null
)

data class ProtocolMessageLog(
    val id: String,
    val opId: String,
    val fromStore: String,
    val toStore: String,
    val targetPhone: String,
    val cmd: String,
    val amount: Long,
    val seq: Int,
    val timestamp: String,
    val rawPayload: String,
    val hmacVerified: Boolean,
    val smsDispatched: Boolean,
    val replyReceived: Boolean,
    val operationConfirmed: Boolean,
    val status: String
)

enum class ProtocolState {
    DRAFT,
    RESERVE_PENDING,
    RESERVED,
    WAITING_PAYMENT,
    PAYMENT_MATCHED,
    CANCEL_PENDING,
    CANCELLED,
    EXPIRED,
    COORDINATION_FAILED,
    UNASSIGNED,
    TRANSFER_REQUESTED,
    ASSIGNED_LOCAL,
    CONFLICT,
    RESOLVED
}

enum class PosRole {
    CASHIER,
    OWNER
}
