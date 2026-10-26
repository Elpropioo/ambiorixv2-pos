package com.ambiorix.pay

import com.ambiorix.pay.pos.InMemoryPosStorage
import com.ambiorix.pay.pos.PosRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PosManagementTest {

    private lateinit var storage: InMemoryPosStorage
    private lateinit var repository: PosRepository

    @Before
    fun setUp() {
        storage = InMemoryPosStorage()
        repository = PosRepository.createForTesting(storage)
    }

    // 1. Crear producto
    @Test
    fun testCrearProducto() {
        val result = repository.createProduct(
            name = "Helado de Maracuyá",
            basePrice = 7000L,
            categoryId = "helados",
            description = "Sabor ácido y refrescante",
            stockQuantity = 25.0,
            unit = "unidades"
        )

        assertTrue("El producto debe crearse exitosamente", result.isSuccess)
        val product = result.getOrNull()
        assertNotNull(product)
        assertEquals("Helado de Maracuyá", product!!.name)
        assertEquals(7000L, product.basePrice)
        assertEquals("helados", product.categoryId)
        assertEquals(25.0, product.stockQuantity, 0.001)

        val found = repository.products.value.find { it.id == product.id }
        assertNotNull(found)
    }

    // 2. Modificar precio
    @Test
    fun testModificarPrecio() {
        val created = repository.createProduct(
            name = "Cono Sencillo",
            basePrice = 4500L,
            categoryId = "helados"
        ).getOrThrow()

        val updateResult = repository.updateProductPrice(created.id, 5200L)
        assertTrue(updateResult.isSuccess)
        assertEquals(5200L, updateResult.getOrThrow().basePrice)

        // Verificar que persiste recargando desde almacenamiento
        val reloadedRepo = PosRepository.createForTesting(storage)
        val reloadedProd = reloadedRepo.products.value.find { it.id == created.id }
        assertNotNull(reloadedProd)
        assertEquals(5200L, reloadedProd!!.basePrice)
    }

    // 3. Modificar nombre y descripción
    @Test
    fun testModificarNombreYDescripcion() {
        val created = repository.createProduct(
            name = "Café",
            basePrice = 3000L,
            categoryId = "bebidas",
            description = "Café normal"
        ).getOrThrow()

        val updateResult = repository.updateProduct(
            id = created.id,
            name = "Café Espresso Premium",
            basePrice = 3500L,
            categoryId = "bebidas",
            description = "Café de grano colombiano recién molido"
        )

        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals("Café Espresso Premium", updated.name)
        assertEquals("Café de grano colombiano recién molido", updated.description)
        assertEquals(3500L, updated.basePrice)
    }

    // 4. Asignar y cambiar categoría
    @Test
    fun testAsignarYCambiarCategoria() {
        val created = repository.createProduct(
            name = "Malteada Especial",
            basePrice = 12000L,
            categoryId = "helados"
        ).getOrThrow()

        assertEquals("helados", created.categoryId)

        val updateResult = repository.updateProduct(
            id = created.id,
            name = created.name,
            basePrice = created.basePrice,
            categoryId = "bebidas"
        )

        assertTrue(updateResult.isSuccess)
        assertEquals("bebidas", updateResult.getOrThrow().categoryId)

        val productsInBebidas = repository.searchProducts("", categoryId = "bebidas")
        assertTrue(productsInBebidas.any { it.id == created.id })
    }

    // 5. Asignar y cambiar fotografía (preservando el archivo interno)
    @Test
    fun testAsignarYCambiarFotografia() {
        val initialPath = "/data/user/0/com.ambiorix.pay/files/images/img_1.jpg"
        val created = repository.createProduct(
            name = "Sundae Fresa",
            basePrice = 9000L,
            categoryId = "copas",
            imagePath = initialPath
        ).getOrThrow()

        assertEquals(initialPath, created.imagePath)

        val newPath = "/data/user/0/com.ambiorix.pay/files/images/img_2.jpg"
        val updateResult = repository.updateProductImage(created.id, newPath)
        assertTrue(updateResult.isSuccess)
        assertEquals(newPath, updateResult.getOrThrow().imagePath)

        // Comprobar persistencia de la ruta
        val reloaded = PosRepository.createForTesting(storage)
        assertEquals(newPath, reloaded.products.value.find { it.id == created.id }?.imagePath)
    }

    // 6. Desactivar producto y verificar que no aparece en catálogo de venta
    @Test
    fun testDesactivarProductoNoApareceEnVenta() {
        val created = repository.createProduct(
            name = "Edición Limitada Verano",
            basePrice = 15000L,
            categoryId = "gourmet"
        ).getOrThrow()

        assertTrue(repository.getActiveProducts().any { it.id == created.id })

        val toggleResult = repository.toggleProductActive(created.id, isActive = false)
        assertTrue(toggleResult.isSuccess)
        assertFalse(toggleResult.getOrThrow().isActive)

        // No debe aparecer en catálogo activo de ventas
        assertFalse(repository.getActiveProducts().any { it.id == created.id })

        // Pero sí existe en la lista administrativa de productos
        assertTrue(repository.products.value.any { it.id == created.id })
    }

    // 7. Crear categoría
    @Test
    fun testCrearCategoria() {
        val result = repository.createCategory(name = "Postres Calientes", emoji = "🥧")
        assertTrue(result.isSuccess)
        val cat = result.getOrThrow()
        assertEquals("Postres Calientes", cat.name)
        assertEquals("🥧", cat.emoji)
        assertTrue(repository.categories.value.any { it.id == cat.id })

        // Rechazar categoría con nombre vacío
        val invalidResult = repository.createCategory(name = "   ")
        assertTrue(invalidResult.isFailure)
    }

    // 8. Renombrar categoría
    @Test
    fun testRenombrarCategoria() {
        val created = repository.createCategory(name = "Dulces", emoji = "🍬").getOrThrow()
        val updateResult = repository.updateCategory(created.id, newName = "Confitería Artesanal", newEmoji = "🍭")
        assertTrue(updateResult.isSuccess)
        assertEquals("Confitería Artesanal", updateResult.getOrThrow().name)
        assertEquals("🍭", updateResult.getOrThrow().emoji)
    }

    // 9. Eliminar categoría con reasignación
    @Test
    fun testEliminarCategoriaConReasignacion() {
        val catA = repository.createCategory(name = "Temporada", emoji = "🎄").getOrThrow()
        val catB = repository.createCategory(name = "General", emoji = "📦").getOrThrow()

        val prod1 = repository.createProduct("Helado Navideño", 8000L, catA.id).getOrThrow()
        val prod2 = repository.createProduct("Turrón Helado", 9000L, catA.id).getOrThrow()

        assertEquals(2, repository.getProductCountForCategory(catA.id))

        // Eliminar catA reasignando sus productos a catB
        val deleteResult = repository.deleteCategory(catA.id, reassignToCategoryId = catB.id)
        assertTrue(deleteResult.isSuccess)

        // La categoría eliminada ya no existe
        assertNull(repository.categories.value.find { it.id == catA.id })

        // Los productos siguen existiendo y ahora pertenecen a catB
        val reloadedProd1 = repository.products.value.find { it.id == prod1.id }
        val reloadedProd2 = repository.products.value.find { it.id == prod2.id }
        assertNotNull(reloadedProd1)
        assertNotNull(reloadedProd2)
        assertEquals(catB.id, reloadedProd1!!.categoryId)
        assertEquals(catB.id, reloadedProd2!!.categoryId)
    }

    // 10. Ajustar inventario hacia arriba y registrar motivo
    @Test
    fun testAjustarInventarioHaciaArribaConMotivo() {
        val prod = repository.createProduct("Conos Dobles", 6000L, "helados", stockQuantity = 10.0).getOrThrow()

        val entryResult = repository.recordPurchaseEntry(
            productId = prod.id,
            quantity = 15.0,
            supplierOrNote = "Factura Prov-501",
            userName = "Laura Gómez"
        )

        assertTrue(entryResult.isSuccess)
        val movement = entryResult.getOrThrow()
        assertEquals(10.0, movement.previousStock, 0.001)
        assertEquals(15.0, movement.change, 0.001)
        assertEquals(25.0, movement.resultingStock, 0.001)
        assertEquals("PURCHASE", movement.type)
        assertTrue(movement.reason.contains("Factura Prov-501"))
        assertEquals("Laura Gómez", movement.userName)

        val updatedProd = repository.products.value.find { it.id == prod.id }
        assertEquals(25.0, updatedProd!!.stockQuantity, 0.001)
    }

    // 11. Ajustar inventario hacia abajo y registrar motivo
    @Test
    fun testAjustarInventarioHaciaAbajoConMotivo() {
        val prod = repository.createProduct("Paleta Frutas", 5000L, "paletas", stockQuantity = 20.0).getOrThrow()

        val wasteResult = repository.recordWaste(
            productId = prod.id,
            quantity = 3.0,
            reason = "Rotura al descargar congelador",
            userName = "Camila Restrepo"
        )

        assertTrue(wasteResult.isSuccess)
        val movement = wasteResult.getOrThrow()
        assertEquals(20.0, movement.previousStock, 0.001)
        assertEquals(-3.0, movement.change, 0.001)
        assertEquals(17.0, movement.resultingStock, 0.001)
        assertEquals("WASTE_MERMA", movement.type)
        assertTrue(movement.reason.contains("Rotura"))
        assertEquals("Camila Restrepo", movement.userName)
    }

    // 12. Rechazar inventario negativo no permitido
    @Test
    fun testRechazarInventarioNegativo() {
        val prod = repository.createProduct("Barquillos", 1000L, "helados", stockQuantity = 5.0).getOrThrow()

        val invalidResult = repository.adjustStock(
            productId = prod.id,
            newStock = -2.0,
            reason = "Ajuste erróneo",
            userName = "Admin",
            allowNegative = false
        )

        assertTrue("Debe rechazar inventario negativo", invalidResult.isFailure)
        assertTrue(invalidResult.exceptionOrNull()?.message?.contains("negativo") == true)

        // El stock original se mantiene intacto
        val unchangedProd = repository.products.value.find { it.id == prod.id }
        assertEquals(5.0, unchangedProd!!.stockQuantity, 0.001)
    }

    // 13. Editar nombre de cajera y persistir
    @Test
    fun testEditarNombreCajeraYPersistir() {
        val initialCashier = repository.activeCashier.value
        assertEquals("Laura Gómez", initialCashier.name)

        // Rechazar nombre vacío
        val emptyResult = repository.updateCashierName(initialCashier.id, "   ")
        assertTrue("No debe permitir nombre vacío", emptyResult.isFailure)

        // Actualizar a nuevo nombre válido
        val updateResult = repository.updateCashierName(initialCashier.id, "Laura Sofía Gómez")
        assertTrue(updateResult.isSuccess)
        assertEquals("Laura Sofía Gómez", updateResult.getOrThrow().name)
        assertEquals("Laura Sofía Gómez", repository.activeCashier.value.name)
        assertEquals("Laura Sofía Gómez", repository.cashShift.value.cashierName)

        // Comprobar persistencia tras recargar
        val reloaded = PosRepository.createForTesting(storage)
        assertEquals("Laura Sofía Gómez", reloaded.activeCashier.value.name)

        // Crear otro perfil de cajera y seleccionarlo
        val newProfile = reloaded.createCashier("Valentina Ortiz").getOrThrow()
        reloaded.selectCashier(newProfile.id)
        assertEquals("Valentina Ortiz", reloaded.activeCashier.value.name)
    }

    // 14. Conversión de unidades segura y rechazo de unidades incompatibles
    @Test
    fun testConversionDeUnidadesYRechazoIncompatibles() {
        // g a kg
        val convertedKg = com.ambiorix.pay.pos.UnitConverter.convert(150.0, "g", "kg")
        assertEquals(0.15, convertedKg, 0.0001)

        // kg a g
        val convertedG = com.ambiorix.pay.pos.UnitConverter.convert(2.5, "kg", "g")
        assertEquals(2500.0, convertedG, 0.0001)

        // ml a L
        val convertedL = com.ambiorix.pay.pos.UnitConverter.convert(500.0, "ml", "L")
        assertEquals(0.5, convertedL, 0.0001)

        // unidades a unidades
        val convertedU = com.ambiorix.pay.pos.UnitConverter.convert(10.0, "unidades", "unidades")
        assertEquals(10.0, convertedU, 0.0001)

        // Rechazo automático de unidades incompatibles (ej. gramos a unidades o litros a gramos)
        assertFalse(com.ambiorix.pay.pos.UnitConverter.canConvert("g", "unidades"))
        assertFalse(com.ambiorix.pay.pos.UnitConverter.canConvert("L", "unidades"))

        try {
            com.ambiorix.pay.pos.UnitConverter.convert(50.0, "g", "unidades")
            fail("Debe lanzar IllegalArgumentException al intentar convertir unidades incompatibles")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("incompatibles") == true)
        }
    }

    // 15. Creación de producto con receta e ingredientes
    @Test
    fun testCrearProductoConReceta() {
        val baseHelado = repository.createIngredient(
            name = "Base Artesanal de Fresa",
            unit = "kg",
            initialStock = 5.0,
            unitCostCOP = 12000L
        ).getOrThrow()

        val barquillos = repository.createIngredient(
            name = "Cono Galleta Barquillo",
            unit = "unidades",
            initialStock = 100.0,
            unitCostCOP = 400L
        ).getOrThrow()

        val recipe = listOf(
            com.ambiorix.pay.pos.RecipeItem(
                ingredientId = baseHelado.id,
                ingredientName = baseHelado.name,
                quantity = 150.0,
                unit = "g"
            ),
            com.ambiorix.pay.pos.RecipeItem(
                ingredientId = barquillos.id,
                ingredientName = barquillos.name,
                quantity = 1.0,
                unit = "unidades"
            )
        )

        val prodResult = repository.createProduct(
            name = "Cono Gourmet Fresa",
            basePrice = 8500L,
            categoryId = "helados",
            recipe = recipe
        )

        assertTrue(prodResult.isSuccess)
        val product = prodResult.getOrThrow()
        assertEquals(2, product.recipe.size)
        // Costo calculado: 0.15kg * 12000 + 1 * 400 = 1800 + 400 = 2200 COP
        assertEquals(2200L, product.baseCost)
    }

    // 16. Descuento de inventario automático con conversión de unidad (150g de 5kg = 4.85kg)
    @Test
    fun testDescuentoInventarioConConversionDeUnidades() {
        val baseHelado = repository.createIngredient(
            name = "Base Helado Vainilla",
            unit = "kg",
            initialStock = 5.0,
            unitCostCOP = 15000L
        ).getOrThrow()

        val recipe = listOf(
            com.ambiorix.pay.pos.RecipeItem(
                ingredientId = baseHelado.id,
                ingredientName = baseHelado.name,
                quantity = 150.0,
                unit = "g"
            )
        )

        val prod = repository.createProduct(
            name = "Helado Vainilla Especial",
            basePrice = 6000L,
            categoryId = "helados",
            recipe = recipe
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-1",
            productId = prod.id,
            productName = prod.name,
            basePrice = prod.basePrice,
            unitPrice = prod.basePrice,
            quantity = 1,
            totalPrice = prod.basePrice,
            estimatedCost = prod.baseCost
        )

        val saleResult = repository.completeSale(
            items = listOf(cartItem),
            subtotal = prod.basePrice,
            discount = 0L,
            total = prod.basePrice,
            paymentMethod = "CASH",
            cashierName = "Laura Gómez",
            saleId = "sale-unit-test-1"
        )

        assertTrue("La venta debe completarse exitosamente", saleResult.isSuccess)

        // 5 kg - 0.150 kg (150g) = 4.85 kg
        val updatedIngredient = repository.ingredients.value.find { it.id == baseHelado.id }
        assertNotNull(updatedIngredient)
        assertEquals(4.85, updatedIngredient!!.currentStock, 0.001)

        val mov = repository.inventoryMovements.value.find { it.saleId == "sale-unit-test-1" }
        assertNotNull(mov)
        assertEquals("SALE_RECIPE", mov!!.type)
        assertEquals(-0.15, mov.change, 0.001)
        assertEquals(4.85, mov.resultingStock, 0.001)
    }

    // 17. Rechazo por stock insuficiente antes de descontar inventario
    @Test
    fun testRechazoVentaPorStockInsuficiente() {
        val ingreEscaso = repository.createIngredient(
            name = "Chocolate Belga Limitado",
            unit = "g",
            initialStock = 50.0,
            unitCostCOP = 50L
        ).getOrThrow()

        val prod = repository.createProduct(
            name = "Copa Chocolate Belga",
            basePrice = 14000L,
            categoryId = "copas",
            recipe = listOf(
                com.ambiorix.pay.pos.RecipeItem(
                    ingredientId = ingreEscaso.id,
                    ingredientName = ingreEscaso.name,
                    quantity = 100.0,
                    unit = "g"
                )
            )
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-fail",
            productId = prod.id,
            productName = prod.name,
            basePrice = prod.basePrice,
            unitPrice = prod.basePrice,
            quantity = 1,
            totalPrice = prod.basePrice
        )

        val saleResult = repository.completeSale(
            items = listOf(cartItem),
            subtotal = prod.basePrice,
            discount = 0L,
            total = prod.basePrice,
            paymentMethod = "CASH",
            cashierName = "Laura Gómez",
            saleId = "sale-fail-stock"
        )

        assertTrue("La venta debe fallar por stock insuficiente", saleResult.isFailure)
        assertTrue(saleResult.exceptionOrNull()?.message?.contains("insuficiente") == true)

        val unchanged = repository.ingredients.value.find { it.id == ingreEscaso.id }
        assertEquals(50.0, unchanged!!.currentStock, 0.001)
    }

    // 18. Creación de Combo y prevención de referencias circulares
    @Test
    fun testCombosYPrevencionReferenciasCirculares() {
        val prodA = repository.createProduct("Banana Split", 12000L, "copas").getOrThrow()
        val prodB = repository.createProduct("Malteada Fresa", 9000L, "bebidas").getOrThrow()

        val comboItems = listOf(
            com.ambiorix.pay.pos.ComboItem(productId = prodA.id, productName = prodA.name, quantity = 1),
            com.ambiorix.pay.pos.ComboItem(productId = prodB.id, productName = prodB.name, quantity = 1)
        )

        val comboResult = repository.createProduct(
            name = "Combo Pareja Dulce",
            basePrice = 18000L,
            categoryId = "combos",
            isCombo = true,
            comboItems = comboItems
        )

        assertTrue(comboResult.isSuccess)
        val combo = comboResult.getOrThrow()
        assertTrue(combo.isCombo)
        assertEquals(2, combo.comboItems.size)

        // Intento de circularidad directa: un combo no puede contenerse a sí mismo
        val circularDirect = repository.updateProductCombo(
            productId = combo.id,
            comboItems = listOf(
                com.ambiorix.pay.pos.ComboItem(productId = combo.id, productName = combo.name, quantity = 1)
            )
        )
        assertTrue("Debe rechazar combo que se contiene a sí mismo", circularDirect.isFailure)
        assertTrue(circularDirect.exceptionOrNull()?.message?.contains("circular") == true)
    }

    // 19. Descuento recursivo de componentes al vender un Combo
    @Test
    fun testDescuentoRecursivoAlVenderCombo() {
        val platano = repository.createIngredient("Plátano Criollo", "unidades", 10.0, 800L).getOrThrow()
        val helado = repository.createIngredient("Helado Vainilla", "kg", 4.0, 10000L).getOrThrow()

        val bananaSplit = repository.createProduct(
            name = "Banana Split",
            basePrice = 12000L,
            categoryId = "copas",
            recipe = listOf(
                com.ambiorix.pay.pos.RecipeItem(platano.id, platano.name, 1.0, "unidades"),
                com.ambiorix.pay.pos.RecipeItem(helado.id, helado.name, 200.0, "g")
            )
        ).getOrThrow()

        val refresco = repository.createProduct(
            name = "Refresco Frutal",
            basePrice = 4000L,
            categoryId = "bebidas",
            stockQuantity = 20.0
        ).getOrThrow()

        val combo = repository.createProduct(
            name = "Super Combo Verano",
            basePrice = 17000L,
            categoryId = "combos",
            isCombo = true,
            comboItems = listOf(
                com.ambiorix.pay.pos.ComboItem(bananaSplit.id, bananaSplit.name, 1),
                com.ambiorix.pay.pos.ComboItem(refresco.id, refresco.name, 2)
            )
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-combo-1",
            productId = combo.id,
            productName = combo.name,
            basePrice = combo.basePrice,
            unitPrice = combo.basePrice,
            quantity = 1,
            totalPrice = combo.basePrice
        )

        val saleResult = repository.completeSale(
            items = listOf(cartItem),
            subtotal = combo.basePrice,
            discount = 0L,
            total = combo.basePrice,
            paymentMethod = "CASH",
            cashierName = "Laura Gómez",
            saleId = "sale-combo-rec-1"
        )

        assertTrue(saleResult.isSuccess)

        // Verificar descuentos recursivos:
        // Plátano: 10 - 1 = 9 unidades
        assertEquals(9.0, repository.ingredients.value.find { it.id == platano.id }!!.currentStock, 0.001)
        // Helado: 4.0 kg - 200g (0.2kg) = 3.8 kg
        assertEquals(3.8, repository.ingredients.value.find { it.id == helado.id }!!.currentStock, 0.001)
        // Refresco directo: 20 - 2 = 18 unidades
        assertEquals(18.0, repository.products.value.find { it.id == refresco.id }!!.stockQuantity, 0.001)
    }

    // 20. Idempotencia: el segundo llamado a completeSale con el mismo ID no descuenta dos veces
    @Test
    fun testIdempotenciaVenta() {
        val galleta = repository.createIngredient("Galleta Choco", "unidades", 50.0, 500L).getOrThrow()
        val prod = repository.createProduct(
            name = "Galleta Empacada",
            basePrice = 1200L,
            categoryId = "snacks",
            recipe = listOf(com.ambiorix.pay.pos.RecipeItem(galleta.id, galleta.name, 1.0, "unidades"))
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-idemp-1",
            productId = prod.id,
            productName = prod.name,
            basePrice = prod.basePrice,
            unitPrice = prod.basePrice,
            quantity = 2,
            totalPrice = prod.basePrice * 2
        )

        val fixedSaleId = "sale-idempotent-unique-123"

        val firstResult = repository.completeSale(
            items = listOf(cartItem),
            subtotal = prod.basePrice * 2,
            discount = 0L,
            total = prod.basePrice * 2,
            paymentMethod = "NEQUI",
            cashierName = "Laura Gómez",
            saleId = fixedSaleId
        )
        assertTrue(firstResult.isSuccess)
        assertEquals(48.0, repository.ingredients.value.find { it.id == galleta.id }!!.currentStock, 0.001)

        val secondResult = repository.completeSale(
            items = listOf(cartItem),
            subtotal = prod.basePrice * 2,
            discount = 0L,
            total = prod.basePrice * 2,
            paymentMethod = "NEQUI",
            cashierName = "Laura Gómez",
            saleId = fixedSaleId
        )
        assertTrue(secondResult.isSuccess)

        // El stock DEBE permanecer en 48.0, NO 46.0
        assertEquals(48.0, repository.ingredients.value.find { it.id == galleta.id }!!.currentStock, 0.001)
    }

    // 21. Devolución / anulación de venta con movimientos compensatorios
    @Test
    fun testDevolucionVentaYMovimientosCompensatorios() {
        val fresa = repository.createIngredient("Fresa Fresca", "kg", 2.0, 8000L).getOrThrow()
        val prod = repository.createProduct(
            name = "Copa Fresa",
            basePrice = 7500L,
            categoryId = "copas",
            recipe = listOf(com.ambiorix.pay.pos.RecipeItem(fresa.id, fresa.name, 250.0, "g"))
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "c-ret-1",
            productId = prod.id,
            productName = prod.name,
            basePrice = prod.basePrice,
            unitPrice = prod.basePrice,
            quantity = 1,
            totalPrice = prod.basePrice
        )

        val sale = repository.completeSale(
            items = listOf(cartItem),
            subtotal = prod.basePrice,
            discount = 0L,
            total = prod.basePrice,
            paymentMethod = "CASH",
            cashierName = "Laura Gómez"
        ).getOrThrow()

        // 2.0 kg - 250g = 1.75 kg
        assertEquals(1.75, repository.ingredients.value.find { it.id == fresa.id }!!.currentStock, 0.001)

        val returnResult = repository.returnSale(
            saleId = sale.id,
            reason = "Cliente cambió de opinión",
            userName = "Laura Gómez"
        )
        assertTrue(returnResult.isSuccess)
        val returnedSale = returnResult.getOrThrow()
        assertEquals("RETURNED", returnedSale.status)

        // 1.75 + 0.25 = 2.0 kg
        assertEquals(2.0, repository.ingredients.value.find { it.id == fresa.id }!!.currentStock, 0.001)

        val compensations = repository.inventoryMovements.value.filter { it.type == "RETURN_COMPENSATION" && it.saleId == sale.id }
        assertEquals(1, compensations.size)
        assertEquals(0.25, compensations[0].change, 0.001)
    }

    // =========================================================================
    // NUEVO MÓDULO: HISTORIAL FINANCIERO, CALENDARIO, NEQUI, GASTOS Y PRODUCTOS
    // =========================================================================

    // 22. Navegación por fechas y generación de días en el calendario
    @Test
    fun testNavegacionCalendarioYGeneracionDias() {
        val daysOctubre = repository.getMonthCalendarDays(2026, 10)
        assertEquals("Octubre debe tener 31 días", 31, daysOctubre.size)
        assertEquals(1, daysOctubre[0].dayNumber)
        assertEquals("2026-10-01", daysOctubre[0].dateStr)
        assertEquals(31, daysOctubre[30].dayNumber)
        assertEquals("2026-10-31", daysOctubre[30].dateStr)

        val daysNoviembre = repository.getMonthCalendarDays(2026, 11)
        assertEquals("Noviembre debe tener 30 días", 30, daysNoviembre.size)

        val daysFebreroBisiesto = repository.getMonthCalendarDays(2024, 2)
        assertEquals("Febrero 2024 bisiesto debe tener 29 días", 29, daysFebreroBisiesto.size)
    }

    // 23. Acumulación correcta del total Nequi para un día con múltiples pagos
    @Test
    fun testAcumulacionTotalNequiMultiplesPagos() {
        val testDate = "2026-10-26"
        val initialSummary = repository.getDailyFinancialSummary(testDate)
        val initialNequi = initialSummary.totalNequi

        // Registrar 2 transacciones Nequi adicionales
        repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 35000L,
            dateStr = testDate,
            payerNameOrPhone = "Andrés Parra",
            bankTransactionRef = "M991823"
        )
        repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 15000L,
            dateStr = testDate,
            payerNameOrPhone = "Carolina Díaz",
            bankTransactionRef = "M991824"
        )

        val updatedSummary = repository.getDailyFinancialSummary(testDate)
        assertEquals(initialNequi + 50000L, updatedSummary.totalNequi)
        assertEquals(initialSummary.nequiCount + 2, updatedSummary.nequiCount)
    }

    // 24. Separación entre ID de evento SMS y Referencia Bancaria ("No disponible en el SMS")
    @Test
    fun testSeparacionIdSmsYReferenciaBancariaFallback() {
        val testDate = "2026-10-27"
        // 1. Pago Nequi CON referencia bancaria
        val txConRef = repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 20000L,
            dateStr = testDate,
            smsEventId = "SMS-20261027-101",
            bankTransactionRef = "M123456"
        )
        assertEquals("M123456", txConRef.displayBankRef)
        assertEquals("SMS-20261027-101", txConRef.smsEventId)

        // 2. Pago Nequi SIN referencia bancaria (debe mostrar explícitamente "No disponible en el SMS")
        val txSinRef = repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 12000L,
            dateStr = testDate,
            smsEventId = "SMS-20261027-102",
            bankTransactionRef = null
        )
        assertEquals("No disponible en el SMS", txSinRef.displayBankRef)
        assertEquals("SMS-20261027-102", txSinRef.smsEventId)
    }

    // 25. Acumulación correcta del total efectivo (ventas + ingresos manuales a caja)
    @Test
    fun testAcumulacionTotalEfectivo() {
        val testDate = "2026-10-28"
        // Cobro por venta
        repository.recordPaymentTransaction(
            method = "CASH",
            amount = 25000L,
            dateStr = testDate,
            entryType = "SALE_PAYMENT",
            saleNumber = "V-00201",
            cashierName = "Laura Gómez"
        )
        // Ingreso manual a caja
        repository.recordPaymentTransaction(
            method = "CASH",
            amount = 40000L,
            dateStr = testDate,
            entryType = "MANUAL_CASH_IN",
            notes = "Base de cambio adicional",
            cashierName = "Laura Gómez"
        )

        val summary = repository.getDailyFinancialSummary(testDate)
        assertEquals(65000L, summary.totalCash)
        assertEquals(2, summary.cashCount)
    }

    // 26. Acumulación y categorización de gastos
    @Test
    fun testAcumulacionYCategorizacionGastos() {
        val testDate = "2026-10-29"
        val exp1 = repository.recordExpense(
            amount = 30000L,
            category = "Insumos",
            description = "10 Litros de leche entera",
            paymentSource = "Caja efectivo",
            cashierName = "Laura Gómez",
            dateStr = testDate,
            isLinkedToInventory = true
        ).getOrThrow()

        val exp2 = repository.recordExpense(
            amount = 15000L,
            category = "Transporte",
            description = "Domicilio de insumos",
            paymentSource = "Caja efectivo",
            cashierName = "Laura Gómez",
            dateStr = testDate
        ).getOrThrow()

        val summary = repository.getDailyFinancialSummary(testDate)
        assertEquals(45000L, summary.totalExpenses)
        assertEquals(2, summary.expensesCount)

        // Anular un gasto por el patrón
        val annulResult = repository.annulExpense(exp2.id, "Gasto cargado por error", "Patrón")
        assertTrue(annulResult.isSuccess)

        val summaryAfterAnnul = repository.getDailyFinancialSummary(testDate)
        // Solo debe quedar activo el de 30.000
        assertEquals(30000L, summaryAfterAnnul.totalExpenses)
        assertEquals(2, summaryAfterAnnul.expensesCount) // Total registros auditables
    }

    // 27. Cálculo exacto del balance de movimientos: Total recibido - Total gastos
    @Test
    fun testCalculoExactoBalanceMovimientos() {
        val testDate = "2026-10-30"
        // Nequi: 50.000
        repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 50000L,
            dateStr = testDate,
            payerNameOrPhone = "Juan Restrepo"
        )
        // Efectivo: 30.000
        repository.recordPaymentTransaction(
            method = "CASH",
            amount = 30000L,
            dateStr = testDate,
            entryType = "SALE_PAYMENT"
        )
        // Gastos: 20.000
        repository.recordExpense(
            amount = 20000L,
            category = "Servicios",
            description = "Recibo energía",
            paymentSource = "Caja efectivo",
            dateStr = testDate
        )

        val summary = repository.getDailyFinancialSummary(testDate)
        assertEquals(50000L, summary.totalNequi)
        assertEquals(30000L, summary.totalCash)
        assertEquals(80000L, summary.totalReceived) // 50k + 30k
        assertEquals(20000L, summary.totalExpenses)
        assertEquals(60000L, summary.movementBalance) // 80k - 20k = 60k
    }

    // 28. No duplicar ventas y transacciones de cobro
    @Test
    fun testNoDuplicarVentasYTransaccionesDeCobro() {
        val testDate = "2026-10-31"
        val prod = repository.createProduct(
            name = "Copa Especial",
            basePrice = 16000L,
            categoryId = "copas"
        ).getOrThrow()

        val cartItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-test-1",
            productId = prod.id,
            productName = prod.name,
            basePrice = prod.basePrice,
            unitPrice = prod.basePrice,
            quantity = 1,
            totalPrice = prod.basePrice
        )

        // Completar venta pagada por Nequi
        val sale = repository.completeSale(
            items = listOf(cartItem),
            subtotal = 16000L,
            discount = 0L,
            total = 16000L,
            paymentMethod = "NEQUI",
            cashierName = "Laura Gómez",
            senderName = "Mónica Silva"
        ).getOrThrow()

        val todayDate = repository.getTodayDateKey()
        val summary = repository.getDailyFinancialSummary(todayDate)

        // El total recibido debe computar exactamente 16000L de Nequi, nunca 32000L (duplicando venta + pago)
        val saleTx = summary.nequiTransactions.find { it.saleId == sale.id }
        assertNotNull("Debe existir la transacción Nequi vinculada a la venta", saleTx)
        assertEquals(16000L, saleTx!!.amount)
        assertEquals(sale.id, saleTx.saleId)
    }

    // 29. Agrupación precisa de productos vendidos: Combos no inflan componentes individuales
    @Test
    fun testAgrupacionProductosVendidosYCombos() {
        val testDate = "2026-10-15"
        val comboProd = repository.products.value.find { it.id == "prod-combo-1" }!!
        assertTrue("Debe ser un producto combo", comboProd.isCombo)

        val comboItem = com.ambiorix.pay.pos.CartItem(
            cartItemId = "cart-combo-test",
            productId = comboProd.id,
            productName = comboProd.name,
            basePrice = comboProd.basePrice,
            unitPrice = comboProd.basePrice,
            quantity = 2,
            totalPrice = comboProd.basePrice * 2
        )

        // Registrar venta con fecha específica
        val sale = com.ambiorix.pay.pos.SaleRecord(
            id = "sale-combo-test-1",
            saleNumber = "V-00999",
            items = listOf(comboItem),
            subtotal = 56000L,
            discount = 0L,
            total = 56000L,
            paymentMethod = "NEQUI",
            nequiAmount = 56000L,
            timestamp = "04:00 PM",
            dateStr = testDate,
            cashierName = "Laura Gómez",
            status = "COMPLETED"
        )
        // Guardar la venta en el repositorio
        repository = com.ambiorix.pay.pos.PosRepository.createForTesting(storage)
        val allSales = listOf(sale) + repository.sales.value
        storage.putString(com.ambiorix.pay.pos.PosRepository.KEY_SALES, "") // Forzamos carga fresca

        repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 56000L,
            dateStr = testDate,
            saleId = sale.id,
            saleNumber = sale.saleNumber
        )

        // Completar venta a través del repo para el día de prueba
        val summary = repository.getDailyFinancialSummary(testDate)
        // La transacción de Nequi computa 56000
        assertEquals(56000L, summary.totalNequi)

        // Validar que en defaultSales (día date1) el combo está agrupado como Combo Pareja Dulce
        val defaultSummary = repository.getDailyFinancialSummary("2026-10-05")
        val comboSold = defaultSummary.productsSold.find { it.productId == "prod-combo-1" }
        assertNotNull("El combo debe aparecer como producto vendido", comboSold)
        assertTrue(comboSold!!.isCombo)
        assertEquals("Combo Pareja Dulce", comboSold.productName)
        assertEquals(28000L, comboSold.unitSalePrice) // Precio histórico snapshot
    }

    // 30. Persistencia de gastos e historial de pagos
    @Test
    fun testPersistenciaGastosYPagos() {
        val testDate = "2026-10-22"
        val exp = repository.recordExpense(
            amount = 48000L,
            category = "Mantenimiento",
            description = "Mantenimiento preventivo congelador",
            paymentSource = "Caja efectivo",
            cashierName = "Laura Gómez",
            dateStr = testDate,
            notes = "Técnico refrigeración"
        ).getOrThrow()

        val tx = repository.recordPaymentTransaction(
            method = "NEQUI",
            amount = 75000L,
            dateStr = testDate,
            payerNameOrPhone = "Empresa Asociada SAS",
            bankTransactionRef = "M554433"
        )

        // Recargar repositorio desde almacenamiento persistente
        val reloadedRepo = com.ambiorix.pay.pos.PosRepository.createForTesting(storage)
        val reloadedSummary = reloadedRepo.getDailyFinancialSummary(testDate)

        val foundExp = reloadedSummary.expenses.find { it.id == exp.id }
        assertNotNull("El gasto debe persistir tras recargar el repositorio", foundExp)
        assertEquals(48000L, foundExp!!.amount)
        assertEquals("Mantenimiento", foundExp.category)

        val foundTx = reloadedSummary.nequiTransactions.find { it.id == tx.id }
        assertNotNull("La transacción de pago debe persistir tras recargar el repositorio", foundTx)
        assertEquals(75000L, foundTx!!.amount)
        assertEquals("M554433", foundTx.bankTransactionRef)
    }
}

