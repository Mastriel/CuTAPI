package xyz.mastriel.cutapi.gui

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.inventory.*
import xyz.mastriel.cutapi.periodic.*
import java.util.*

public sealed interface GuiOpenResult {
    public data class Opened(public val session: GuiSession<*, *>) : GuiOpenResult
    public data object PlayerOffline : GuiOpenResult
    public data object Cancelled : GuiOpenResult
    public data class Unsupported(public val message: String) : GuiOpenResult
    public data class Failed(public val cause: Throwable) : GuiOpenResult
}

public class GuiManager {
    private val activeByPlayer: MutableMap<UUID, GuiSession<*, *>> = mutableMapOf()
    private val sessionsByDefinition: MutableMap<GuiDefinition<*, *>, MutableSet<GuiSession<*, *>>> =
        Collections.synchronizedMap(IdentityHashMap())
    private val knownDefinitions: MutableMap<GuiDefinition<*, *>, Unit> =
        Collections.synchronizedMap(WeakHashMap())

    public fun <V : InventoryView> open(
        player: Player,
        definition: GuiDefinition<Unit, V>,
    ): GuiOpenResult = openTyped(
        player,
        definition,
        Unit,
        GuiSubject.Standalone,
        null,
        emptyMap(),
    )

    public fun <C, V : InventoryView> open(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
    ): GuiOpenResult = openTyped(
        player,
        definition,
        context,
        GuiSubject.Standalone,
        null,
        emptyMap(),
    )

    public fun <V : InventoryView> open(
        player: Player,
        definition: GuiDefinition<Unit, V>,
        configure: GuiOpenBuilder<Unit>.() -> Unit,
    ): GuiOpenResult {
        val bindings = GuiOpenBuilder(definition).apply(configure).bindings.toMap()
        return openTyped(player, definition, Unit, GuiSubject.Standalone, null, bindings)
    }

    public fun <C, V : InventoryView> open(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
        configure: GuiOpenBuilder<C>.() -> Unit,
    ): GuiOpenResult {
        val bindings = GuiOpenBuilder(definition).apply(configure).bindings.toMap()
        return openTyped(player, definition, context, GuiSubject.Standalone, null, bindings)
    }

    public fun <V : InventoryView> attach(
        player: Player,
        definition: GuiDefinition<Unit, V>,
        view: InventoryView = player.openInventory,
    ): GuiOpenResult = attachTyped(player, definition, Unit, view)

    public fun <C, V : InventoryView> attach(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
        view: InventoryView = player.openInventory,
    ): GuiOpenResult = attachTyped(player, definition, context, view)

    private fun <C, V : InventoryView> attachTyped(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
        view: InventoryView,
    ): GuiOpenResult {
        GuiExecution.requirePrimaryThread()
        if (!player.isOnline) return GuiOpenResult.PlayerOffline
        if (player.openInventory !== view) {
            return GuiOpenResult.Unsupported("The supplied InventoryView is not the player's currently open view.")
        }
        if (definition.overlay != null) {
            return GuiOpenResult.Unsupported("An overlay GUI cannot attach to a view whose title it did not create.")
        }
        if (view.topInventory.size < definition.type.layout.topSize) {
            return GuiOpenResult.Unsupported(
                "The existing view has ${view.topInventory.size} top slots; ${definition.id} requires " +
                    "${definition.type.layout.topSize}.",
            )
        }
        val old = activeByPlayer[player.uniqueId]
        @Suppress("UNCHECKED_CAST")
        val typedView = view as V
        val session = GuiSession(definition, player, GuiSubject.Standalone, context, this)
        return try {
            session.installPorts(resolvePorts(player, definition, context, GuiSubject.Standalone, null, emptyMap()))
            session.initializeState()
            session.bindView(typedView)
            render(session, initial = true)
            old?.let { finishClose(it, GuiCloseReason.Replaced, closeView = false) }
            activeByPlayer[player.uniqueId] = session
            knownDefinitions[definition] = Unit
            sessionsByDefinition.getOrPut(definition) { identitySessionSet() }.add(session)
            session.status = GuiSessionStatus.Open
            executeHandlers(session, "open", null, definition.openHandlers) { GuiLifecycleContext(session) }
            GuiOpenResult.Opened(session)
        } catch (failure: Throwable) {
            discard(session)
            GuiOpenResult.Failed(failure)
        }
    }

