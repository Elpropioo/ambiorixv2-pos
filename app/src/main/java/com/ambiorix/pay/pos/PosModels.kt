package com.ambiorix.pay.pos

data class CashierProfile(
    val id: String,
    val name: String,
    val isActive: Boolean = true,
    val pin: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val emoji: String = "🍦",
    val isDefault: Boolean = false
)

data class ProductVariant(
    val id: String,
    val name: String,
    val priceDelta: Long = 0L,
    val weightGrams: Int = 0,
    val coneUnits: Int = 0,
    val cupUnits: Int = 0
)

data class ToppingOption(
    val id: String,
    val name: String,
    val price: Long = 0L,
    val ingredientId: String? = null,
    val gramsRequired: Int = 0
)

data class RecipeItem(
    val ingredientId: String,
    val ingredientName: String,
    val quantity: Double,
    val unit: String = "g" // g, kg, ml, L, unidades
)

data class ComboItem(
    val productId: String,
    val productName: String,
    val quantity: Int = 1
)

data class Product(
    val id: String,
    val name: String,
    val description: String = "",
    val basePrice: Long,
    val baseCost: Long = 0L,
    val categoryId: String,
    val imagePath: String? = null,
    val emoji: String = "🍨",
    val badge: String? = null,
    val isActive: Boolean = true,
    val isCombo: Boolean = false,
    val stockQuantity: Double = 0.0,
    val minStockAlert: Double = 5.0,
    val unit: String = "unidades", // unidades, g, kg, ml, L
    val allowNegativeStock: Boolean = false,
    val recipe: List<RecipeItem> = emptyList(),
    val comboItems: List<ComboItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val variants: List<ProductVariant> = emptyList(),
    val allowedToppings: Boolean = true,
    val baseIngredientId: String? = null
)

data class CartItem(
    val cartItemId: String,
    val productId: String,
    val productName: String,
    val variantId: String = "",
    val variantName: String = "",
    val basePrice: Long,
    val variantPriceDelta: Long = 0L,
    val unitPrice: Long,
    val quantity: Int,
    val toppings: List<ToppingOption> = emptyList(),
    val totalPrice: Long,
    val estimatedCost: Long = 0L,
    val imagePath: String? = null
)

data class Ingredient(
    val id: String,
    val name: String,
    val category: String = "Insumos",
    val unit: String, // g, kg, ml, L, unidades
    val currentStock: Double,
    val minStockAlert: Double = 100.0,
    val unitCostCOP: Long = 0L,
    val isActive: Boolean = true,
    val batchCode: String = "LOT-GEN",
    val expiryDate: String = "31/12/2026"
)

data class InventoryMovement(
    val id: String,
    val itemId: String, // productId o ingredientId
    val itemName: String,
    val itemType: String = "INGREDIENT", // INGREDIENT o PRODUCT
    val previousStock: Double,
    val change: Double,
    val resultingStock: Double,
    val type: String, // PURCHASE, MANUAL_ADJUSTMENT, WASTE_MERMA, SALE_RECIPE, SALE, RETURN_COMPENSATION
    val reason: String,
    val saleId: String? = null,
    val date: Long = System.currentTimeMillis(),
    val dateFormatted: String,
    val userName: String
) {
    val productName: String get() = itemName
}

