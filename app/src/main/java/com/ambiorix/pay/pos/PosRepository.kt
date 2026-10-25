package com.ambiorix.pay.pos

import android.content.Context
import android.telephony.SmsManager
import com.ambiorix.pay.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PosRepository private constructor(private val context: Context) {

    private val settingsRepo = SettingsRepository.getInstance(context)

    // Ingredients
    private val _ingredients = MutableStateFlow<List<Ingredient>>(initialIngredients())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    // Sales History
    private val _sales = MutableStateFlow<List<SaleRecord>>(emptyList())
    val sales: StateFlow<List<SaleRecord>> = _sales.asStateFlow()

    // Current Cash Shift
    private val _cashShift = MutableStateFlow<CashShift>(
        CashShift(
            id = "shift-${System.currentTimeMillis()}",
            cashierName = "Laura Gómez",
            openedAt = getCurrentTime(),
            initialCash = 150000L,
            status = "OPEN"
        )
    )
    val cashShift: StateFlow<CashShift> = _cashShift.asStateFlow()

    // Protocol State Machine
    private val _protocolState = MutableStateFlow(ProtocolState.DRAFT)
    val protocolState: StateFlow<ProtocolState> = _protocolState.asStateFlow()

    private val _activeOpId = MutableStateFlow<String?>(null)
    val activeOpId: StateFlow<String?> = _activeOpId.asStateFlow()

    private val _smsLogs = MutableStateFlow<List<ProtocolMessageLog>>(emptyList())
    val smsLogs: StateFlow<List<ProtocolMessageLog>> = _smsLogs.asStateFlow()

    private val _unassignedPayments = MutableStateFlow<List<SaleRecord>>(emptyList())
    val unassignedPayments: StateFlow<List<SaleRecord>> = _unassignedPayments.asStateFlow()

    fun resetIngredients() {
        _ingredients.value = initialIngredients()
    }

    fun completeSale(
        items: List<CartItem>,
        subtotal: Long,
        discount: Long,
        total: Long,
        paymentMethod: String,
        cashierName: String,
        senderName: String? = null,
        channel: String? = null
    ): SaleRecord {
        val saleNo = "V-${(_sales.value.size + 101).toString().padStart(5, '0')}"
        val cogs = items.sumOf { it.estimatedCost }

        val sale = SaleRecord(
            id = "sale-${System.currentTimeMillis()}",
            saleNumber = saleNo,
            items = items,
            subtotal = subtotal,
            discount = discount,
            total = total,
            paymentMethod = paymentMethod,
            cashAmount = if (paymentMethod == "CASH") total else 0L,
            nequiAmount = if (paymentMethod == "NEQUI") total else 0L,
            nequiSenderName = senderName,
            nequiChannel = channel,
            timestamp = getCurrentTime(),
            dateStr = getCurrentDate(),
            cashierName = cashierName,
            status = "COMPLETED",
            cogsCost = cogs
        )

        // Deduct inventory by recipe
        deductStockForSale(items)

        // Update sales
        _sales.value = listOf(sale) + _sales.value

        // Update cash shift if cash
        if (paymentMethod == "CASH") {
            _cashShift.value = _cashShift.value.copy(
                cashIn = _cashShift.value.cashIn + total
            )
        }

        return sale
    }

    private fun deductStockForSale(items: List<CartItem>) {
        val currentList = _ingredients.value.toMutableList()
        for (item in items) {
            val prod = defaultProducts().find { it.id == item.productId }
            val variant = prod?.variants?.find { it.id == item.variantId }

            // Deduct base ice cream
            if (prod != null && variant != null && variant.weightGrams > 0) {
                val idx = currentList.indexOfFirst { it.id == prod.baseIngredientId }
                if (idx >= 0) {
                    val ing = currentList[idx]
                    val toDeduct = variant.weightGrams * item.quantity
                    currentList[idx] = ing.copy(currentStock = maxOf(0.0, ing.currentStock - toDeduct))
                }
            }

            // Deduct cone unit
            if (variant != null && variant.coneUnits > 0) {
                val idx = currentList.indexOfFirst { it.id == "ing-6" }
                if (idx >= 0) {
                    val ing = currentList[idx]
                    currentList[idx] = ing.copy(currentStock = maxOf(0.0, ing.currentStock - (variant.coneUnits * item.quantity)))
                }
            }

            // Deduct cup unit
            if (variant != null && variant.cupUnits > 0) {
                val idx = currentList.indexOfFirst { it.id == "ing-7" }
                if (idx >= 0) {
                    val ing = currentList[idx]
                    currentList[idx] = ing.copy(currentStock = maxOf(0.0, ing.currentStock - (variant.cupUnits * item.quantity)))
                }
            }

            // Deduct toppings
            for (t in item.toppings) {
                if (t.ingredientId != null && t.gramsRequired > 0) {
                    val idx = currentList.indexOfFirst { it.id == t.ingredientId }
                    if (idx >= 0) {
                        val ing = currentList[idx]
                        currentList[idx] = ing.copy(currentStock = maxOf(0.0, ing.currentStock - (t.gramsRequired * item.quantity)))
                    }
                }
            }
        }
        _ingredients.value = currentList
    }

    fun addCashExpense(concept: String, amount: Long) {
        val expense = CashMovement(
            id = "exp-${System.currentTimeMillis()}",
            concept = concept,
            amount = amount,
            type = "EXPENSE",
            time = getCurrentTime()
        )
        _cashShift.value = _cashShift.value.copy(
            expenses = _cashShift.value.expenses + expense
        )
    }

    fun performBlindClose(countedCash: Long): CashShift {
        val current = _cashShift.value
        val totalExpenses = current.expenses.sumOf { it.amount }
        val expected = current.initialCash + current.cashIn - totalExpenses
        val diff = countedCash - expected

        val closed = current.copy(
            status = "CLOSED",
            expectedCash = expected,
            countedCash = countedCash,
            difference = diff,
            closedAt = getCurrentTime()
        )
        _cashShift.value = closed
        return closed
    }

    // P2P SMS Dispatcher
    fun sendProtocolCommand(cmd: String, amount: Long): Boolean {
        val settings = settingsRepo.getSettings()
        val targetPhone = settings.peerStorePhone.trim()

        if (!settings.peerStoreEnabled || targetPhone.isEmpty()) {
            return false
        }

        val opId = _activeOpId.value ?: "op-${System.currentTimeMillis().toString().takeLast(6)}"
        _activeOpId.value = opId

        val seq = _smsLogs.value.size + 1
        val ts = System.currentTimeMillis()
        val prePayload = "APOS|v1|msg-$ts|$opId|$cmd|$seq|${settings.localStoreId}|${settings.peerStoreId}|$ts|120|$amount"
        val hmac = generateHmacSha256(prePayload, settings.peerSharedSecret)
        val fullPayload = "$prePayload|$hmac"

        // Actual SMS sending attempt
        var sent = false
        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            smsManager.sendTextMessage(targetPhone, null, fullPayload, null, null)
            sent = true
        } catch (e: Exception) {
            e.printStackTrace()
            sent = false
        }

        val log = ProtocolMessageLog(
            id = "msg-$ts",
            opId = opId,
            fromStore = settings.localStoreName,
            toStore = settings.peerStoreName,
            targetPhone = targetPhone,
            cmd = cmd,
            amount = amount,
            seq = seq,
            timestamp = getCurrentTime(),
            rawPayload = fullPayload,
            hmacVerified = true,
            smsDispatched = sent,
            replyReceived = false,
            operationConfirmed = false,
            status = if (sent) "DISPATCHED" else "FAILED"
        )
        _smsLogs.value = listOf(log) + _smsLogs.value

        if (cmd == "RESERVE") {
            _protocolState.value = ProtocolState.RESERVE_PENDING
        } else if (cmd == "CANCEL") {
            _protocolState.value = ProtocolState.CANCEL_PENDING
        }

        return sent
    }

    fun setProtocolState(state: ProtocolState) {
        _protocolState.value = state
        if (state == ProtocolState.DRAFT || state == ProtocolState.CANCELLED || state == ProtocolState.RESOLVED) {
            _activeOpId.value = null
        }
    }

    private fun generateHmacSha256(data: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "hmac_mock_sig"
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    companion object {
        @Volatile
        private var INSTANCE: PosRepository? = null

        fun getInstance(context: Context): PosRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PosRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun defaultCategories(): List<Pair<String, String>> = listOf(
            "todos" to "🍦 Todos",
            "helados" to "🍨 Helados",
            "gourmet" to "✨ Gourmet",
            "paletas" to "🍧 Paletas",
            "copas" to "🧇 Copas",
            "bebidas" to "🥤 Bebidas"
        )

        fun defaultToppings(): List<ToppingOption> = listOf(
            ToppingOption("top-1", "Chispas de Chocolate", 1500L, "ing-8", 15),
            ToppingOption("top-2", "Salsa de Arequipe", 1500L, "ing-9", 25),
            ToppingOption("top-3", "Crema Chantilly", 2000L, "ing-10", 30),
            ToppingOption("top-4", "Lluvia de Maní", 1500L, null, 15),
            ToppingOption("top-5", "Cereza Marrasquino", 1000L, null, 10),
            ToppingOption("top-6", "Barquillo Pirulito", 1000L, null, 1)
        )

        fun defaultProducts(): List<Product> = listOf(
            Product(
                id = "prod-1",
                categoryId = "helados",
                name = "Vainilla Francesa",
                description = "Vainilla cremosa de Madagascar",
                basePrice = 6000L,
                baseCost = 1800L,
                emoji = "🍦",
                badge = "Top",
                baseIngredientId = "ing-1",
                variants = listOf(
                    ProductVariant("var-1", "1 Bola (80g)", 0L, weightGrams = 80, coneUnits = 1),
                    ProductVariant("var-2", "2 Bolas (160g)", 5000L, weightGrams = 160, coneUnits = 1),
                    ProductVariant("var-3", "Vaso 2 Bolas", 5000L, weightGrams = 160, cupUnits = 1),
                    ProductVariant("var-4", "Litro (1000g)", 22000L, weightGrams = 1000, cupUnits = 1)
                )
            ),
            Product(
                id = "prod-2",
                categoryId = "helados",
                name = "Chocolate Intenso 70%",
                description = "Cacao de origen colombiano",
                basePrice = 6500L,
                baseCost = 2000L,
                emoji = "🍫",
                badge = "Top",
                baseIngredientId = "ing-2",
                variants = listOf(
                    ProductVariant("var-1", "1 Bola (80g)", 0L, weightGrams = 80, coneUnits = 1),
                    ProductVariant("var-2", "2 Bolas (160g)", 5000L, weightGrams = 160, coneUnits = 1),
                    ProductVariant("var-3", "Vaso 2 Bolas", 5000L, weightGrams = 160, cupUnits = 1),
                    ProductVariant("var-4", "Litro (1000g)", 23000L, weightGrams = 1000, cupUnits = 1)
                )
            ),
            Product(
                id = "prod-3",
                categoryId = "helados",
                name = "Arequipe Manjar",
                description = "Dulce de leche con vetas doradas",
                basePrice = 6000L,
                baseCost = 1900L,
                emoji = "🍮",
                baseIngredientId = "ing-3",
                variants = listOf(
                    ProductVariant("var-1", "1 Bola (80g)", 0L, weightGrams = 80, coneUnits = 1),
                    ProductVariant("var-2", "2 Bolas (160g)", 5000L, weightGrams = 160, coneUnits = 1),
                    ProductVariant("var-3", "Vaso 2 Bolas", 5000L, weightGrams = 160, cupUnits = 1)
                )
            ),
            Product(
                id = "prod-4",
                categoryId = "helados",
                name = "Fresa Silvestre",
                description = "Fruta fresca de temporada",
                basePrice = 6000L,
                baseCost = 1850L,
                emoji = "🍓",
                baseIngredientId = "ing-4",
                variants = listOf(
                    ProductVariant("var-1", "1 Bola (80g)", 0L, weightGrams = 80, coneUnits = 1),
                    ProductVariant("var-2", "2 Bolas (160g)", 5000L, weightGrams = 160, coneUnits = 1),
                    ProductVariant("var-3", "Vaso 2 Bolas", 5000L, weightGrams = 160, cupUnits = 1)
                )
            ),
            Product(
                id = "prod-5",
                categoryId = "gourmet",
                name = "Pistacho Bronte Sicilia",
                description = "Pistacho puro tostado",
                basePrice = 8500L,
                baseCost = 3200L,
                emoji = "🌰",
                badge = "Gourmet",
                baseIngredientId = "ing-5",
                variants = listOf(
                    ProductVariant("var-1", "1 Bola (80g)", 0L, weightGrams = 80, coneUnits = 1),
                    ProductVariant("var-2", "2 Bolas (160g)", 6500L, weightGrams = 160, coneUnits = 1)
                )
            ),
            Product(
                id = "prod-6",
                categoryId = "paletas",
                name = "Paleta Rellena de Leche",
                description = "Fresa rellena de leche condensada",
                basePrice = 5500L,
                baseCost = 1600L,
                emoji = "🍭",
                baseIngredientId = "ing-4",
                allowedToppings = false,
                variants = listOf(
                    ProductVariant("var-1", "Paleta Unitaria", 0L, weightGrams = 100)
                )
            ),
            Product(
                id = "prod-7",
                categoryId = "copas",
                name = "Banana Split Clásica",
                description = "3 bolas, banano, arequipe y chantilly",
                basePrice = 18000L,
                baseCost = 5500L,
                emoji = "🍌",
                badge = "Clásica",
                baseIngredientId = "ing-1",
                variants = listOf(
                    ProductVariant("var-1", "Copa Completa", 0L, weightGrams = 240, cupUnits = 1)
                )
            ),
            Product(
                id = "prod-8",
                categoryId = "bebidas",
                name = "Malteada Espesa",
                description = "Batida al instante con helado y leche",
                basePrice = 13000L,
                baseCost = 3800L,
                emoji = "🥤",
                baseIngredientId = "ing-2",
                variants = listOf(
                    ProductVariant("var-1", "Vaso 16oz", 0L, weightGrams = 200, cupUnits = 1)
                )
            )
        )

        fun initialIngredients(): List<Ingredient> = listOf(
            Ingredient("ing-1", "Base Helado Vainilla Francesa", "Helados", "g", 4800.0, 1000.0, 22L, "LOT-2026-V01", "20/11/2026"),
            Ingredient("ing-2", "Base Helado Chocolate Intenso", "Helados", "g", 3500.0, 1000.0, 25L, "LOT-2026-CH02", "18/11/2026"),
            Ingredient("ing-3", "Base Helado Arequipe Manjar", "Helados", "g", 2200.0, 800.0, 24L, "LOT-2026-AR01", "25/11/2026"),
            Ingredient("ing-4", "Base Helado Fresa Silvestre", "Helados", "g", 1800.0, 800.0, 23L, "LOT-2026-FR03", "15/11/2026"),
            Ingredient("ing-5", "Base Helado Pistacho Bronte", "Helados", "g", 1200.0, 600.0, 38L, "LOT-2026-PIS01", "01/12/2026"),
            Ingredient("ing-6", "Barquillos Artesanales Dulces", "Insumos", "unidades", 85.0, 20.0, 400L, "LOT-BARQ-99", "30/12/2026"),
            Ingredient("ing-7", "Vasos Térmicos Biodegradables", "Insumos", "unidades", 140.0, 30.0, 250L, "LOT-VAS-102", "01/01/2027"),
            Ingredient("ing-8", "Chispas de Chocolate Semiamargo", "Toppings", "g", 1200.0, 300.0, 28L, "LOT-CHIS-44", "15/02/2027"),
            Ingredient("ing-9", "Salsa Arequipe Tradicional", "Toppings", "g", 950.0, 250.0, 20L, "LOT-AREQ-12", "10/12/2026"),
            Ingredient("ing-10", "Crema Chantilly Batida", "Toppings", "ml", 800.0, 200.0, 30L, "LOT-CHAN-08", "05/11/2026")
        )
    }
}