    internal fun open(
        player: Player,
        tile: CuTPlacedTileEntity,
        presentation: BlockInventoryPresentation<*, out InventoryView>,
    ): GuiOpenResult {
        val inventory = tile.inventoryOrNull
            ?: return GuiOpenResult.Unsupported(
                "Tile ${tile.identity.customTile?.id ?: tile.identity} does not have a usable block inventory.",
            )
        return openBlockUntyped(player, tile, inventory, presentation)
    }

    @Suppress("UNCHECKED_CAST")
    private fun openBlockUntyped(
        player: Player,
        tile: CuTPlacedTileEntity,
        inventory: BlockInventory,
        presentation: BlockInventoryPresentation<*, out InventoryView>,
    ): GuiOpenResult {
        GuiExecution.requirePrimaryThread()
        if (!player.isOnline) return GuiOpenResult.PlayerOffline
        val typed = presentation as BlockInventoryPresentation<Any?, InventoryView>
        val subject = GuiSubject.Block(tile, inventory)
        val context = try {
            typed.contextProducer(BlockGuiOpenContext(player, tile, inventory))
        } catch (failure: Throwable) {
            return GuiOpenResult.Failed(failure)
        }
        return openTyped(player, typed.gui, context, subject, typed, emptyMap())
    }

    private fun <C, V : InventoryView> openTyped(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
        subject: GuiSubject,
        presentation: BlockInventoryPresentation<C, V>?,
        openBindings: Map<GuiSlotPort, GuiSlotBackingInitializationContext<C>.() -> GuiSlotBacking>,
    ): GuiOpenResult {
        GuiExecution.requirePrimaryThread()
        if (!player.isOnline) return GuiOpenResult.PlayerOffline
        val old = activeByPlayer[player.uniqueId]
        old?.status = GuiSessionStatus.Replacing
        val session = GuiSession(definition, player, subject, context, this)
        return try {
            session.installPorts(resolvePorts(player, definition, context, subject, presentation, openBindings))
            session.initializeState()
            val visibleTitle = execute(session, GuiExecutionPhase.Rendering) {
                definition.titleProducer(GuiRenderContext(session))
            }
            val title = definition.overlay?.let { GuiOverlayComposer.compose(definition.id, it, visibleTitle) }
                ?: visibleTitle
            val view = definition.type.create(
                GuiCreateContext(
                    player = player,
                    title = title,
                    location = (subject as? GuiSubject.Block)?.tile?.location,
                ),
            )
            session.bindView(view)
            render(session, initial = true)
            activeByPlayer[player.uniqueId] = session
            knownDefinitions[definition] = Unit
            sessionsByDefinition.getOrPut(definition) { identitySessionSet() }.add(session)
            player.openInventory(view)
            if (player.openInventory !== view) {
                discard(session)
                if (old != null && !old.isClosed) {
                    old.status = GuiSessionStatus.Open
                    activeByPlayer[player.uniqueId] = old
                }
                return GuiOpenResult.Cancelled
            }
            session.status = GuiSessionStatus.Open
            if (old != null) finishClose(old, GuiCloseReason.Replaced, closeView = false)
            executeHandlers(session, "open", null, definition.openHandlers) { GuiLifecycleContext(session) }
            GuiOpenResult.Opened(session)
        } catch (unsupported: UnsupportedOperationException) {
            discard(session)
            if (old != null && !old.isClosed) old.status = GuiSessionStatus.Open
            GuiOpenResult.Unsupported(unsupported.message ?: "The selected inventory view is unsupported.")
        } catch (failure: Throwable) {
            discard(session)
            if (old != null && !old.isClosed) old.status = GuiSessionStatus.Open
            GuiOpenResult.Failed(failure)
        }
    }

