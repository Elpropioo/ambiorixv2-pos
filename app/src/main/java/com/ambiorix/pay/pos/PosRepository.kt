package com.ambiorix.pay.pos

import android.content.Context
import android.content.SharedPreferences
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

interface PosStorage {
    fun getString(key: String, defaultValue: String?): String?
    fun putString(key: String, value: String)
}

class SharedPreferencesStorage(private val prefs: SharedPreferences) : PosStorage {
    override fun getString(key: String, defaultValue: String?): String? = prefs.getString(key, defaultValue)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class InMemoryPosStorage : PosStorage {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, defaultValue: String?): String? = map[key] ?: defaultValue
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}

data class ResolvedIngredientConsumption(
    val ingredientId: String,
    val ingredientName: String,
    val consumedQuantity: Double, // In ingredient's base unit
    val unit: String
)

data class ResolvedProductConsumption(
    val productId: String,
    val productName: String,
    val quantity: Int
)

class PosRepository(
    private val context: Context?,
    private val storage: PosStorage
) {

    private val settingsRepo: SettingsRepository? = context?.let { SettingsRepository.getInstance(it) }

    // Cashiers
    private val _cashiers = MutableStateFlow<List<CashierProfile>>(emptyList())
    val cashiers: StateFlow<List<CashierProfile>> = _cashiers.asStateFlow()

    private val _activeCashier = MutableStateFlow(
        CashierProfile(id = "cashier-1", name = "Laura Gómez", isActive = true)
    )
    val activeCashier: StateFlow<CashierProfile> = _activeCashier.asStateFlow()

    // Categories
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // Products & Combos
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    // Ingredients (Raw Materials Inventory)
    private val _ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    // Inventory Movements (Audit Log)
    private val _inventoryMovements = MutableStateFlow<List<InventoryMovement>>(emptyList())
    val inventoryMovements: StateFlow<List<InventoryMovement>> = _inventoryMovements.asStateFlow()

    // Sales History
    private val _sales = MutableStateFlow<List<SaleRecord>>(emptyList())
    val sales: StateFlow<List<SaleRecord>> = _sales.asStateFlow()

    // Expenses (Gastos)
    private val _expenses = MutableStateFlow<List<ExpenseRecord>>(emptyList())
    val expenses: StateFlow<List<ExpenseRecord>> = _expenses.asStateFlow()

    // Payment Transactions (Nequi, Cash, etc.)
    private val _paymentTransactions = MutableStateFlow<List<PaymentTransaction>>(emptyList())
    val paymentTransactions: StateFlow<List<PaymentTransaction>> = _paymentTransactions.asStateFlow()

    // Processed Sale IDs for Idempotency
    private val _processedSaleIds = mutableSetOf<String>()

    // Current Cash Shift
    private val _cashShift = MutableStateFlow(
        CashShift(
            id = "shift-${System.currentTimeMillis()}",
            cashierName = "Laura Gómez",
            openedAt = getCurrentTime(),
            initialCash = 150000L,
            status = "OPEN"
        )
    )
    val cashShift: StateFlow<CashShift> = _cashShift.asStateFlow()

    // Protocol State Machine (P2P SMS)
    private val _protocolState = MutableStateFlow(ProtocolState.DRAFT)
    val protocolState: StateFlow<ProtocolState> = _protocolState.asStateFlow()

    private val _activeOpId = MutableStateFlow<String?>(null)
    val activeOpId: StateFlow<String?> = _activeOpId.asStateFlow()

    private val _smsLogs = MutableStateFlow<List<ProtocolMessageLog>>(emptyList())
    val smsLogs: StateFlow<List<ProtocolMessageLog>> = _smsLogs.asStateFlow()

    private val _unassignedPayments = MutableStateFlow<List<SaleRecord>>(emptyList())
    val unassignedPayments: StateFlow<List<SaleRecord>> = _unassignedPayments.asStateFlow()

    init {
        loadData()
    }

    // =========================================================================
    // 1. CASHIER MANAGEMENT
    // =========================================================================

    fun updateCashierName(cashierId: String, newName: String): Result<CashierProfile> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre de la cajera no puede estar vacío"))
        }

        val currentList = _cashiers.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == cashierId }
        val updatedCashier = if (index >= 0) {
            val updated = currentList[index].copy(name = trimmed)
            currentList[index] = updated
            updated
        } else {
            val created = CashierProfile(id = cashierId, name = trimmed)
            currentList.add(created)
            created
        }

        _cashiers.value = currentList
        if (_activeCashier.value.id == cashierId) {
            _activeCashier.value = updatedCashier
            _cashShift.value = _cashShift.value.copy(cashierName = trimmed)
        }

        saveCashiers()
        return Result.success(updatedCashier)
    }

    fun createCashier(name: String, pin: String? = null): Result<CashierProfile> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre de la cajera no puede estar vacío"))
        }
        val newCashier = CashierProfile(
            id = "cashier-${System.currentTimeMillis()}",
            name = trimmed,
            pin = pin
        )
        _cashiers.value = _cashiers.value + newCashier
        saveCashiers()
        return Result.success(newCashier)
    }

    fun selectCashier(cashierId: String): Boolean {
        val cashier = _cashiers.value.find { it.id == cashierId } ?: return false
        _activeCashier.value = cashier
        _cashShift.value = _cashShift.value.copy(cashierName = cashier.name)
        storage.putString(KEY_ACTIVE_CASHIER_ID, cashierId)
        return true
    }

    // =========================================================================
    // 2. CATEGORY MANAGEMENT
    // =========================================================================

    fun createCategory(name: String, emoji: String = "🍦"): Result<Category> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre de la categoría no puede estar vacío"))
        }
        val id = "cat-${System.currentTimeMillis()}"
        val newCat = Category(
            id = id,
            name = trimmed,
            emoji = emoji,
            displayOrder = _categories.value.size
        )
        _categories.value = _categories.value + newCat
        saveCategories()
        return Result.success(newCat)
    }

    fun updateCategory(id: String, newName: String, newEmoji: String = "🍦", isActive: Boolean = true): Result<Category> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre de la categoría no puede estar vacío"))
        }
        val current = _categories.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) {
            return Result.failure(NoSuchElementException("Categoría no encontrada"))
        }
        val updated = current[index].copy(name = trimmed, emoji = newEmoji, isActive = isActive)
        current[index] = updated
        _categories.value = current
        saveCategories()
        return Result.success(updated)
    }

    fun deleteCategory(id: String, reassignToCategoryId: String): Result<Boolean> {
        if (id == reassignToCategoryId) {
            return Result.failure(IllegalArgumentException("La categoría de reasignación debe ser diferente"))
        }
        val targetCat = _categories.value.find { it.id == reassignToCategoryId }
            ?: return Result.failure(IllegalArgumentException("La categoría de reasignación no existe"))

        val catToDelete = _categories.value.find { it.id == id }
            ?: return Result.failure(NoSuchElementException("Categoría no encontrada"))

        if (_categories.value.size <= 1) {
            return Result.failure(IllegalStateException("No se puede eliminar la única categoría existente"))
        }

        // Reassign products safely without dropping them
        val updatedProducts = _products.value.map { prod ->
            if (prod.categoryId == id) {
                prod.copy(categoryId = targetCat.id, updatedAt = System.currentTimeMillis())
            } else prod
        }
        _products.value = updatedProducts

        _categories.value = _categories.value.filter { it.id != id }

        saveProducts()
        saveCategories()
        return Result.success(true)
    }

    fun getProductCountForCategory(categoryId: String): Int {
        return _products.value.count { it.categoryId == categoryId }
    }

    fun getDefaultCategory(): Category {
        return _categories.value.firstOrNull { it.isDefault }
            ?: _categories.value.firstOrNull()
            ?: Category("cat-default", "Helados", 0, true, "🍨", true)
    }

    // =========================================================================
    // 3. INGREDIENTS INVENTORY MANAGEMENT
    // =========================================================================

    fun createIngredient(
        name: String,
        unit: String,
        initialStock: Double,
        minStockAlert: Double = 100.0,
        unitCostCOP: Long = 0L,
        category: String = "Insumos"
    ): Result<Ingredient> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre del ingrediente es obligatorio"))
        }
        val id = "ing-${System.currentTimeMillis()}"
        val ing = Ingredient(
            id = id,
            name = trimmed,
            category = category,
            unit = unit,
            currentStock = initialStock,
            minStockAlert = minStockAlert,
            unitCostCOP = unitCostCOP,
            isActive = true
        )
        _ingredients.value = _ingredients.value + ing

        if (initialStock > 0.0) {
            logMovement(
                itemId = id,
                itemName = trimmed,
                itemType = "INGREDIENT",
                previousStock = 0.0,
                change = initialStock,
                resultingStock = initialStock,
                type = "PURCHASE",
                reason = "Inventario inicial de ingrediente",
                userName = _activeCashier.value.name
            )
        }

        saveIngredients()
        return Result.success(ing)
    }

    fun createIngredient(
        name: String,
        unit: String,
        initialStock: Double,
        unitCostCOP: Long
    ): Result<Ingredient> {
        return createIngredient(
            name = name,
            unit = unit,
            initialStock = initialStock,
            minStockAlert = 100.0,
            unitCostCOP = unitCostCOP
        )
    }

    fun updateIngredient(
        id: String,
        name: String,
        unit: String,
        minStockAlert: Double,
        unitCostCOP: Long,
        isActive: Boolean = true
    ): Result<Ingredient> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre no puede estar vacío"))
        }
        val current = _ingredients.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return Result.failure(NoSuchElementException("Ingrediente no encontrado"))

        val existing = current[index]
        val updated = existing.copy(
            name = trimmed,
            unit = unit,
            minStockAlert = minStockAlert,
            unitCostCOP = unitCostCOP,
            isActive = isActive
        )
        current[index] = updated
        _ingredients.value = current
        saveIngredients()
        return Result.success(updated)
    }

    fun adjustIngredientStock(
        ingredientId: String,
        newStock: Double,
        reason: String,
        userName: String,
        type: String = "MANUAL_ADJUSTMENT",
        allowNegative: Boolean = false
    ): Result<InventoryMovement> {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            return Result.failure(IllegalArgumentException("El motivo del ajuste es obligatorio"))
        }
        val trimmedUser = userName.trim()
        if (trimmedUser.isEmpty()) {
            return Result.failure(IllegalArgumentException("El usuario auditor es obligatorio"))
        }
        if (!allowNegative && newStock < 0.0) {
            return Result.failure(IllegalArgumentException("El inventario resultante no puede ser negativo"))
        }

        val current = _ingredients.value.toMutableList()
        val index = current.indexOfFirst { it.id == ingredientId }
        if (index < 0) return Result.failure(NoSuchElementException("Ingrediente no encontrado"))

        val ing = current[index]
        val prev = ing.currentStock
        val delta = newStock - prev

        current[index] = ing.copy(currentStock = newStock)
        _ingredients.value = current

        val mov = logMovement(
            itemId = ing.id,
            itemName = ing.name,
            itemType = "INGREDIENT",
            previousStock = prev,
            change = delta,
            resultingStock = newStock,
            type = type,
            reason = trimmedReason,
            userName = trimmedUser
        )

        saveIngredients()
        return Result.success(mov)
    }

    fun recordIngredientPurchase(
        ingredientId: String,
        quantity: Double,
        unit: String,
        supplierOrNote: String,
        userName: String
    ): Result<InventoryMovement> {
        if (quantity <= 0.0) return Result.failure(IllegalArgumentException("La cantidad debe ser mayor a cero"))
        val ing = _ingredients.value.find { it.id == ingredientId }
            ?: return Result.failure(NoSuchElementException("Ingrediente no encontrado"))

        val convertedQty = try {
            UnitConverter.convert(quantity, unit, ing.unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val newStock = ing.currentStock + convertedQty
        val note = if (supplierOrNote.isNotBlank()) supplierOrNote.trim() else "Compra recibida"
        return adjustIngredientStock(
            ingredientId = ingredientId,
            newStock = newStock,
            reason = "Entrada compra ($quantity $unit): $note",
            userName = userName,
            type = "PURCHASE"
        )
    }

    fun recordIngredientWaste(
        ingredientId: String,
        quantity: Double,
        unit: String,
        reason: String,
        userName: String
    ): Result<InventoryMovement> {
        if (quantity <= 0.0) return Result.failure(IllegalArgumentException("La cantidad de merma debe ser mayor a cero"))
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) return Result.failure(IllegalArgumentException("El motivo de merma es obligatorio"))

        val ing = _ingredients.value.find { it.id == ingredientId }
            ?: return Result.failure(NoSuchElementException("Ingrediente no encontrado"))

        val convertedQty = try {
            UnitConverter.convert(quantity, unit, ing.unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val newStock = maxOf(0.0, ing.currentStock - convertedQty)
        return adjustIngredientStock(
            ingredientId = ingredientId,
            newStock = newStock,
            reason = "Merma ($quantity $unit): $trimmedReason",
            userName = userName,
            type = "WASTE_MERMA"
        )
    }

    // =========================================================================
    // 4. PRODUCT, RECIPE & COMBO MANAGEMENT
    // =========================================================================

    fun createProduct(
        name: String,
        basePrice: Long,
        categoryId: String,
        description: String = "",
        imagePath: String? = null,
        emoji: String = "🍨",
        stockQuantity: Double = 0.0,
        unit: String = "unidades",
        baseCost: Long = 0L,
        minStockAlert: Double = 5.0,
        badge: String? = null,
        isCombo: Boolean = false,
        recipe: List<RecipeItem> = emptyList(),
        comboItems: List<ComboItem> = emptyList(),
        allowNegativeStock: Boolean = false
    ): Result<Product> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("El nombre del producto no puede estar vacío"))
        if (basePrice < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo"))

        val catExists = _categories.value.any { it.id == categoryId }
        val finalCategoryId = if (catExists) categoryId else getDefaultCategory().id

        val id = "prod-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        // Calculate estimated cost from recipe if recipe provided and baseCost is 0
        val computedCost = if (baseCost > 0L) baseCost else calculateRecipeCost(recipe)

        val product = Product(
            id = id,
            name = trimmed,
            description = description.trim(),
            basePrice = basePrice,
            baseCost = computedCost,
            categoryId = finalCategoryId,
            imagePath = imagePath,
            emoji = emoji,
            badge = badge,
            isActive = true,
            isCombo = isCombo,
            stockQuantity = stockQuantity,
            minStockAlert = minStockAlert,
            unit = unit,
            allowNegativeStock = allowNegativeStock,
            recipe = recipe,
            comboItems = comboItems,
            createdAt = now,
            updatedAt = now
        )

        _products.value = _products.value + product

        if (stockQuantity > 0.0 && !isCombo && recipe.isEmpty()) {
            logMovement(
                itemId = id,
                itemName = trimmed,
                itemType = "PRODUCT",
                previousStock = 0.0,
                change = stockQuantity,
                resultingStock = stockQuantity,
                type = "PURCHASE",
                reason = "Inventario inicial de producto",
                userName = _activeCashier.value.name
            )
        }

        saveProducts()
        return Result.success(product)
    }

    fun updateProduct(
        id: String,
        name: String,
        basePrice: Long,
        categoryId: String,
        description: String = "",
        imagePath: String? = null,
        emoji: String = "🍨",
        unit: String = "unidades",
        baseCost: Long = 0L,
        minStockAlert: Double = 5.0,
        isActive: Boolean = true,
        badge: String? = null,
        isCombo: Boolean = false,
        recipe: List<RecipeItem>? = null,
        comboItems: List<ComboItem>? = null,
        allowNegativeStock: Boolean = false
    ): Result<Product> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("El nombre del producto no puede estar vacío"))
        if (basePrice < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo"))

        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))

        val existing = current[index]
        val finalRecipe = recipe ?: existing.recipe
        val finalComboItems = comboItems ?: existing.comboItems

        // Prevent circular combo
        if (isCombo) {
            for (ci in finalComboItems) {
                if (hasCircularCombo(id, ci.productId)) {
                    return Result.failure(IllegalArgumentException("Referencia circular detectada: el combo no puede contenerse a sí mismo"))
                }
            }
        }

        val computedCost = if (baseCost > 0L) baseCost else calculateRecipeCost(finalRecipe)

        val updated = existing.copy(
            name = trimmed,
            basePrice = basePrice,
            categoryId = categoryId,
            description = description.trim(),
            imagePath = imagePath ?: existing.imagePath,
            emoji = emoji,
            unit = unit,
            baseCost = computedCost,
            minStockAlert = minStockAlert,
            isActive = isActive,
            badge = badge,
            isCombo = isCombo,
            recipe = finalRecipe,
            comboItems = finalComboItems,
            allowNegativeStock = allowNegativeStock,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun updateProductRecipe(productId: String, recipe: List<RecipeItem>): Result<Product> {
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))

        val p = current[index]
        val computedCost = calculateRecipeCost(recipe)
        val updated = p.copy(
            recipe = recipe,
            baseCost = if (computedCost > 0) computedCost else p.baseCost,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun duplicateProductRecipe(sourceProductId: String, targetProductId: String): Result<Product> {
        val source = _products.value.find { it.id == sourceProductId }
            ?: return Result.failure(NoSuchElementException("Producto de origen no encontrado"))
        return updateProductRecipe(targetProductId, source.recipe)
    }

    fun updateProductCombo(productId: String, comboItems: List<ComboItem>): Result<Product> {
        // Validate circular references
        for (item in comboItems) {
            if (hasCircularCombo(productId, item.productId)) {
                return Result.failure(IllegalArgumentException("Referencia circular detectada con producto '${item.productName}'"))
            }
        }

        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))

        val p = current[index]
        val updated = p.copy(
            isCombo = true,
            comboItems = comboItems,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun hasCircularCombo(parentProductId: String, candidateChildId: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (parentProductId == candidateChildId) return true
        if (!visited.add(candidateChildId)) return false

        val childProd = _products.value.find { it.id == candidateChildId } ?: return false
        if (childProd.isCombo) {
            for (grandChild in childProd.comboItems) {
                if (hasCircularCombo(parentProductId, grandChild.productId, visited)) return true
            }
        }
        return false
    }

    fun calculateRecipeCost(recipe: List<RecipeItem>): Long {
        var totalCost = 0.0
        for (item in recipe) {
            val ing = _ingredients.value.find { it.id == item.ingredientId }
            if (ing != null) {
                try {
                    val convertedQty = UnitConverter.convert(item.quantity, item.unit, ing.unit)
                    totalCost += convertedQty * ing.unitCostCOP
                } catch (e: Exception) {
                    totalCost += item.quantity * ing.unitCostCOP
                }
            }
        }
        return totalCost.toLong()
    }

    fun updateProductPrice(productId: String, newPrice: Long): Result<Product> {
        if (newPrice < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo"))
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))
        val updated = current[index].copy(basePrice = newPrice, updatedAt = System.currentTimeMillis())
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun updateProductImage(productId: String, newImagePath: String?): Result<Product> {
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))
        val updated = current[index].copy(imagePath = newImagePath, updatedAt = System.currentTimeMillis())
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun toggleProductActive(productId: String, isActive: Boolean): Result<Product> {
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))
        val updated = current[index].copy(isActive = isActive, updatedAt = System.currentTimeMillis())
        current[index] = updated
        _products.value = current
        saveProducts()
        return Result.success(updated)
    }

    fun deleteProduct(productId: String): Result<Boolean> {
        val current = _products.value
        val prod = current.find { it.id == productId }
            ?: return Result.failure(NoSuchElementException("Producto no encontrado"))

        // Check if product exists in historical sales
        val usedInSales = _sales.value.any { s -> s.items.any { it.productId == productId } }
        if (usedInSales) {
            // Soft delete to protect historical sales integrity
            return toggleProductActive(productId, isActive = false).map { true }
        }

        _products.value = current.filter { it.id != productId }
        saveProducts()
        return Result.success(true)
    }

    fun getActiveProducts(): List<Product> {
        return _products.value.filter { it.isActive }
    }

    fun searchProducts(query: String, categoryId: String? = null): List<Product> {
        val q = query.trim().lowercase(Locale.getDefault())
        return _products.value.filter { prod ->
            val matchesCat = categoryId == null || categoryId == "todos" || prod.categoryId == categoryId
            val matchesQuery = q.isEmpty() || prod.name.lowercase(Locale.getDefault()).contains(q) || prod.description.lowercase(Locale.getDefault()).contains(q)
            matchesCat && matchesQuery
        }
    }

    // =========================================================================
    // 5. INVENTORY MOVEMENTS AUDIT & MANUAL STOCK ADJUSTMENTS
    // =========================================================================

    fun adjustStock(
        productId: String,
        newStock: Double,
        reason: String,
        userName: String,
        type: String = "MANUAL_ADJUSTMENT",
        allowNegative: Boolean = false
    ): Result<InventoryMovement> {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) return Result.failure(IllegalArgumentException("El motivo del ajuste es obligatorio"))
        val trimmedUser = userName.trim()
        if (trimmedUser.isEmpty()) return Result.failure(IllegalArgumentException("El nombre del usuario que autoriza el ajuste es obligatorio"))
        if (!allowNegative && newStock < 0.0) return Result.failure(IllegalArgumentException("El inventario resultante no puede ser negativo"))

        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index < 0) return Result.failure(NoSuchElementException("Producto no encontrado"))

        val prod = current[index]
        val prev = prod.stockQuantity
        val delta = newStock - prev

        val updatedProd = prod.copy(stockQuantity = newStock, updatedAt = System.currentTimeMillis())
        current[index] = updatedProd
        _products.value = current

        val movement = logMovement(
            itemId = prod.id,
            itemName = prod.name,
            itemType = "PRODUCT",
            previousStock = prev,
            change = delta,
            resultingStock = newStock,
            type = type,
            reason = trimmedReason,
            userName = trimmedUser
        )

        saveProducts()
        return Result.success(movement)
    }

    fun recordPurchaseEntry(
        productId: String,
        quantity: Double,
        supplierOrNote: String,
        userName: String
    ): Result<InventoryMovement> {
        if (quantity <= 0.0) return Result.failure(IllegalArgumentException("La cantidad ingresada debe ser mayor a cero"))
        val prod = _products.value.find { it.id == productId }
            ?: return Result.failure(NoSuchElementException("Producto no encontrado"))

        val newStock = prod.stockQuantity + quantity
        val note = if (supplierOrNote.isNotBlank()) supplierOrNote.trim() else "Ingreso de compra"
        return adjustStock(
            productId = productId,
            newStock = newStock,
            reason = "Entrada de compra: $note",
            userName = userName,
            type = "PURCHASE"
        )
    }

    fun recordWaste(
        productId: String,
        quantity: Double,
        reason: String,
        userName: String
    ): Result<InventoryMovement> {
        if (quantity <= 0.0) return Result.failure(IllegalArgumentException("La cantidad de merma debe ser mayor a cero"))
        val prod = _products.value.find { it.id == productId }
            ?: return Result.failure(NoSuchElementException("Producto no encontrado"))

        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) return Result.failure(IllegalArgumentException("El motivo de la merma o baja es obligatorio"))

        val newStock = maxOf(0.0, prod.stockQuantity - quantity)
        return adjustStock(
            productId = productId,
            newStock = newStock,
            reason = "Baja / Merma: $trimmedReason",
            userName = userName,
            type = "WASTE_MERMA"
        )
    }

    private fun logMovement(
        itemId: String,
        itemName: String,
        itemType: String,
        previousStock: Double,
        change: Double,
        resultingStock: Double,
        type: String,
        reason: String,
        userName: String,
        saleId: String? = null
    ): InventoryMovement {
        val now = System.currentTimeMillis()
        val movement = InventoryMovement(
            id = "mov-$now-${(1..999).random()}",
            itemId = itemId,
            itemName = itemName,
            itemType = itemType,
            previousStock = previousStock,
            change = change,
            resultingStock = resultingStock,
            type = type,
            reason = reason,
            saleId = saleId,
            date = now,
            dateFormatted = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(now)),
            userName = userName
        )
        _inventoryMovements.value = listOf(movement) + _inventoryMovements.value
        saveMovements()
        return movement
    }

    // =========================================================================
    // 6. ATOMIC & IDEMPOTENT SALE INVENTORY DEDUCTION
    // =========================================================================

    /**
     * Resolves all ingredient and direct product requirements recursively for cart items.
     * Combos resolve child products; products resolve recipes; toppings resolve ingredients.
     */
    fun resolveSaleRequirements(items: List<CartItem>): Pair<List<ResolvedIngredientConsumption>, List<ResolvedProductConsumption>> {
        val ingredientMap = mutableMapOf<String, Double>() // ingredientId -> total quantity in ingredient's base unit
        val productMap = mutableMapOf<String, Int>()       // productId -> total units

        fun resolveProductDeduction(productId: String, multiplier: Int, visited: MutableSet<String>) {
            if (!visited.add(productId)) return // circular reference guard
            val prod = _products.value.find { it.id == productId } ?: return

            if (prod.isCombo && prod.comboItems.isNotEmpty()) {
                // Recursively resolve combo items
                for (ci in prod.comboItems) {
                    resolveProductDeduction(ci.productId, ci.quantity * multiplier, visited.toMutableSet())
                }
            } else if (prod.recipe.isNotEmpty()) {
                // Resolve product recipe ingredients
                for (rec in prod.recipe) {
                    val ing = _ingredients.value.find { it.id == rec.ingredientId }
                    if (ing != null) {
                        val baseQty = try {
                            UnitConverter.convert(rec.quantity, rec.unit, ing.unit)
                        } catch (e: Exception) {
                            rec.quantity
                        }
                        val totalRequired = baseQty * multiplier
                        ingredientMap[ing.id] = (ingredientMap[ing.id] ?: 0.0) + totalRequired
                    }
                }
            } else {
                // Standalone direct product
                productMap[prod.id] = (productMap[prod.id] ?: 0) + multiplier
            }
        }

        for (item in items) {
            resolveProductDeduction(item.productId, item.quantity, mutableSetOf())

            // Process item toppings
            for (t in item.toppings) {
                if (t.ingredientId != null && t.gramsRequired > 0) {
                    val ing = _ingredients.value.find { it.id == t.ingredientId }
                    if (ing != null) {
                        val converted = try {
                            UnitConverter.convert(t.gramsRequired.toDouble(), "g", ing.unit)
                        } catch (e: Exception) {
                            t.gramsRequired.toDouble()
                        }
                        val total = converted * item.quantity
                        ingredientMap[ing.id] = (ingredientMap[ing.id] ?: 0.0) + total
                    }
                }
            }
        }

        val resolvedIngs = ingredientMap.map { (id, qty) ->
            val ing = _ingredients.value.first { it.id == id }
            ResolvedIngredientConsumption(id, ing.name, qty, ing.unit)
        }

        val resolvedProds = productMap.map { (id, qty) ->
            val prod = _products.value.first { it.id == id }
            ResolvedProductConsumption(id, prod.name, qty)
        }

        return Pair(resolvedIngs, resolvedProds)
    }

    /**
     * Completes a sale with idempotent, atomic inventory deduction.
     */
    @Synchronized
    fun completeSale(
        items: List<CartItem>,
        subtotal: Long,
        discount: Long,
        total: Long,
        paymentMethod: String,
        cashierName: String,
        senderName: String? = null,
        channel: String? = null,
        saleId: String? = null
    ): Result<SaleRecord> {
        val finalSaleId = saleId ?: "sale-${System.currentTimeMillis()}"

        // Idempotency check: prevent duplicate deduction
        if (_processedSaleIds.contains(finalSaleId)) {
            val existing = _sales.value.find { it.id == finalSaleId }
            if (existing != null) return Result.success(existing)
        }

        val (requiredIngredients, requiredProducts) = resolveSaleRequirements(items)

        // 1. Validate ingredient stock
        val currentIngs = _ingredients.value.associateBy { it.id }.toMutableMap()
        for (req in requiredIngredients) {
            val ing = currentIngs[req.ingredientId]
                ?: return Result.failure(IllegalStateException("Ingrediente '${req.ingredientName}' no existe en inventario"))
            if (ing.currentStock < req.consumedQuantity) {
                return Result.failure(
                    IllegalStateException("Stock insuficiente de '${ing.name}': disponible ${ing.currentStock} ${ing.unit}, requerido ${req.consumedQuantity} ${ing.unit}")
                )
            }
        }

        // 2. Validate standalone product stock (unless allowNegativeStock is true)
        val currentProds = _products.value.associateBy { it.id }.toMutableMap()
        for (req in requiredProducts) {
            val prod = currentProds[req.productId]
                ?: return Result.failure(IllegalStateException("Producto '${req.productName}' no encontrado"))
            if (!prod.allowNegativeStock && prod.stockQuantity < req.quantity) {
                return Result.failure(
                    IllegalStateException("Stock insuficiente del producto '${prod.name}': disponible ${prod.stockQuantity.toInt()}, requerido ${req.quantity}")
                )
            }
        }

        // 3. Atomically apply deductions
        val saleNo = "V-${(_sales.value.size + 101).toString().padStart(5, '0')}"
        val cogs = items.sumOf { it.estimatedCost }

        // Deduct ingredients
        for (req in requiredIngredients) {
            val ing = currentIngs[req.ingredientId]!!
            val newStock = maxOf(0.0, ing.currentStock - req.consumedQuantity)
            currentIngs[req.ingredientId] = ing.copy(currentStock = newStock)
            logMovement(
                itemId = ing.id,
                itemName = ing.name,
                itemType = "INGREDIENT",
                previousStock = ing.currentStock,
                change = -req.consumedQuantity,
                resultingStock = newStock,
                type = "SALE_RECIPE",
                reason = "Receta venta $saleNo",
                userName = cashierName,
                saleId = finalSaleId
            )
        }
        _ingredients.value = currentIngs.values.toList()
        saveIngredients()

        // Deduct standalone products
        for (req in requiredProducts) {
            val prod = currentProds[req.productId]!!
            val newStock = prod.stockQuantity - req.quantity
            currentProds[req.productId] = prod.copy(stockQuantity = newStock, updatedAt = System.currentTimeMillis())
            logMovement(
                itemId = prod.id,
                itemName = prod.name,
                itemType = "PRODUCT",
                previousStock = prod.stockQuantity,
                change = -(req.quantity.toDouble()),
                resultingStock = newStock,
                type = "SALE",
                reason = "Venta $saleNo",
                userName = cashierName,
                saleId = finalSaleId
            )
        }
        _products.value = currentProds.values.toList()
        saveProducts()

        // 4. Create Sale Record
        val sale = SaleRecord(
            id = finalSaleId,
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
            isInventoryDeducted = true,
            cogsCost = cogs
        )

        _sales.value = listOf(sale) + _sales.value
        _processedSaleIds.add(finalSaleId)
        saveSales()

        // Update cash shift and record payment transaction(s)
        if (paymentMethod == "CASH") {
            _cashShift.value = _cashShift.value.copy(
                cashIn = _cashShift.value.cashIn + total
            )
            recordPaymentTransaction(
                method = "CASH",
                amount = total,
                saleId = finalSaleId,
                saleNumber = saleNo,
                cashierName = cashierName,
                entryType = "SALE_PAYMENT",
                dateStr = getTodayDateKey(),
                time = getCurrentTime()
            )
        } else if (paymentMethod == "NEQUI") {
            recordPaymentTransaction(
                method = "NEQUI",
                amount = total,
                saleId = finalSaleId,
                saleNumber = saleNo,
                cashierName = cashierName,
                payerNameOrPhone = senderName,
                bankTransactionRef = null,
                smsEventId = "SMS-${System.currentTimeMillis()}",
                originInstallation = "Tienda Local",
                nequiChannel = channel,
                dateStr = getTodayDateKey(),
                time = getCurrentTime()
            )
        } else if (paymentMethod == "SPLIT") {
            if (sale.cashAmount > 0L) {
                _cashShift.value = _cashShift.value.copy(
                    cashIn = _cashShift.value.cashIn + sale.cashAmount
                )
                recordPaymentTransaction(
                    method = "CASH",
                    amount = sale.cashAmount,
                    saleId = finalSaleId,
                    saleNumber = saleNo,
                    cashierName = cashierName,
                    entryType = "SALE_PAYMENT",
                    dateStr = getTodayDateKey(),
                    time = getCurrentTime()
                )
            }
            if (sale.nequiAmount > 0L) {
                recordPaymentTransaction(
                    method = "NEQUI",
                    amount = sale.nequiAmount,
                    saleId = finalSaleId,
                    saleNumber = saleNo,
                    cashierName = cashierName,
                    payerNameOrPhone = senderName,
                    bankTransactionRef = null,
                    smsEventId = "SMS-${System.currentTimeMillis()}",
                    originInstallation = "Tienda Local",
                    nequiChannel = channel,
                    dateStr = getTodayDateKey(),
                    time = getCurrentTime()
                )
            }
        }

        return Result.success(sale)
    }

    /**
     * Annulls / returns a confirmed sale and registers compensatory inventory movements.
     */
    @Synchronized
    fun returnSale(saleId: String, reason: String, userName: String): Result<SaleRecord> {
        val currentSales = _sales.value.toMutableList()
        val index = currentSales.indexOfFirst { it.id == saleId }
        if (index < 0) return Result.failure(NoSuchElementException("Venta no encontrada"))

        val sale = currentSales[index]
        if (sale.status == "RETURNED") return Result.failure(IllegalStateException("La venta ya ha sido devuelta"))

        val trimmedReason = reason.trim().ifEmpty { "Devolución / Anulación de venta" }

        // Compensate inventory
        val (requiredIngredients, requiredProducts) = resolveSaleRequirements(sale.items)

        // Restore ingredients
        val currentIngs = _ingredients.value.associateBy { it.id }.toMutableMap()
        for (req in requiredIngredients) {
            val ing = currentIngs[req.ingredientId]
            if (ing != null) {
                val newStock = ing.currentStock + req.consumedQuantity
                currentIngs[req.ingredientId] = ing.copy(currentStock = newStock)
                logMovement(
                    itemId = ing.id,
                    itemName = ing.name,
                    itemType = "INGREDIENT",
                    previousStock = ing.currentStock,
                    change = req.consumedQuantity,
                    resultingStock = newStock,
                    type = "RETURN_COMPENSATION",
                    reason = "Compensación por devolución ${sale.saleNumber}: $trimmedReason",
                    userName = userName,
                    saleId = saleId
                )
            }
        }
        _ingredients.value = currentIngs.values.toList()
        saveIngredients()

        // Restore standalone products
        val currentProds = _products.value.associateBy { it.id }.toMutableMap()
        for (req in requiredProducts) {
            val prod = currentProds[req.productId]
            if (prod != null) {
                val newStock = prod.stockQuantity + req.quantity
                currentProds[req.productId] = prod.copy(stockQuantity = newStock, updatedAt = System.currentTimeMillis())
                logMovement(
                    itemId = prod.id,
                    itemName = prod.name,
                    itemType = "PRODUCT",
                    previousStock = prod.stockQuantity,
                    change = req.quantity.toDouble(),
                    resultingStock = newStock,
                    type = "RETURN_COMPENSATION",
                    reason = "Compensación por devolución ${sale.saleNumber}: $trimmedReason",
                    userName = userName,
                    saleId = saleId
                )
            }
        }
        _products.value = currentProds.values.toList()
        saveProducts()

        val updatedSale = sale.copy(status = "RETURNED")
        currentSales[index] = updatedSale
        _sales.value = currentSales

        return Result.success(updatedSale)
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

    // =========================================================================
    // 7. P2P SMS PROTOCOL DISPATCHER
    // =========================================================================

    fun sendProtocolCommand(cmd: String, amount: Long): Boolean {
        val settings = settingsRepo?.getSettings() ?: return false
        val targetPhone = settings.peerStorePhone.trim()

        if (!settings.peerStoreEnabled || targetPhone.isEmpty()) return false

        val opId = _activeOpId.value ?: "op-${System.currentTimeMillis().toString().takeLast(6)}"
        _activeOpId.value = opId

        val seq = _smsLogs.value.size + 1
        val ts = System.currentTimeMillis()
        val prePayload = "APOS|v1|msg-$ts|$opId|$cmd|$seq|${settings.localStoreId}|${settings.peerStoreId}|$ts|120|$amount"
        val hmac = generateHmacSha256(prePayload, settings.peerSharedSecret)
        val fullPayload = "$prePayload|$hmac"

        var sent = false
        try {
            if (context != null) {
                val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                    ?: SmsManager.getDefault()
                smsManager.sendTextMessage(targetPhone, null, fullPayload, null, null)
                sent = true
            }
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

    fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    fun getCurrentDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    fun getNormalizedDateKey(dateInput: String): String {
        val trimmed = dateInput.trim()
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return trimmed
        if (trimmed.matches(Regex("""^\d{1,2}/\d{1,2}/\d{4}$"""))) {
            val parts = trimmed.split("/")
            val day = parts[0].padStart(2, '0')
            val month = parts[1].padStart(2, '0')
            val year = parts[2]
            return "$year-$month-$day"
        }
        return trimmed
    }

    fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // =========================================================================
    // FINANCIAL HISTORY, EXPENSES & CALENDAR
    // =========================================================================

    @Synchronized
    fun recordPaymentTransaction(
        method: String,
        amount: Long,
        saleId: String? = null,
        saleNumber: String? = null,
        cashierName: String = _activeCashier.value.name,
        payerNameOrPhone: String? = null,
        bankTransactionRef: String? = null,
        smsEventId: String? = null,
        originInstallation: String = "Tienda Local",
        nequiChannel: String? = null,
        entryType: String = "SALE_PAYMENT",
        notes: String = "",
        dateStr: String? = null,
        time: String? = null,
        status: String = "CONFIRMED"
    ): PaymentTransaction {
        val finalDate = dateStr?.let { getNormalizedDateKey(it) } ?: getTodayDateKey()
        val finalTime = time ?: getCurrentTime()
        val tx = PaymentTransaction(
            id = "tx-${System.currentTimeMillis()}-${(100..999).random()}",
            method = method,
            amount = amount,
            dateStr = finalDate,
            time = finalTime,
            timestamp = System.currentTimeMillis(),
            status = status,
            saleId = saleId,
            saleNumber = saleNumber,
            cashierName = cashierName,
            payerNameOrPhone = payerNameOrPhone,
            bankTransactionRef = bankTransactionRef,
            smsEventId = smsEventId ?: if (method == "NEQUI") "SMS-${System.currentTimeMillis()}" else null,
            originInstallation = originInstallation,
            nequiChannel = nequiChannel,
            entryType = entryType,
            notes = notes
        )
        _paymentTransactions.value = listOf(tx) + _paymentTransactions.value
        savePaymentTransactions()
        return tx
    }

    @Synchronized
    fun recordExpense(
        amount: Long,
        category: String,
        description: String,
        paymentSource: String = "Caja efectivo",
        cashierName: String = _activeCashier.value.name,
        notes: String = "",
        dateStr: String? = null,
        isLinkedToInventory: Boolean = false
    ): Result<ExpenseRecord> {
        if (amount <= 0L) {
            return Result.failure(IllegalArgumentException("El monto del gasto debe ser mayor a 0"))
        }
        if (description.isBlank()) {
            return Result.failure(IllegalArgumentException("La descripción del gasto es obligatoria"))
        }
        val finalDate = dateStr?.let { getNormalizedDateKey(it) } ?: getTodayDateKey()
        val expense = ExpenseRecord(
            id = "exp-${System.currentTimeMillis()}-${(100..999).random()}",
            amount = amount,
            category = category.trim(),
            description = description.trim(),
            paymentSource = paymentSource,
            cashierName = cashierName,
            notes = notes.trim(),
            dateStr = finalDate,
            time = getCurrentTime(),
            timestamp = System.currentTimeMillis(),
            isLinkedToInventory = isLinkedToInventory,
            status = "ACTIVE"
        )
        _expenses.value = listOf(expense) + _expenses.value
        saveExpenses()
        return Result.success(expense)
    }

    @Synchronized
    fun annulExpense(expenseId: String, reason: String, userName: String): Result<ExpenseRecord> {
        val current = _expenses.value.toMutableList()
        val idx = current.indexOfFirst { it.id == expenseId }
        if (idx < 0) return Result.failure(NoSuchElementException("Gasto no encontrado"))
        val existing = current[idx]
        val updated = existing.copy(
            status = "CANCELLED",
            notes = if (existing.notes.isBlank()) "Anulado por $userName: $reason" else "${existing.notes} | Anulado por $userName: $reason"
        )
        current[idx] = updated
        _expenses.value = current
        saveExpenses()
        return Result.success(updated)
    }

    fun getDailyFinancialSummary(dateInput: String): DailyFinancialSummary {
        val normalizedDate = getNormalizedDateKey(dateInput)

        // 1. Nequi transactions for this day
        val dayNequiTxns = _paymentTransactions.value.filter {
            it.method == "NEQUI" && getNormalizedDateKey(it.dateStr) == normalizedDate
        }
        val totalNequi = dayNequiTxns.filter { it.status == "CONFIRMED" }.sumOf { it.amount }

        // 2. Cash transactions for this day
        val dayCashTxns = _paymentTransactions.value.filter {
            it.method == "CASH" && getNormalizedDateKey(it.dateStr) == normalizedDate
        }
        val totalCash = dayCashTxns.filter { it.status == "CONFIRMED" }.sumOf { it.amount }

        // Formula: Total recibido = Total Nequi + Total efectivo
        val totalReceived = totalNequi + totalCash

        // 3. Expenses for this day
        val dayExpenses = _expenses.value.filter {
            getNormalizedDateKey(it.dateStr) == normalizedDate
        }
        val totalExpenses = dayExpenses.filter { it.status == "ACTIVE" }.sumOf { it.amount }

        // Formula: Balance de movimientos = Total recibido - Total gastos
        val movementBalance = totalReceived - totalExpenses

        // 4. Products sold on this date (Lo que se vendió ese día)
        // Grouped strictly from completed sales on that date.
        // Rule: Combos are recorded as the combo itself without inflating individual ingredients or components.
        val daySales = _sales.value.filter {
            it.status == "COMPLETED" && getNormalizedDateKey(it.dateStr) == normalizedDate
        }

        val productOccurrences = mutableMapOf<String, MutableList<Pair<SaleRecord, CartItem>>>()
        for (sale in daySales) {
            for (item in sale.items) {
                productOccurrences.getOrPut(item.productId) { mutableListOf() }.add(Pair(sale, item))
            }
        }

        val allProductsMap = _products.value.associateBy { it.id }

        val productsSoldList = productOccurrences.map { (prodId, pairs) ->
            val firstItem = pairs.first().second
            val isCombo = allProductsMap[prodId]?.isCombo ?: (firstItem.cartItemId.startsWith("combo"))
            val totalQty = pairs.sumOf { it.second.quantity }
            val totalRev = pairs.sumOf { it.second.totalPrice }
            val occurrences = pairs.map { (sale, item) ->
                ProductSaleOccurrence(
                    saleId = sale.id,
                    saleNumber = sale.saleNumber,
                    time = sale.timestamp,
                    cashierName = sale.cashierName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    totalPrice = item.totalPrice
                )
            }
            ProductSaleSummary(
                productId = prodId,
                productName = firstItem.productName,
                totalQuantity = totalQty,
                unitSalePrice = firstItem.unitPrice, // Historical snapshot price
                totalRevenue = totalRev,
                discountsTotal = 0L,
                salesCount = pairs.map { it.first.id }.distinct().size,
                isCombo = isCombo,
                salesDetails = occurrences
            )
        }.sortedByDescending { it.totalRevenue }

        val totalUnits = productsSoldList.sumOf { it.totalQuantity }

        return DailyFinancialSummary(
            dateStr = normalizedDate,
            totalNequi = totalNequi,
            nequiCount = dayNequiTxns.size,
            nequiTransactions = dayNequiTxns,
            totalCash = totalCash,
            cashCount = dayCashTxns.size,
            cashTransactions = dayCashTxns,
            totalReceived = totalReceived,
            totalExpenses = totalExpenses,
            expensesCount = dayExpenses.size,
            expenses = dayExpenses,
            movementBalance = movementBalance,
            productsSold = productsSoldList,
            totalUnitsSold = totalUnits,
            salesCount = daySales.size
        )
    }

    fun getMonthCalendarDays(year: Int, month: Int): List<MonthDayIndicator> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val maxDays = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

        val indicators = mutableListOf<MonthDayIndicator>()
        for (day in 1..maxDays) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            val summary = getDailyFinancialSummary(dateStr)
            indicators.add(
                MonthDayIndicator(
                    dateStr = dateStr,
                    dayNumber = day,
                    hasSalesOrPayments = summary.totalReceived > 0L || summary.salesCount > 0,
                    hasExpenses = summary.totalExpenses > 0L,
                    totalReceived = summary.totalReceived,
                    totalExpenses = summary.totalExpenses
                )
            )
        }
        return indicators
    }

    // =========================================================================
    // 8. PERSISTENCE HELPERS
    // =========================================================================

    private fun loadData() {
        // Load Cashiers
        val cashiersJson = storage.getString(KEY_CASHIERS, null)
        val loadedCashiers = if (!cashiersJson.isNullOrBlank()) {
            decodeCashiers(cashiersJson)
        } else {
            defaultCashiers()
        }
        _cashiers.value = loadedCashiers

        val activeId = storage.getString(KEY_ACTIVE_CASHIER_ID, null)
        val foundActive = loadedCashiers.find { it.id == activeId } ?: loadedCashiers.firstOrNull()
        if (foundActive != null) {
            _activeCashier.value = foundActive
            _cashShift.value = _cashShift.value.copy(cashierName = foundActive.name)
        }

        // Load Categories
        val catsJson = storage.getString(KEY_CATEGORIES, null)
        _categories.value = if (!catsJson.isNullOrBlank()) {
            decodeCategories(catsJson)
        } else {
            defaultCategories()
        }

        // Load Ingredients
        val ingsJson = storage.getString(KEY_INGREDIENTS, null)
        _ingredients.value = if (!ingsJson.isNullOrBlank()) {
            decodeIngredients(ingsJson)
        } else {
            defaultIngredients()
        }

        // Load Products & Combos
        val prodsJson = storage.getString(KEY_PRODUCTS, null)
        _products.value = if (!prodsJson.isNullOrBlank()) {
            decodeProducts(prodsJson)
        } else {
            defaultProducts()
        }

        // Load Movements
        val movsJson = storage.getString(KEY_MOVEMENTS, null)
        if (!movsJson.isNullOrBlank()) {
            _inventoryMovements.value = decodeMovements(movsJson)
        }

        // Load Sales
        val salesJson = storage.getString(KEY_SALES, null)
        _sales.value = if (!salesJson.isNullOrBlank()) {
            decodeSales(salesJson)
        } else {
            defaultSales()
        }

        // Load Expenses
        val expensesJson = storage.getString(KEY_EXPENSES, null)
        _expenses.value = if (!expensesJson.isNullOrBlank()) {
            decodeExpenses(expensesJson)
        } else {
            defaultExpenses()
        }

        // Load Payment Transactions
        val paymentsJson = storage.getString(KEY_PAYMENT_TRANSACTIONS, null)
        _paymentTransactions.value = if (!paymentsJson.isNullOrBlank()) {
            decodePaymentTransactions(paymentsJson)
        } else {
            defaultPaymentTransactions()
        }
    }

    private fun saveCashiers() {
        storage.putString(KEY_CASHIERS, encodeCashiers(_cashiers.value))
        storage.putString(KEY_ACTIVE_CASHIER_ID, _activeCashier.value.id)
    }

    private fun saveCategories() {
        storage.putString(KEY_CATEGORIES, encodeCategories(_categories.value))
    }

    private fun saveIngredients() {
        storage.putString(KEY_INGREDIENTS, encodeIngredients(_ingredients.value))
    }

    private fun saveProducts() {
        storage.putString(KEY_PRODUCTS, encodeProducts(_products.value))
    }

    private fun saveMovements() {
        storage.putString(KEY_MOVEMENTS, encodeMovements(_inventoryMovements.value.take(300)))
    }

    fun saveSales() {
        storage.putString(KEY_SALES, encodeSales(_sales.value.take(200)))
    }

    fun saveExpenses() {
        storage.putString(KEY_EXPENSES, encodeExpenses(_expenses.value.take(300)))
    }

    fun savePaymentTransactions() {
        storage.putString(KEY_PAYMENT_TRANSACTIONS, encodePaymentTransactions(_paymentTransactions.value.take(500)))
    }

    private fun encodeExpenses(list: List<ExpenseRecord>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, e ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(e.id)}\",\"amount\":${e.amount},\"category\":\"${escape(e.category)}\",\"description\":\"${escape(e.description)}\",\"paymentSource\":\"${escape(e.paymentSource)}\",\"cashierName\":\"${escape(e.cashierName)}\",\"notes\":\"${escape(e.notes)}\",\"dateStr\":\"${escape(e.dateStr)}\",\"time\":\"${escape(e.time)}\",\"timestamp\":${e.timestamp},\"isLinkedToInventory\":${e.isLinkedToInventory},\"status\":\"${escape(e.status)}\"}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeExpenses(json: String): List<ExpenseRecord> {
        val result = mutableListOf<ExpenseRecord>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val amount = map["amount"]?.toLongOrNull() ?: 0L
            val cat = map["category"] ?: "Otros"
            val desc = map["description"] ?: ""
            val src = map["paymentSource"] ?: "Caja efectivo"
            val cashier = map["cashierName"] ?: "Laura Gómez"
            val notes = map["notes"] ?: ""
            val dateStr = map["dateStr"] ?: getTodayDateKey()
            val time = map["time"] ?: "12:00 PM"
            val ts = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
            val linked = map["isLinkedToInventory"]?.toBoolean() ?: false
            val status = map["status"] ?: "ACTIVE"
            result.add(
                ExpenseRecord(
                    id = id,
                    amount = amount,
                    category = cat,
                    description = desc,
                    paymentSource = src,
                    cashierName = cashier,
                    notes = notes,
                    dateStr = dateStr,
                    time = time,
                    timestamp = ts,
                    isLinkedToInventory = linked,
                    status = status
                )
            )
        }
        return result
    }

    private fun encodePaymentTransactions(list: List<PaymentTransaction>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, p ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(p.id)}\",\"method\":\"${escape(p.method)}\",\"amount\":${p.amount},\"dateStr\":\"${escape(p.dateStr)}\",\"time\":\"${escape(p.time)}\",\"timestamp\":${p.timestamp},\"status\":\"${escape(p.status)}\",\"saleId\":${p.saleId?.let { "\"${escape(it)}\"" } ?: "null"},\"saleNumber\":${p.saleNumber?.let { "\"${escape(it)}\"" } ?: "null"},\"cashierName\":\"${escape(p.cashierName)}\",\"payerNameOrPhone\":${p.payerNameOrPhone?.let { "\"${escape(it)}\"" } ?: "null"},\"bankTransactionRef\":${p.bankTransactionRef?.let { "\"${escape(it)}\"" } ?: "null"},\"smsEventId\":${p.smsEventId?.let { "\"${escape(it)}\"" } ?: "null"},\"originInstallation\":\"${escape(p.originInstallation)}\",\"nequiChannel\":${p.nequiChannel?.let { "\"${escape(it)}\"" } ?: "null"},\"entryType\":\"${escape(p.entryType)}\",\"notes\":\"${escape(p.notes)}\"}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodePaymentTransactions(json: String): List<PaymentTransaction> {
        val result = mutableListOf<PaymentTransaction>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val method = map["method"] ?: "CASH"
            val amount = map["amount"]?.toLongOrNull() ?: 0L
            val dateStr = map["dateStr"] ?: getTodayDateKey()
            val time = map["time"] ?: "12:00 PM"
            val ts = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
            val status = map["status"] ?: "CONFIRMED"
            val saleId = map["saleId"]?.takeIf { it != "null" }
            val saleNo = map["saleNumber"]?.takeIf { it != "null" }
            val cashier = map["cashierName"] ?: "Laura Gómez"
            val payer = map["payerNameOrPhone"]?.takeIf { it != "null" }
            val bankRef = map["bankTransactionRef"]?.takeIf { it != "null" && it.isNotBlank() }
            val smsId = map["smsEventId"]?.takeIf { it != "null" }
            val origin = map["originInstallation"] ?: "Tienda Local"
            val channel = map["nequiChannel"]?.takeIf { it != "null" }
            val entryType = map["entryType"] ?: "SALE_PAYMENT"
            val notes = map["notes"] ?: ""
            result.add(
                PaymentTransaction(
                    id = id,
                    method = method,
                    amount = amount,
                    dateStr = dateStr,
                    time = time,
                    timestamp = ts,
                    status = status,
                    saleId = saleId,
                    saleNumber = saleNo,
                    cashierName = cashier,
                    payerNameOrPhone = payer,
                    bankTransactionRef = bankRef,
                    smsEventId = smsId,
                    originInstallation = origin,
                    nequiChannel = channel,
                    entryType = entryType,
                    notes = notes
                )
            )
        }
        return result
    }

    private fun encodeSales(list: List<SaleRecord>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, s ->
            if (i > 0) sb.append(",")
            val itemsJson = StringBuilder("[")
            s.items.forEachIndexed { idx, itm ->
                if (idx > 0) itemsJson.append(",")
                itemsJson.append("{\"cartItemId\":\"${escape(itm.cartItemId)}\",\"productId\":\"${escape(itm.productId)}\",\"productName\":\"${escape(itm.productName)}\",\"basePrice\":${itm.basePrice},\"unitPrice\":${itm.unitPrice},\"quantity\":${itm.quantity},\"totalPrice\":${itm.totalPrice},\"estimatedCost\":${itm.estimatedCost}}")
            }
            itemsJson.append("]")
            sb.append("{\"id\":\"${escape(s.id)}\",\"saleNumber\":\"${escape(s.saleNumber)}\",\"items\":$itemsJson,\"subtotal\":${s.subtotal},\"discount\":${s.discount},\"total\":${s.total},\"paymentMethod\":\"${escape(s.paymentMethod)}\",\"cashAmount\":${s.cashAmount},\"nequiAmount\":${s.nequiAmount},\"nequiSenderName\":${s.nequiSenderName?.let { "\"${escape(it)}\"" } ?: "null"},\"nequiChannel\":${s.nequiChannel?.let { "\"${escape(it)}\"" } ?: "null"},\"timestamp\":\"${escape(s.timestamp)}\",\"dateStr\":\"${escape(s.dateStr)}\",\"cashierName\":\"${escape(s.cashierName)}\",\"status\":\"${escape(s.status)}\",\"isInventoryDeducted\":${s.isInventoryDeducted},\"cogsCost\":${s.cogsCost}}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeSales(json: String): List<SaleRecord> {
        val result = mutableListOf<SaleRecord>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val saleNo = map["saleNumber"] ?: "V-00000"
            val subtotal = map["subtotal"]?.toLongOrNull() ?: 0L
            val discount = map["discount"]?.toLongOrNull() ?: 0L
            val total = map["total"]?.toLongOrNull() ?: 0L
            val method = map["paymentMethod"] ?: "CASH"
            val cashAmt = map["cashAmount"]?.toLongOrNull() ?: 0L
            val nequiAmt = map["nequiAmount"]?.toLongOrNull() ?: 0L
            val sender = map["nequiSenderName"]?.takeIf { it != "null" }
            val ch = map["nequiChannel"]?.takeIf { it != "null" }
            val time = map["timestamp"] ?: "12:00 PM"
            val dateStr = map["dateStr"] ?: getTodayDateKey()
            val cashier = map["cashierName"] ?: "Laura Gómez"
            val status = map["status"] ?: "COMPLETED"
            val deducted = map["isInventoryDeducted"]?.toBoolean() ?: true
            val cogs = map["cogsCost"]?.toLongOrNull() ?: 0L

            val itemsList = mutableListOf<CartItem>()
            val itemsRaw = map["items"]
            if (itemsRaw != null && itemsRaw.startsWith("[")) {
                for (itmRaw in splitJsonArray(itemsRaw)) {
                    val itmMap = parseSimpleJsonObject(itmRaw)
                    val pId = itmMap["productId"] ?: continue
                    val pName = itmMap["productName"] ?: "Producto"
                    val bp = itmMap["basePrice"]?.toLongOrNull() ?: 0L
                    val up = itmMap["unitPrice"]?.toLongOrNull() ?: bp
                    val qty = itmMap["quantity"]?.toIntOrNull() ?: 1
                    val tp = itmMap["totalPrice"]?.toLongOrNull() ?: (up * qty)
                    val cost = itmMap["estimatedCost"]?.toLongOrNull() ?: 0L
                    itemsList.add(
                        CartItem(
                            cartItemId = itmMap["cartItemId"] ?: "item-$pId",
                            productId = pId,
                            productName = pName,
                            basePrice = bp,
                            unitPrice = up,
                            quantity = qty,
                            totalPrice = tp,
                            estimatedCost = cost
                        )
                    )
                }
            }

            result.add(
                SaleRecord(
                    id = id,
                    saleNumber = saleNo,
                    items = itemsList,
                    subtotal = subtotal,
                    discount = discount,
                    total = total,
                    paymentMethod = method,
                    cashAmount = cashAmt,
                    nequiAmount = nequiAmt,
                    nequiSenderName = sender,
                    nequiChannel = ch,
                    timestamp = time,
                    dateStr = dateStr,
                    cashierName = cashier,
                    status = status,
                    isInventoryDeducted = deducted,
                    cogsCost = cogs
                )
            )
        }
        return result
    }

    // --- JSON Encoding & Decoding Helpers ---

    private fun encodeCashiers(list: List<CashierProfile>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(c.id)}\",\"name\":\"${escape(c.name)}\",\"isActive\":${c.isActive},\"pin\":${c.pin?.let { "\"${escape(it)}\"" } ?: "null"},\"createdAt\":${c.createdAt}}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeCashiers(json: String): List<CashierProfile> {
        val result = mutableListOf<CashierProfile>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val name = map["name"] ?: continue
            val isActive = map["isActive"]?.toBoolean() ?: true
            val pin = map["pin"]?.takeIf { it != "null" }
            val createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            result.add(CashierProfile(id, name, isActive, pin, createdAt))
        }
        return if (result.isNotEmpty()) result else defaultCashiers()
    }

    private fun encodeCategories(list: List<Category>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(c.id)}\",\"name\":\"${escape(c.name)}\",\"displayOrder\":${c.displayOrder},\"isActive\":${c.isActive},\"emoji\":\"${escape(c.emoji)}\",\"isDefault\":${c.isDefault}}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeCategories(json: String): List<Category> {
        val result = mutableListOf<Category>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val name = map["name"] ?: continue
            val displayOrder = map["displayOrder"]?.toIntOrNull() ?: 0
            val isActive = map["isActive"]?.toBoolean() ?: true
            val emoji = map["emoji"] ?: "🍦"
            val isDefault = map["isDefault"]?.toBoolean() ?: false
            result.add(Category(id, name, displayOrder, isActive, emoji, isDefault))
        }
        return if (result.isNotEmpty()) result else defaultCategories()
    }

    private fun encodeIngredients(list: List<Ingredient>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, ing ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(ing.id)}\",\"name\":\"${escape(ing.name)}\",\"category\":\"${escape(ing.category)}\",\"unit\":\"${escape(ing.unit)}\",\"currentStock\":${ing.currentStock},\"minStockAlert\":${ing.minStockAlert},\"unitCostCOP\":${ing.unitCostCOP},\"isActive\":${ing.isActive},\"batchCode\":\"${escape(ing.batchCode)}\",\"expiryDate\":\"${escape(ing.expiryDate)}\"}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeIngredients(json: String): List<Ingredient> {
        val result = mutableListOf<Ingredient>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val name = map["name"] ?: continue
            val category = map["category"] ?: "Insumos"
            val unit = map["unit"] ?: "g"
            val currentStock = map["currentStock"]?.toDoubleOrNull() ?: 0.0
            val minStockAlert = map["minStockAlert"]?.toDoubleOrNull() ?: 100.0
            val unitCostCOP = map["unitCostCOP"]?.toLongOrNull() ?: 0L
            val isActive = map["isActive"]?.toBoolean() ?: true
            val batchCode = map["batchCode"] ?: "LOT-GEN"
            val expiryDate = map["expiryDate"] ?: "31/12/2026"
            result.add(Ingredient(id, name, category, unit, currentStock, minStockAlert, unitCostCOP, isActive, batchCode, expiryDate))
        }
        return if (result.isNotEmpty()) result else defaultIngredients()
    }

    private fun encodeProducts(list: List<Product>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, p ->
            if (i > 0) sb.append(",")
            val recipeJson = encodeRecipeList(p.recipe)
            val comboJson = encodeComboList(p.comboItems)
            sb.append("{\"id\":\"${escape(p.id)}\",\"name\":\"${escape(p.name)}\",\"description\":\"${escape(p.description)}\",\"basePrice\":${p.basePrice},\"baseCost\":${p.baseCost},\"categoryId\":\"${escape(p.categoryId)}\",\"imagePath\":${p.imagePath?.let { "\"${escape(it)}\"" } ?: "null"},\"emoji\":\"${escape(p.emoji)}\",\"badge\":${p.badge?.let { "\"${escape(it)}\"" } ?: "null"},\"isActive\":${p.isActive},\"isCombo\":${p.isCombo},\"stockQuantity\":${p.stockQuantity},\"minStockAlert\":${p.minStockAlert},\"unit\":\"${escape(p.unit)}\",\"allowNegativeStock\":${p.allowNegativeStock},\"recipe\":$recipeJson,\"comboItems\":$comboJson,\"createdAt\":${p.createdAt},\"updatedAt\":${p.updatedAt},\"baseIngredientId\":${p.baseIngredientId?.let { "\"${escape(it)}\"" } ?: "null"}}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeProducts(json: String): List<Product> {
        val result = mutableListOf<Product>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val name = map["name"] ?: continue
            val description = map["description"] ?: ""
            val basePrice = map["basePrice"]?.toLongOrNull() ?: 0L
            val baseCost = map["baseCost"]?.toLongOrNull() ?: 0L
            val categoryId = map["categoryId"] ?: "helados"
            val imagePath = map["imagePath"]?.takeIf { it != "null" }
            val emoji = map["emoji"] ?: "🍨"
            val badge = map["badge"]?.takeIf { it != "null" }
            val isActive = map["isActive"]?.toBoolean() ?: true
            val isCombo = map["isCombo"]?.toBoolean() ?: false
            val stockQuantity = map["stockQuantity"]?.toDoubleOrNull() ?: 0.0
            val minStockAlert = map["minStockAlert"]?.toDoubleOrNull() ?: 5.0
            val unit = map["unit"] ?: "unidades"
            val allowNegativeStock = map["allowNegativeStock"]?.toBoolean() ?: false
            val recipe = decodeRecipeList(map["recipe"] ?: "[]")
            val comboItems = decodeComboList(map["comboItems"] ?: "[]")
            val createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            val updatedAt = map["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            val baseIngredientId = map["baseIngredientId"]?.takeIf { it != "null" }

            val defaultTemplate = defaultProducts().find { it.id == id }
            val variants = defaultTemplate?.variants ?: emptyList()
            val allowedToppings = defaultTemplate?.allowedToppings ?: true

            result.add(
                Product(
                    id = id,
                    name = name,
                    description = description,
                    basePrice = basePrice,
                    baseCost = baseCost,
                    categoryId = categoryId,
                    imagePath = imagePath,
                    emoji = emoji,
                    badge = badge,
                    isActive = isActive,
                    isCombo = isCombo,
                    stockQuantity = stockQuantity,
                    minStockAlert = minStockAlert,
                    unit = unit,
                    allowNegativeStock = allowNegativeStock,
                    recipe = recipe.ifEmpty { defaultTemplate?.recipe ?: emptyList() },
                    comboItems = comboItems.ifEmpty { defaultTemplate?.comboItems ?: emptyList() },
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    variants = variants,
                    allowedToppings = allowedToppings,
                    baseIngredientId = baseIngredientId
                )
            )
        }
        return if (result.isNotEmpty()) result else defaultProducts()
    }

    private fun encodeRecipeList(recipe: List<RecipeItem>): String {
        val sb = StringBuilder("[")
        recipe.forEachIndexed { idx, r ->
            if (idx > 0) sb.append(",")
            sb.append("{\"ingredientId\":\"${escape(r.ingredientId)}\",\"ingredientName\":\"${escape(r.ingredientName)}\",\"quantity\":${r.quantity},\"unit\":\"${escape(r.unit)}\"}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeRecipeList(json: String): List<RecipeItem> {
        val list = mutableListOf<RecipeItem>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["ingredientId"] ?: continue
            val name = map["ingredientName"] ?: ""
            val qty = map["quantity"]?.toDoubleOrNull() ?: 0.0
            val unit = map["unit"] ?: "g"
            list.add(RecipeItem(id, name, qty, unit))
        }
        return list
    }

    private fun encodeComboList(combo: List<ComboItem>): String {
        val sb = StringBuilder("[")
        combo.forEachIndexed { idx, c ->
            if (idx > 0) sb.append(",")
            sb.append("{\"productId\":\"${escape(c.productId)}\",\"productName\":\"${escape(c.productName)}\",\"quantity\":${c.quantity}}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeComboList(json: String): List<ComboItem> {
        val list = mutableListOf<ComboItem>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["productId"] ?: continue
            val name = map["productName"] ?: ""
            val qty = map["quantity"]?.toIntOrNull() ?: 1
            list.add(ComboItem(id, name, qty))
        }
        return list
    }

    private fun encodeMovements(list: List<InventoryMovement>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, m ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${escape(m.id)}\",\"itemId\":\"${escape(m.itemId)}\",\"itemName\":\"${escape(m.itemName)}\",\"itemType\":\"${escape(m.itemType)}\",\"previousStock\":${m.previousStock},\"change\":${m.change},\"resultingStock\":${m.resultingStock},\"type\":\"${escape(m.type)}\",\"reason\":\"${escape(m.reason)}\",\"saleId\":${m.saleId?.let { "\"${escape(it)}\"" } ?: "null"},\"date\":${m.date},\"dateFormatted\":\"${escape(m.dateFormatted)}\",\"userName\":\"${escape(m.userName)}\"}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun decodeMovements(json: String): List<InventoryMovement> {
        val result = mutableListOf<InventoryMovement>()
        for (item in splitJsonArray(json)) {
            val map = parseSimpleJsonObject(item)
            val id = map["id"] ?: continue
            val itemId = map["itemId"] ?: continue
            val itemName = map["itemName"] ?: ""
            val itemType = map["itemType"] ?: "INGREDIENT"
            val previousStock = map["previousStock"]?.toDoubleOrNull() ?: 0.0
            val change = map["change"]?.toDoubleOrNull() ?: 0.0
            val resultingStock = map["resultingStock"]?.toDoubleOrNull() ?: 0.0
            val type = map["type"] ?: "MANUAL_ADJUSTMENT"
            val reason = map["reason"] ?: ""
            val saleId = map["saleId"]?.takeIf { it != "null" }
            val date = map["date"]?.toLongOrNull() ?: System.currentTimeMillis()
            val dateFormatted = map["dateFormatted"] ?: ""
            val userName = map["userName"] ?: ""
            result.add(
                InventoryMovement(
                    id = id,
                    itemId = itemId,
                    itemName = itemName,
                    itemType = itemType,
                    previousStock = previousStock,
                    change = change,
                    resultingStock = resultingStock,
                    type = type,
                    reason = reason,
                    saleId = saleId,
                    date = date,
                    dateFormatted = dateFormatted,
                    userName = userName
                )
            )
        }
        return result
    }

    private fun escape(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun splitJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) return emptyList()

        val list = mutableListOf<String>()
        var depth = 0
        var inQuotes = false
        var isEscape = false
        val current = StringBuilder()

        for (ch in content) {
            if (isEscape) {
                current.append(ch)
                isEscape = false
                continue
            }
            if (ch == '\\') {
                current.append(ch)
                isEscape = true
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
                current.append(ch)
                continue
            }
            if (!inQuotes) {
                if (ch == '{') depth++
                if (ch == '}') depth--
                if (ch == ',' && depth == 0) {
                    list.add(current.toString().trim())
                    current.clear()
                    continue
                }
            }
            current.append(ch)
        }
        if (current.isNotBlank()) {
            list.add(current.toString().trim())
        }
        return list
    }

    private fun parseSimpleJsonObject(json: String): Map<String, String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) return emptyMap()

        val map = mutableMapOf<String, String>()
        var inQuotes = false
        var isEscape = false
        var inKey = true
        var depth = 0
        val currentKey = StringBuilder()
        val currentValue = StringBuilder()

        var i = 0
        while (i < content.length) {
            val ch = content[i]
            if (isEscape) {
                if (inKey) currentKey.append(ch) else currentValue.append(ch)
                isEscape = false
                i++
                continue
            }
            if (ch == '\\') {
                isEscape = true
                i++
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes) {
                if (ch == '[' || ch == '{') depth++
                if (ch == ']' || ch == '}') depth--

                if (ch == ':' && depth == 0 && inKey) {
                    inKey = false
                    i++
                    continue
                }
                if (ch == ',' && depth == 0) {
                    val k = currentKey.toString().trim()
                    val v = currentValue.toString().trim()
                    if (k.isNotEmpty()) map[k] = v
                    currentKey.clear()
                    currentValue.clear()
                    inKey = true
                    i++
                    continue
                }
            }
            if (inKey) currentKey.append(ch) else currentValue.append(ch)
            i++
        }
        val k = currentKey.toString().trim()
        val v = currentValue.toString().trim()
        if (k.isNotEmpty()) map[k] = v
        return map
    }

    companion object {
        const val KEY_CASHIERS = "pos_cashiers_json"
        const val KEY_ACTIVE_CASHIER_ID = "pos_active_cashier_id"
        const val KEY_CATEGORIES = "pos_categories_json"
        const val KEY_PRODUCTS = "pos_products_json"
        const val KEY_INGREDIENTS = "pos_ingredients_json"
        const val KEY_MOVEMENTS = "pos_movements_json"
        const val KEY_SALES = "pos_sales_json"
        const val KEY_EXPENSES = "pos_expenses_json"
        const val KEY_PAYMENT_TRANSACTIONS = "pos_payment_transactions_json"

        @Volatile
        private var INSTANCE: PosRepository? = null

        fun getInstance(context: Context): PosRepository {
            return INSTANCE ?: synchronized(this) {
                val prefs = context.applicationContext.getSharedPreferences("ambiorix_pos_store", Context.MODE_PRIVATE)
                val repo = PosRepository(context.applicationContext, SharedPreferencesStorage(prefs))
                INSTANCE = repo
                repo
            }
        }

        fun createForTesting(storage: PosStorage = InMemoryPosStorage()): PosRepository {
            return PosRepository(null, storage)
        }

        fun defaultCashiers(): List<CashierProfile> = listOf(
            CashierProfile("cashier-1", "Laura Gómez", isActive = true),
            CashierProfile("cashier-2", "Camila Restrepo", isActive = true),
            CashierProfile("cashier-3", "Valentina Ortiz", isActive = true)
        )

        fun defaultCategories(): List<Category> = listOf(
            Category("helados", "Helados", 0, true, "🍨", true),
            Category("gourmet", "Gourmet", 1, true, "✨", false),
            Category("paletas", "Paletas", 2, true, "🍧", false),
            Category("copas", "Copas", 3, true, "🧇", false),
            Category("bebidas", "Bebidas", 4, true, "🥤", false),
            Category("combos", "Combos", 5, true, "🎁", false)
        )

        fun defaultIngredients(): List<Ingredient> = listOf(
            Ingredient("ing-1", "Base Helado Vainilla Francesa", "Helados", "g", 5000.0, 1000.0, 22L, true, "LOT-2026-V01", "20/11/2026"),
            Ingredient("ing-2", "Base Helado Chocolate Intenso", "Helados", "g", 4000.0, 1000.0, 25L, true, "LOT-2026-CH02", "18/11/2026"),
            Ingredient("ing-3", "Base Helado Arequipe Manjar", "Helados", "g", 3000.0, 800.0, 24L, true, "LOT-2026-AR01", "25/11/2026"),
            Ingredient("ing-4", "Base Helado Fresa Silvestre", "Helados", "g", 2500.0, 800.0, 23L, true, "LOT-2026-FR03", "15/11/2026"),
            Ingredient("ing-5", "Base Helado Pistacho Bronte", "Helados", "g", 1500.0, 600.0, 38L, true, "LOT-2026-PIS01", "01/12/2026"),
            Ingredient("ing-banano", "Banano Criollo Maduro", "Frutas", "unidades", 40.0, 10.0, 800L, true, "LOT-BAN-01", "10/11/2026"),
            Ingredient("ing-cereza", "Cerezas Marrasquino", "Frutas", "unidades", 60.0, 15.0, 500L, true, "LOT-CER-01", "15/12/2026"),
            Ingredient("ing-salsa-choc", "Salsa de Chocolate Artesanal", "Salsas", "ml", 1200.0, 300.0, 20L, true, "LOT-SCH-01", "10/01/2027"),
            Ingredient("ing-6", "Barquillos Artesanales Dulces", "Insumos", "unidades", 100.0, 20.0, 400L, true, "LOT-BARQ-99", "30/12/2026"),
            Ingredient("ing-7", "Vasos Térmicos Biodegradables", "Insumos", "unidades", 150.0, 30.0, 250L, true, "LOT-VAS-102", "01/01/2027"),
            Ingredient("ing-8", "Chispas de Chocolate Semiamargo", "Toppings", "g", 1200.0, 300.0, 28L, true, "LOT-CHIS-44", "15/02/2027"),
            Ingredient("ing-9", "Salsa Arequipe Tradicional", "Toppings", "g", 1000.0, 250.0, 20L, true, "LOT-AREQ-12", "10/12/2026"),
            Ingredient("ing-10", "Crema Chantilly Batida", "Toppings", "g", 1000.0, 200.0, 30L, true, "LOT-CHAN-08", "05/11/2026")
        )

        fun defaultToppings(): List<ToppingOption> = listOf(
            ToppingOption("top-1", "Chispas de Chocolate", 1500L, "ing-8", 15),
            ToppingOption("top-2", "Salsa de Arequipe", 1500L, "ing-9", 25),
            ToppingOption("top-3", "Crema Chantilly", 2000L, "ing-10", 30),
            ToppingOption("top-4", "Lluvia de Maní", 1500L, null, 15),
            ToppingOption("top-5", "Cereza Marrasquino", 1000L, "ing-cereza", 1),
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
                stockQuantity = 45.0,
                minStockAlert = 10.0,
                unit = "unidades",
                baseIngredientId = "ing-1",
                recipe = listOf(
                    RecipeItem("ing-1", "Base Helado Vainilla Francesa", 80.0, "g"),
                    RecipeItem("ing-6", "Barquillos Artesanales Dulces", 1.0, "unidades")
                ),
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
                stockQuantity = 38.0,
                minStockAlert = 10.0,
                unit = "unidades",
                baseIngredientId = "ing-2",
                recipe = listOf(
                    RecipeItem("ing-2", "Base Helado Chocolate Intenso", 80.0, "g"),
                    RecipeItem("ing-6", "Barquillos Artesanales Dulces", 1.0, "unidades")
                ),
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
                stockQuantity = 22.0,
                minStockAlert = 8.0,
                unit = "unidades",
                baseIngredientId = "ing-3",
                recipe = listOf(
                    RecipeItem("ing-3", "Base Helado Arequipe Manjar", 80.0, "g"),
                    RecipeItem("ing-6", "Barquillos Artesanales Dulces", 1.0, "unidades")
                ),
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
                stockQuantity = 18.0,
                minStockAlert = 8.0,
                unit = "unidades",
                baseIngredientId = "ing-4",
                recipe = listOf(
                    RecipeItem("ing-4", "Base Helado Fresa Silvestre", 80.0, "g"),
                    RecipeItem("ing-6", "Barquillos Artesanales Dulces", 1.0, "unidades")
                ),
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
                stockQuantity = 14.0,
                minStockAlert = 5.0,
                unit = "unidades",
                baseIngredientId = "ing-5",
                recipe = listOf(
                    RecipeItem("ing-5", "Base Helado Pistacho Bronte", 80.0, "g"),
                    RecipeItem("ing-6", "Barquillos Artesanales Dulces", 1.0, "unidades")
                ),
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
                stockQuantity = 30.0,
                minStockAlert = 10.0,
                unit = "unidades",
                baseIngredientId = "ing-4",
                allowedToppings = false,
                variants = listOf(
                    ProductVariant("var-1", "Paleta Unitaria", 0L, weightGrams = 100)
                )
            ),
            // Exemplary Recipe Product requested by User in Prompt Section 5
            Product(
                id = "prod-7",
                categoryId = "copas",
                name = "Banana Split Clásica",
                description = "Banano, 2 bolas de helado, salsa de chocolate, chantilly y cereza",
                basePrice = 18000L,
                baseCost = 5500L,
                emoji = "🍌",
                badge = "Clásica",
                stockQuantity = 20.0,
                minStockAlert = 4.0,
                unit = "unidades",
                recipe = listOf(
                    RecipeItem("ing-banano", "Banano Criollo Maduro", 1.0, "unidades"),
                    RecipeItem("ing-1", "Base Helado Vainilla Francesa", 100.0, "g"),
                    RecipeItem("ing-2", "Base Helado Chocolate Intenso", 100.0, "g"),
                    RecipeItem("ing-salsa-choc", "Salsa de Chocolate Artesanal", 20.0, "ml"),
                    RecipeItem("ing-10", "Crema Chantilly Batida", 30.0, "g"),
                    RecipeItem("ing-cereza", "Cerezas Marrasquino", 1.0, "unidades")
                ),
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
                stockQuantity = 20.0,
                minStockAlert = 5.0,
                unit = "unidades",
                baseIngredientId = "ing-2",
                recipe = listOf(
                    RecipeItem("ing-2", "Base Helado Chocolate Intenso", 150.0, "g"),
                    RecipeItem("ing-7", "Vasos Térmicos Biodegradables", 1.0, "unidades")
                ),
                variants = listOf(
                    ProductVariant("var-1", "Vaso 16oz", 0L, weightGrams = 200, cupUnits = 1)
                )
            ),
            // Exemplary Combo Product requested by User in Prompt Section 6
            Product(
                id = "prod-combo-1",
                categoryId = "combos",
                name = "Combo Pareja Dulce",
                description = "1 Banana Split Clásica + 1 Malteada Espesa",
                basePrice = 28000L,
                baseCost = 8500L,
                emoji = "🎁",
                badge = "Combo",
                isCombo = true,
                stockQuantity = 10.0,
                minStockAlert = 2.0,
                unit = "unidades",
                comboItems = listOf(
                    ComboItem("prod-7", "Banana Split Clásica", 1),
                    ComboItem("prod-8", "Malteada Espesa", 1)
                )
            )
        )
    }

    fun defaultPaymentTransactions(): List<PaymentTransaction> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val date1 = String.format(Locale.US, "%04d-%02d-05", y, m)
        val date2 = String.format(Locale.US, "%04d-%02d-12", y, m)
        val date3 = String.format(Locale.US, "%04d-%02d-18", y, m)

        return listOf(
            PaymentTransaction(
                id = "tx-today-1",
                method = "NEQUI",
                amount = 28000L,
                dateStr = today,
                time = "10:15 AM",
                status = "CONFIRMED",
                saleId = "sale-101",
                saleNumber = "V-00101",
                cashierName = "Laura Gómez",
                payerNameOrPhone = "Carlos Mendoza (3124567890)",
                bankTransactionRef = "M884920",
                smsEventId = "SMS-20261026-001",
                originInstallation = "Tienda Local",
                nequiChannel = "App Nequi"
            ),
            PaymentTransaction(
                id = "tx-today-2",
                method = "NEQUI",
                amount = 14500L,
                dateStr = today,
                time = "11:40 AM",
                status = "CONFIRMED",
                saleId = "sale-102",
                saleNumber = "V-00102",
                cashierName = "Laura Gómez",
                payerNameOrPhone = "Valentina Ríos",
                bankTransactionRef = null, // Explicitly tests fallback: "No disponible en el SMS"
                smsEventId = "SMS-20261026-002",
                originInstallation = "Tienda Local",
                nequiChannel = "Bre-B"
            ),
            PaymentTransaction(
                id = "tx-today-3",
                method = "CASH",
                amount = 22000L,
                dateStr = today,
                time = "12:30 PM",
                status = "CONFIRMED",
                saleId = "sale-103",
                saleNumber = "V-00103",
                cashierName = "Laura Gómez",
                entryType = "SALE_PAYMENT",
                notes = "Pago con billete de $50.000"
            ),
            PaymentTransaction(
                id = "tx-today-4",
                method = "CASH",
                amount = 50000L,
                dateStr = today,
                time = "09:00 AM",
                status = "CONFIRMED",
                saleId = null,
                saleNumber = null,
                cashierName = "Laura Gómez",
                entryType = "MANUAL_CASH_IN",
                notes = "Fondo de sencillo adicional para vueltas"
            ),
            // Past day 1
            PaymentTransaction(
                id = "tx-p1-1",
                method = "NEQUI",
                amount = 45000L,
                dateStr = date1,
                time = "03:10 PM",
                status = "CONFIRMED",
                saleId = "sale-p1-1",
                saleNumber = "V-00085",
                cashierName = "Camila Restrepo",
                payerNameOrPhone = "Andrés Morales",
                bankTransactionRef = "M765431",
                smsEventId = "SMS-20261005-010",
                originInstallation = "Tienda Local"
            ),
            PaymentTransaction(
                id = "tx-p1-2",
                method = "CASH",
                amount = 32000L,
                dateStr = date1,
                time = "04:25 PM",
                status = "CONFIRMED",
                saleId = "sale-p1-2",
                saleNumber = "V-00086",
                cashierName = "Camila Restrepo",
                entryType = "SALE_PAYMENT"
            ),
            // Past day 2
            PaymentTransaction(
                id = "tx-p2-1",
                method = "NEQUI",
                amount = 62000L,
                dateStr = date2,
                time = "02:20 PM",
                status = "CONFIRMED",
                saleId = "sale-p2-1",
                saleNumber = "V-00092",
                cashierName = "Laura Gómez",
                payerNameOrPhone = "Mariana Duque",
                bankTransactionRef = null, // "No disponible en el SMS"
                smsEventId = "SMS-20261012-005",
                originInstallation = "Tienda Remota: Pizzería"
            ),
            // Past day 3
            PaymentTransaction(
                id = "tx-p3-1",
                method = "CASH",
                amount = 54000L,
                dateStr = date3,
                time = "05:15 PM",
                status = "CONFIRMED",
                saleId = "sale-p3-1",
                saleNumber = "V-00098",
                cashierName = "Valentina Ortiz",
                entryType = "SALE_PAYMENT"
            )
        )
    }

    fun defaultExpenses(): List<ExpenseRecord> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val date1 = String.format(Locale.US, "%04d-%02d-05", y, m)
        val date2 = String.format(Locale.US, "%04d-%02d-12", y, m)

        return listOf(
            ExpenseRecord(
                id = "exp-today-1",
                amount = 18000L,
                category = "Insumos",
                description = "Compra de 6 litros de leche entera fresca",
                paymentSource = "Caja efectivo",
                cashierName = "Laura Gómez",
                notes = "Factura prov #441",
                dateStr = today,
                time = "10:45 AM",
                isLinkedToInventory = true,
                status = "ACTIVE"
            ),
            ExpenseRecord(
                id = "exp-today-2",
                amount = 12000L,
                category = "Transporte",
                description = "Domicilio express de frutas frescas",
                paymentSource = "Caja efectivo",
                cashierName = "Laura Gómez",
                notes = "Envío moto",
                dateStr = today,
                time = "11:20 AM",
                isLinkedToInventory = false,
                status = "ACTIVE"
            ),
            ExpenseRecord(
                id = "exp-p1-1",
                amount = 25000L,
                category = "Reposición inventario",
                description = "100 Barquillos dulces artesanales",
                paymentSource = "Caja efectivo",
                cashierName = "Camila Restrepo",
                notes = "Reposición stock de barquillos",
                dateStr = date1,
                time = "02:00 PM",
                isLinkedToInventory = true,
                status = "ACTIVE"
            ),
            ExpenseRecord(
                id = "exp-p2-1",
                amount = 35000L,
                category = "Servicios",
                description = "Pago de servicio de internet banda ancha local",
                paymentSource = "Nequi",
                cashierName = "Laura Gómez",
                notes = "Recibo Claro",
                dateStr = date2,
                time = "09:30 AM",
                isLinkedToInventory = false,
                status = "ACTIVE"
            )
        )
    }

    fun defaultSales(): List<SaleRecord> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val date1 = String.format(Locale.US, "%04d-%02d-05", y, m)

        return listOf(
            SaleRecord(
                id = "sale-101",
                saleNumber = "V-00101",
                items = listOf(
                    CartItem(
                        cartItemId = "cart-101",
                        productId = "prod-combo-1",
                        productName = "Combo Pareja Dulce",
                        basePrice = 28000L,
                        unitPrice = 28000L,
                        quantity = 1,
                        totalPrice = 28000L,
                        estimatedCost = 8500L
                    )
                ),
                subtotal = 28000L,
                discount = 0L,
                total = 28000L,
                paymentMethod = "NEQUI",
                cashAmount = 0L,
                nequiAmount = 28000L,
                nequiSenderName = "Carlos Mendoza",
                nequiChannel = "App Nequi",
                timestamp = "10:15 AM",
                dateStr = today,
                cashierName = "Laura Gómez",
                status = "COMPLETED"
            ),
            SaleRecord(
                id = "sale-102",
                saleNumber = "V-00102",
                items = listOf(
                    CartItem(
                        cartItemId = "cart-102-1",
                        productId = "prod-1",
                        productName = "Cono Vainilla Francesa",
                        basePrice = 6500L,
                        unitPrice = 6500L,
                        quantity = 1,
                        totalPrice = 6500L,
                        estimatedCost = 1500L
                    ),
                    CartItem(
                        cartItemId = "cart-102-2",
                        productId = "prod-3",
                        productName = "Paleta Artesanal de Arequipe",
                        basePrice = 8000L,
                        unitPrice = 8000L,
                        quantity = 1,
                        totalPrice = 8000L,
                        estimatedCost = 2100L
                    )
                ),
                subtotal = 14500L,
                discount = 0L,
                total = 14500L,
                paymentMethod = "NEQUI",
                cashAmount = 0L,
                nequiAmount = 14500L,
                nequiSenderName = "Valentina Ríos",
                nequiChannel = "Bre-B",
                timestamp = "11:40 AM",
                dateStr = today,
                cashierName = "Laura Gómez",
                status = "COMPLETED"
            ),
            SaleRecord(
                id = "sale-103",
                saleNumber = "V-00103",
                items = listOf(
                    CartItem(
                        cartItemId = "cart-103-1",
                        productId = "prod-2",
                        productName = "Copa Chocolate Suprema",
                        basePrice = 11000L,
                        unitPrice = 11000L,
                        quantity = 2,
                        totalPrice = 22000L,
                        estimatedCost = 5000L
                    )
                ),
                subtotal = 22000L,
                discount = 0L,
                total = 22000L,
                paymentMethod = "CASH",
                cashAmount = 22000L,
                nequiAmount = 0L,
                timestamp = "12:30 PM",
                dateStr = today,
                cashierName = "Laura Gómez",
                status = "COMPLETED"
            ),
            SaleRecord(
                id = "sale-p1-1",
                saleNumber = "V-00085",
                items = listOf(
                    CartItem(
                        cartItemId = "cart-p1-1",
                        productId = "prod-combo-1",
                        productName = "Combo Pareja Dulce",
                        basePrice = 28000L,
                        unitPrice = 28000L,
                        quantity = 1,
                        totalPrice = 28000L,
                        estimatedCost = 8500L
                    ),
                    CartItem(
                        cartItemId = "cart-p1-2",
                        productId = "prod-7",
                        productName = "Banana Split Clásica",
                        basePrice = 17000L,
                        unitPrice = 17000L,
                        quantity = 1,
                        totalPrice = 17000L,
                        estimatedCost = 4200L
                    )
                ),
                subtotal = 45000L,
                discount = 0L,
                total = 45000L,
                paymentMethod = "NEQUI",
                cashAmount = 0L,
                nequiAmount = 45000L,
                timestamp = "03:10 PM",
                dateStr = date1,
                cashierName = "Camila Restrepo",
                status = "COMPLETED"
            )
        )
    }
}