data class SaleRecord(
    val id: String,
    val saleNumber: String,
    val items: List<CartItem>,
    val subtotal: Long,
    val discount: Long = 0L,
    val total: Long,
    val paymentMethod: String, // CASH, NEQUI, SPLIT
    val cashAmount: Long = 0L,
    val nequiAmount: Long = 0L,
    val nequiSenderName: String? = null,
    val nequiChannel: String? = null,
    val timestamp: String,
    val dateStr: String,
    val cashierName: String,
    val status: String = "COMPLETED", // COMPLETED, CANCELLED, RETURNED
    val isInventoryDeducted: Boolean = true,
    val cogsCost: Long = 0L
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
    val cashIn: Long = 0L,
    val cashOut: Long = 0L,
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

object UnitConverter {
    fun getConversionFactor(fromUnit: String, toUnit: String): Double? {
        val from = fromUnit.trim().lowercase()
        val to = toUnit.trim().lowercase()
        if (from == to) return 1.0

        // Mass: g <-> kg
        if (from == "g" && to == "kg") return 0.001
        if (from == "kg" && to == "g") return 1000.0

        // Volume: ml <-> l
        if (from == "ml" && (to == "l" || to == "litro" || to == "litros")) return 0.001
        if ((from == "l" || from == "litro" || from == "litros") && to == "ml") return 1000.0

        // Units: unidades <-> un, u
        val isFromUnit = from == "unidades" || from == "un" || from == "u" || from == "unidad"
        val isToUnit = to == "unidades" || to == "un" || to == "u" || to == "unidad"
        if (isFromUnit && isToUnit) return 1.0

        return null
    }

    fun canConvert(fromUnit: String, toUnit: String): Boolean = getConversionFactor(fromUnit, toUnit) != null

    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        val factor = getConversionFactor(fromUnit, toUnit)
            ?: throw IllegalArgumentException("Unidades incompatibles: no se puede convertir de '$fromUnit' a '$toUnit' sin factor de conversión explícito.")
        return quantity * factor
    }
}

data class ExpenseRecord(
    val id: String,
    val amount: Long,
    val category: String, // Insumos, Reposición inventario, Transporte, Servicios, Mantenimiento, Otros
    val description: String,
    val paymentSource: String, // Caja efectivo, Nequi, Otro
    val cashierName: String,
    val notes: String = "",
    val dateStr: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val timestamp: Long = System.currentTimeMillis(),
    val isLinkedToInventory: Boolean = false,
    val status: String = "ACTIVE" // ACTIVE, CANCELLED
)

data class PaymentTransaction(
    val id: String,
    val method: String, // NEQUI, CASH, CARD, OTHER
    val amount: Long,
    val dateStr: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "CONFIRMED", // CONFIRMED, REVIEW, REJECTED, REFUNDED
    val saleId: String? = null,
    val saleNumber: String? = null,
    val cashierName: String,
    
    // Nequi specific
    val payerNameOrPhone: String? = null,
    val bankTransactionRef: String? = null, // e.g. "M982312" or null
    val smsEventId: String? = null, // e.g. "SMS-1729950001"
    val originInstallation: String = "Tienda Local", // "Tienda Local", "Tienda Remota: Pizzería"
    val nequiChannel: String? = null,
    
    // Cash specific
    val entryType: String = "SALE_PAYMENT", // SALE_PAYMENT, MANUAL_CASH_IN
    val notes: String = ""
) {
    val displayBankRef: String
        get() = if (bankTransactionRef.isNullOrBlank()) "No disponible en el SMS" else bankTransactionRef
}

data class ProductSaleOccurrence(
    val saleId: String,
    val saleNumber: String,
    val time: String,
    val cashierName: String,
    val quantity: Int,
    val unitPrice: Long,
    val totalPrice: Long
)

data class ProductSaleSummary(
    val productId: String,
    val productName: String,
    val totalQuantity: Int,
    val unitSalePrice: Long, // Historical snapshot price from sale
    val totalRevenue: Long,
    val discountsTotal: Long = 0L,
    val salesCount: Int,
    val isCombo: Boolean = false,
    val salesDetails: List<ProductSaleOccurrence> = emptyList()
)

data class DailyFinancialSummary(
    val dateStr: String,
    val totalNequi: Long,
    val nequiCount: Int,
    val nequiTransactions: List<PaymentTransaction>,
    val totalCash: Long,
    val cashCount: Int,
    val cashTransactions: List<PaymentTransaction>,
    val totalReceived: Long, // totalNequi + totalCash
    val totalExpenses: Long,
    val expensesCount: Int,
    val expenses: List<ExpenseRecord>,
    val movementBalance: Long, // totalReceived - totalExpenses
    val productsSold: List<ProductSaleSummary>,
    val totalUnitsSold: Int,
    val salesCount: Int
)

data class MonthDayIndicator(
    val dateStr: String,
    val dayNumber: Int,
    val hasSalesOrPayments: Boolean,
    val hasExpenses: Boolean,
    val totalReceived: Long,
    val totalExpenses: Long
)