    internal fun <C, V : InventoryView> resolvePorts(
        player: Player,
        definition: GuiDefinition<C, V>,
        context: C,
        subject: GuiSubject,
        presentation: BlockInventoryPresentation<C, V>?,
        openBindings: Map<GuiSlotPort, GuiSlotBackingInitializationContext<C>.() -> GuiSlotBacking>,
    ): List<GuiResolvedPort> {
        val blockSubject = subject as? GuiSubject.Block
        val storageByPort = presentation?.bindings?.entries?.associate { (storageSlot, port) -> port to storageSlot }
            .orEmpty()
        return definition.elements.mapNotNull { element ->
            val port = element.port ?: return@mapNotNull null
            val initialization = GuiSlotBackingInitializationContext(player, subject, port, context)
            val storageSlot = storageByPort[port]
            val blockRole = storageSlot?.let { blockSubject?.inventory?.definition?.slot(it)?.access }
            val backing = when {
                storageSlot != null && blockSubject != null -> blockSubject.inventory.guiSlotBacking(storageSlot)
                port in openBindings -> openBindings.getValue(port).invoke(initialization)
                element.sessionBackingInitializer != null -> MutableGuiSlotBacking {
                    element.sessionBackingInitializer.invoke(initialization)
                }
                else -> null
            }
            val defaultInsertion = blockRole?.let { it != BlockInventorySlotAccess.Output } ?: true
            val defaultExtraction = true
            val access = element.boundSlotAccess ?: GuiBoundSlotAccess.Default
            GuiResolvedPort(
                port = port,
                slot = element.slot,
                rawSlot = definition.type.layout.toRaw(element.slot),
                backing = backing,
                permitsPlayerInsertion = access.permitsInsertionOverride ?: defaultInsertion,
                permitsPlayerExtraction = access.permitsExtractionOverride ?: defaultExtraction,
                storageSlot = storageSlot,
            )
        }
    }

    public fun activeSession(player: Player): GuiSession<*, *>? = activeByPlayer[player.uniqueId]

    @Periodic(1)
    internal fun tickGuis() {
        GuiExecution.requirePrimaryThread()
        activeByPlayer.values.toList().forEach { session ->
            if (
                !session.isClosed &&
                session.status == GuiSessionStatus.Open &&
                activeByPlayer[session.viewer.uniqueId] === session &&
                runCatching { session.viewer.openInventory === session.view }.getOrDefault(false)
            ) {
                invokeOnTickUntyped(session)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeOnTickUntyped(session: GuiSession<*, *>) {
        val typed = session as GuiSession<Any?, InventoryView>
        executeHandlers(typed, "while-open", null, typed.definition.whileOpenHandlers) {
            GuiLifecycleContext(typed)
        }
    }

    internal fun refreshBlock(tile: CuTPlacedTileEntity) {
        activeByPlayer.values.filter { session ->
            val subject = session.subject as? GuiSubject.Block
            subject?.tile?.location == tile.location
        }.toList().forEach(::invalidate)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun acceptsPort(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        item: ItemStack,
    ): Boolean {
        val typed = session as GuiSession<Any?, InventoryView>
        val element = typed.definition.element(resolved.port) ?: return false
        val predicate = element.portAcceptance ?: return true
        return execute(typed, GuiExecutionPhase.Handling) {
            predicate(GuiPortAcceptanceContext(typed, resolved.port, resolved.slot), item.clone())
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun handlePortBackingUpdate(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        update: GuiSlotBackingUpdate,
    ) {
        GuiExecution.requirePrimaryThread()
        if (session.isClosed) return
        val typed = session as GuiSession<Any?, InventoryView>
        val element = typed.definition.element(resolved.port) ?: return
        invalidate(typed)
        val backing = resolved.backing ?: return
        executeHandlers(typed, "bound-slot-update", element.slot, element.slotUpdateHandlers) {
            GuiBoundSlotUpdateContext(
                session = typed,
                backing = backing,
                port = resolved.port,
                storageSlot = resolved.storageSlot,
                guiSlot = element.slot,
                previous = update.previous,
                item = update.item,
                source = update.source,
            )
        }
    }

    internal fun closeForBlock(tile: CuTPlacedTileEntity, reason: GuiCloseReason) {
        activeByPlayer.values.filter { session ->
            val subject = session.subject as? GuiSubject.Block
            subject?.tile?.location == tile.location
        }.toList().forEach { finishClose(it, reason, closeView = true) }
    }

    public fun invalidate(definition: GuiDefinition<*, *>) {
        GuiExecution.requirePrimaryThread()
        sessionsByDefinition[definition]?.toList()?.forEach(::invalidate)
    }

    internal fun invalidate(session: GuiSession<*, *>, slot: GuiSlot? = null) {
        GuiExecution.requirePrimaryThread()
        if (session.isClosed) return
        if (slot == null) {
            session.fullInvalidation = true
            session.pendingSlots.clear()
        } else if (!session.fullInvalidation) {
            session.pendingSlots += slot
        }
        if (session.renderScheduled) return
        session.renderScheduled = true
        runNextTick {
            session.renderScheduled = false
            if (!session.isClosed && activeByPlayer[session.viewer.uniqueId] === session) renderUntyped(session)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderUntyped(session: GuiSession<*, *>) {
        render(session as GuiSession<Any?, InventoryView>, initial = false)
    }

    private fun <C, V : InventoryView> render(session: GuiSession<C, V>, initial: Boolean) {
        if (session.rendering) {
            invalidate(session)
            return
        }
        if (++session.renderLoopPasses > 8) {
            handleFailure(
                session,
                "render-loop",
                null,
                IllegalStateException("GUI ${session.definition.id} continuously invalidated itself.")
            )
            return
        }
        session.rendering = true
        val full = initial || session.fullInvalidation || session.pendingSlots.isEmpty()
        val selected = session.pendingSlots.toSet()
        session.fullInvalidation = false
        session.pendingSlots.clear()
        try {
            projectPorts(session)
            for (element in session.definition.elements) {
                val raw = session.definition.type.layout.toRaw(element.slot)
                if (element.port != null) continue
                if (!full && element.slot !in selected) continue
                val produced = execute(session, GuiExecutionPhase.Rendering) {
                    val context = GuiRenderContext(session)
                    if (!element.visible(context)) null else element.item?.invoke(context)
                }
                writeRendered(session, raw, produced)
            }
            session.renderCount += 1
        } finally {
            session.rendering = false
            if (!session.renderScheduled) session.renderLoopPasses = 0
        }
    }

    private fun <C, V : InventoryView> projectPorts(session: GuiSession<C, V>) {
        for (resolved in session.resolvedPorts()) {
            val storedItem = resolved.backing?.item
            val renderedItem = if (storedItem != null) {
                session.placeholderRawSlots.remove(resolved.rawSlot)
                storedItem
            } else {
                val placeholder = session.definition.element(resolved.port)?.let { element ->
                    execute(session, GuiExecutionPhase.Rendering) {
                        val context = GuiRenderContext(session)
                        if (!element.visible(context)) null else element.item?.invoke(context)
                    }
                }?.takeUnless { it.type.isAir }
                if (placeholder != null) session.placeholderRawSlots += resolved.rawSlot
                else session.placeholderRawSlots -= resolved.rawSlot
                placeholder
            }
            writeRendered(session, resolved.rawSlot, renderedItem)
        }
    }

    private fun writeRendered(session: GuiSession<*, *>, raw: Int, item: ItemStack?) {
        val next = item?.clone()
        val previous = session.renderedItems[raw]
        if (sameStack(previous, next)) return
        session.view.setItem(raw, next?.clone())
        session.renderedItems[raw] = next?.clone()
    }

    private fun sameStack(first: ItemStack?, second: ItemStack?): Boolean {
        if (first == null || first.type.isAir) return second == null || second.type.isAir
        if (second == null || second.type.isAir) return false
        return first.amount == second.amount && first.isSimilar(second)
    }

    internal fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessionFor(player, event.view) ?: return
        handleClickUntyped(session, event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleClickUntyped(session: GuiSession<*, *>, event: InventoryClickEvent) {
        handleClickTyped(session as GuiSession<Any?, InventoryView>, event)
    }

    private fun <C, V : InventoryView> handleClickTyped(session: GuiSession<C, V>, event: InventoryClickEvent) {
        val definition = session.definition
        val eventSlot = definition.type.layout.fromRaw(event.rawSlot)
        val element = definition.elements.firstOrNull {
            definition.type.layout.toRaw(it.slot) == event.rawSlot
        }
        val logical = element?.slot ?: eventSlot
        val resolvedPort = session.resolvedPortAtRaw(event.rawSlot)
        val blockSubject = session.subject as? GuiSubject.Block

        if (
            event.isShiftClick &&
            event.rawSlot >= definition.type.layout.topSize &&
            session.resolvedPorts().any { it.backing != null && it.permitsPlayerInsertion }
        ) {
            handlePortShiftInsert(session, event)
            return
        }
        if (event.isShiftClick && event.rawSlot >= definition.type.layout.topSize) {
            val topElements = definition.elements.filter { it.slot is GuiSlot.Top }
            val source = event.currentItem?.takeUnless { it.type.isAir }
            val allTopSlotsAreInputs = topElements.size == definition.type.layout.topSize &&
                topElements.all { it.input != null }
            val accepted = source != null && allTopSlotsAreInputs && topElements.all { inputElement ->
                execute(session, GuiExecutionPhase.Handling) {
                    inputElement.input!!.accepts(
                        GuiInputAcceptanceContext(session, inputElement.slot),
                        source.clone(),
                    )
                }
            }
            if (accepted) {
                val previous = topElements.associateWith { inputElement ->
                    session.view.getItem(definition.type.layout.toRaw(inputElement.slot))?.clone()
                }
                event.isCancelled = false
                runNextTick {
                    if (session.isClosed) return@runNextTick
                    topElements.forEach { inputElement ->
                        val raw = definition.type.layout.toRaw(inputElement.slot)
                        executeHandlers(
                            session,
                            "input-shift-change",
                            inputElement.slot,
                            inputElement.input!!.onChange
                        ) {
                            GuiInputChangeContext(
                                session,
                                inputElement.slot,
                                previous[inputElement],
                                session.view.getItem(raw),
                                event
                            )
                        }
                    }
                }
                return
            }
        }

        if (
            blockSubject != null &&
            event.rawSlot in 0 until definition.type.layout.topSize &&
            resolvedPort == null &&
            element == null
        ) {
            // Blank presentation slots are not canonical storage. Allowing an item into one would
            // make it disappear when the per-viewer presentation is closed.
            event.isCancelled = true
            return
        }

        when (definition.defaultInteraction) {
            GuiInteractionPolicy.CancelAll -> event.isCancelled = true
            GuiInteractionPolicy.Allow -> Unit
            GuiInteractionPolicy.CancelManagedSlots -> {
                if (element != null || resolvedPort != null || event.isShiftClick && definition.managedSlots.size > 0 ||
                    event.click.isCreativeAction || event.click.name == "DOUBLE_CLICK"
                ) event.isCancelled = true
            }
        }

        if (resolvedPort != null) {
            handlePortClick(session, resolvedPort, event)
            return
        }

        if (element?.input != null) {
            val cursor = event.cursor.takeUnless { it.type.isAir }
            val accepted = cursor == null || execute(session, GuiExecutionPhase.Handling) {
                element.input.accepts(GuiInputAcceptanceContext(session, logical), cursor.clone())
            }
            if (accepted) {
                val previous = event.currentItem?.clone()
                event.isCancelled = false
                runNextTick {
                    if (session.isClosed) return@runNextTick
                    val resulting = session.view.getItem(event.rawSlot)?.clone()
                    executeHandlers(session, "input-change", logical, element.input.onChange) {
                        GuiInputChangeContext(session, logical, previous, resulting, event)
                    }
                }
            } else {
                event.isCancelled = true
            }
        }

        val context = GuiInteractionContext(
            session = session,
            slot = logical,
            rawSlot = event.rawSlot,
            clickedInventory = event.clickedInventory,
            currentItem = event.currentItem?.clone(),
            cursor = event.cursor.clone(),
            clickType = event.click,
            action = event.action,
            event = event,
        )
        executeHandlers(session, "click", logical, element?.clickHandlers.orEmpty()) { context }
    }

    private fun handlePortShiftInsert(session: GuiSession<*, *>, event: InventoryClickEvent) {
        event.isCancelled = true
        val source = event.currentItem?.takeUnless { it.type.isAir }?.clone() ?: return
        var remainder = source
        try {
            for (resolved in session.resolvedPorts()) {
                if (remainder.amount <= 0) break
                val backing = resolved.backing ?: continue
                if (!resolved.permitsPlayerInsertion || !acceptsPort(session, resolved, remainder)) continue
                remainder = backing.tryInsert(remainder, GuiSlotUpdateSource.Player)
            }
            event.currentItem = remainder.takeUnless { it.type.isAir || it.amount <= 0 }
        } catch (rejected: IllegalArgumentException) {
            event.currentItem = remainder
        } catch (failure: Throwable) {
            handleFailure(session, "bound-slot-shift-insert", null, failure)
        }
    }

    private fun handlePortClick(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        event: InventoryClickEvent,
    ) {
        event.isCancelled = true
        val backing = resolved.backing ?: return
        try {
            when (event.click) {
                ClickType.LEFT -> handlePortCursorClick(session, resolved, backing, event, single = false)
                ClickType.RIGHT -> handlePortCursorClick(session, resolved, backing, event, single = true)
                ClickType.SHIFT_LEFT,
                ClickType.SHIFT_RIGHT,
                -> movePortToPlayer(session, resolved, backing)
                ClickType.NUMBER_KEY -> {
                    val hotbarSlot = event.hotbarButton.takeIf { it in 0..8 } ?: return
                    swapPortWithPlayerSlot(
                        session,
                        resolved,
                        backing,
                        session.viewer.inventory.getItem(hotbarSlot),
                    ) { item -> session.viewer.inventory.setItem(hotbarSlot, item) }
                }
                ClickType.SWAP_OFFHAND -> swapPortWithPlayerSlot(
                    session,
                    resolved,
                    backing,
                    session.viewer.inventory.itemInOffHand,
                ) { item -> session.viewer.inventory.setItemInOffHand(item ?: ItemStack(Material.AIR)) }
                ClickType.DROP -> dropFromPort(session, resolved, backing, 1)
                ClickType.CONTROL_DROP -> dropFromPort(session, resolved, backing, Int.MAX_VALUE)
                else -> Unit
            }
        } catch (_: IllegalArgumentException) {
            // A backing may reject an item more narrowly than the presentation predicate.
        } catch (failure: Throwable) {
            handleFailure(session, "bound-slot-click", resolved.slot, failure)
        }
    }

    private fun handlePortCursorClick(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        backing: GuiSlotBacking,
        event: InventoryClickEvent,
        single: Boolean,
    ) {
        val current = backing.item?.takeUnless { it.type.isAir }
        val cursor = event.cursor.takeUnless { it.type.isAir }?.clone()
        if (cursor == null) {
            if (current == null || !resolved.permitsPlayerExtraction) return
            val amount = if (single) (current.amount + 1) / 2 else current.amount
            setEventCursor(event, backing.tryExtract(amount, GuiSlotUpdateSource.Player))
            return
        }

        if (!resolved.permitsPlayerInsertion || !acceptsPort(session, resolved, cursor)) return
        if (current != null && !current.isSimilar(cursor)) {
            if (single || !resolved.permitsPlayerExtraction) return
            backing.setItem(cursor, GuiSlotUpdateSource.Player)
            setEventCursor(event, current)
            return
        }

        val offered = cursor.clone().also { if (single) it.amount = 1 }
        val remainder = backing.tryInsert(offered, GuiSlotUpdateSource.Player)
        val moved = offered.amount - remainder.amount
        if (moved <= 0) return
        setEventCursor(
            event,
            cursor.clone().also { it.amount -= moved }.takeIf { it.amount > 0 },
        )
    }

    private fun movePortToPlayer(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        backing: GuiSlotBacking,
    ) {
        if (!resolved.permitsPlayerExtraction) return
        val current = backing.item ?: return
        val capacity = session.viewer.inventory.storageContents.sumOf { stack ->
            when {
                stack == null || stack.type.isAir -> current.maxStackSize
                stack.isSimilar(current) -> (minOf(stack.maxStackSize, current.maxStackSize) - stack.amount).coerceAtLeast(0)
                else -> 0
            }
        }
        if (capacity <= 0) return
        val extracted = backing.tryExtract(minOf(capacity, current.amount), GuiSlotUpdateSource.Player) ?: return
        val remainders = session.viewer.inventory.addItem(extracted)
        remainders.values.forEach { remainder ->
            backing.tryInsert(remainder, GuiSlotUpdateSource.Player)
        }
    }

    private fun swapPortWithPlayerSlot(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        backing: GuiSlotBacking,
        playerItem: ItemStack?,
        writePlayerItem: (ItemStack?) -> Unit,
    ) {
        val current = backing.item?.takeUnless { it.type.isAir }
        val offered = playerItem?.takeUnless { it.type.isAir }?.clone()
        if (current != null && !resolved.permitsPlayerExtraction) return
        if (offered != null && (!resolved.permitsPlayerInsertion || !acceptsPort(session, resolved, offered))) return
        backing.setItem(offered, GuiSlotUpdateSource.Player)
        writePlayerItem(current?.clone())
    }

    private fun dropFromPort(
        session: GuiSession<*, *>,
        resolved: GuiResolvedPort,
        backing: GuiSlotBacking,
        amount: Int,
    ) {
        if (!resolved.permitsPlayerExtraction) return
        val extracted = backing.tryExtract(amount, GuiSlotUpdateSource.Player) ?: return
        session.viewer.world.dropItemNaturally(session.viewer.location, extracted)
    }

    @Suppress("DEPRECATION")
    private fun setEventCursor(event: InventoryClickEvent, item: ItemStack?) {
        event.setCursor(item)
    }

    internal fun handleDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessionFor(player, event.view) ?: return
        handleDragUntyped(session, event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleDragUntyped(session: GuiSession<*, *>, event: InventoryDragEvent) {
        handleDragTyped(session as GuiSession<Any?, InventoryView>, event)
    }

    private fun <C, V : InventoryView> handleDragTyped(session: GuiSession<C, V>, event: InventoryDragEvent) {
        val layout = session.definition.type.layout
        val block = session.subject as? GuiSubject.Block
        val elementsByRaw = session.definition.elements.associateBy {
            layout.toRaw(it.slot)
        }
        val affectedPorts = session.resolvedPorts().filter { it.rawSlot in event.rawSlots }
        if (affectedPorts.isNotEmpty()) {
            // Port contents are canonical backing data, not mutable contents of the presentation view.
            // Cancelling avoids letting one vanilla drag transaction partially mutate several backings.
            event.isCancelled = true
            return
        }
        if (block != null) {
            val affectedTop = event.rawSlots.filter { it in 0 until layout.topSize }
            val invalidTopSlot = affectedTop.any { raw ->
                elementsByRaw[raw]?.input == null
            }
            if (invalidTopSlot) {
                event.isCancelled = true
                return
            }
        }

        val affected = event.rawSlots.mapNotNull { raw -> elementsByRaw[raw]?.let { raw to it } }
        if (affected.isEmpty()) return
        val inputsOnly = affected.all { (_, element) -> element.input != null }
        val accepted = inputsOnly && affected.all { (raw, element) ->
            val item = event.newItems[raw] ?: return@all true
            execute(session, GuiExecutionPhase.Handling) {
                element.input!!.accepts(
                    GuiInputAcceptanceContext(session, element.slot),
                    item.clone(),
                )
            }
        }
        if (!accepted) {
            event.isCancelled = true
            return
        }
        val previous = affected.associate { (raw, _) -> raw to session.view.getItem(raw)?.clone() }
        event.isCancelled = false
        runNextTick {
            if (session.isClosed) return@runNextTick
            affected.forEach { (raw, element) ->
                executeHandlers(session, "input-drag-change", element.slot, element.input!!.onChange) {
                    GuiInputChangeContext(session, element.slot, previous[raw], session.view.getItem(raw), null)
                }
            }
        }
    }

    internal fun handleNaturalClose(player: Player, view: InventoryView) {
        val session = sessionFor(player, view) ?: return
        if (session.status == GuiSessionStatus.Replacing) return
        finishClose(session, GuiCloseReason.Player, closeView = false)
    }

    internal fun handleInventoryEvent(event: InventoryEvent) {
        val player = event.view.player as? Player ?: return
        val session = sessionFor(player, event.view) ?: return
        handleInventoryEventUntyped(session, event)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleInventoryEventUntyped(session: GuiSession<*, *>, event: InventoryEvent) {
        val typed = session as GuiSession<Any?, InventoryView>
        for (declaration in typed.definition.inventoryEventHandlers) {
            if (!declaration.eventType.isInstance(event)) continue
            try {
                val handler = declaration as GuiInventoryEventHandler<Any?, InventoryView, InventoryEvent>
                execute(typed, GuiExecutionPhase.Handling) {
                    handler.handler(GuiInventoryEventContext(typed, event))
                }
            } catch (failure: Throwable) {
                handleFailure(typed, event::class.simpleName ?: "inventory-event", null, failure)
                return
            }
        }
    }

    internal fun close(session: GuiSession<*, *>, reason: GuiCloseReason) {
        GuiExecution.requirePrimaryThread()
        finishClose(session, reason, closeView = true)
    }

    private fun finishClose(session: GuiSession<*, *>, reason: GuiCloseReason, closeView: Boolean) {
        if (session.isClosed) return
        (session.subject as? GuiSubject.Block)?.inventory?.flushPending()
        activeByPlayer.remove(session.viewer.uniqueId, session)
        sessionsByDefinition[session.definition]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) sessionsByDefinition.remove(session.definition)
        }
        invokeCloseUntyped(session, reason)
        session.markClosed(reason)
        if (closeView && session.viewer.isOnline && session.viewer.openInventory === session.view) {
            session.viewer.closeInventory()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeCloseUntyped(session: GuiSession<*, *>, reason: GuiCloseReason) {
        val typed = session as GuiSession<Any?, InventoryView>
        executeHandlers(typed, "close", null, typed.definition.closeHandlers) { GuiCloseContext(typed, reason) }
    }

    private fun discard(session: GuiSession<*, *>) {
        activeByPlayer.remove(session.viewer.uniqueId, session)
        sessionsByDefinition[session.definition]?.remove(session)
        session.markClosed(GuiCloseReason.ViewInvalidated)
    }

    internal fun handleFailure(session: GuiSession<*, *>, callback: String, slot: GuiSlot?, failure: Throwable) {
        if (isPluginInitialized() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(Plugin, Runnable { handleFailure(session, callback, slot, failure) })
            return
        }
        session.lastHandlerFailure = failure
        if (isPluginInitialized()) {
            Plugin.logger.severe(
                "GUI handler failure: gui=${session.definition.id}, session=${session.id}, " +
                    "player=${session.viewer.name}/${session.viewer.uniqueId}, subject=${session.subject}, " +
                    "callback=$callback, slot=$slot: ${failure.stackTraceToString()}",
            )
        }
        if (!session.isClosed) finishClose(session, GuiCloseReason.HandlerFailure, closeView = true)
    }

    private fun <GC, V : InventoryView, Callback : GuiContext<GC, V>> executeHandlers(
        session: GuiSession<GC, V>,
        callback: String,
        slot: GuiSlot?,
        handlers: List<Callback.() -> Unit>,
        context: () -> Callback,
    ) {
        for (handler in handlers) {
            try {
                execute(session, if (callback == "close") GuiExecutionPhase.Closing else GuiExecutionPhase.Handling) {
                    handler(context())
                }
            } catch (failure: Throwable) {
                handleFailure(session, callback, slot, failure)
                return
            }
        }
    }

    private inline fun <C, V : InventoryView, T> execute(
        session: GuiSession<C, V>,
        phase: GuiExecutionPhase,
        action: () -> T,
    ): T = GuiExecution.with(GuiExecutionContext(session, phase), action)

    private fun sessionFor(player: Player, view: InventoryView): GuiSession<*, *>? {
        val session = activeByPlayer[player.uniqueId] ?: return null
        return session.takeIf { !it.isClosed && runCatching { it.view === view }.getOrDefault(false) }
    }

    internal fun runNextTick(action: () -> Unit) {
        if (isPluginInitialized() && Plugin.isEnabled) {
            Bukkit.getScheduler().runTask(Plugin, Runnable(action))
        } else {
            action()
        }
    }

    public fun closeForPlugin(namespace: String) {
        activeByPlayer.values.filter { it.definition.id.namespace == namespace }.toList().forEach {
            finishClose(it, GuiCloseReason.PluginDisabled, closeView = true)
        }
        knownDefinitions.keys.filter { it.id.namespace == namespace }.forEach { it.clearSharedState() }
        GuiOverlayCatalog.removeNamespace(namespace)
    }

    public fun shutdown() {
        activeByPlayer.values.toList().forEach {
            finishClose(it, GuiCloseReason.PluginDisabled, closeView = true)
        }
    }

    private fun identitySessionSet(): MutableSet<GuiSession<*, *>> =
        Collections.newSetFromMap(IdentityHashMap())
}

public fun <V : InventoryView> Player.openGui(definition: GuiDefinition<Unit, V>): GuiOpenResult =
    xyz.mastriel.cutapi.CuTAPI.guiManager.open(this, definition)

public fun <C, V : InventoryView> Player.openGui(
    definition: GuiDefinition<C, V>,
    context: C,
): GuiOpenResult = xyz.mastriel.cutapi.CuTAPI.guiManager.open(this, definition, context)

public fun <V : InventoryView> Player.openGui(
    definition: GuiDefinition<Unit, V>,
    configure: GuiOpenBuilder<Unit>.() -> Unit,
): GuiOpenResult = xyz.mastriel.cutapi.CuTAPI.guiManager.open(this, definition, configure)

public fun <C, V : InventoryView> Player.openGui(
    definition: GuiDefinition<C, V>,
    context: C,
    configure: GuiOpenBuilder<C>.() -> Unit,
): GuiOpenResult = xyz.mastriel.cutapi.CuTAPI.guiManager.open(this, definition, context, configure)
