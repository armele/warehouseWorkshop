package com.deathfrog.warehouseworkshop.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopPlayerSettings;
import com.deathfrog.warehouseworkshop.core.compatibility.recipes.OptionalRecipeSupport;
import com.deathfrog.warehouseworkshop.core.compatibility.recipes.OptionalRecipeSupport.CraftingSlotRequirement;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Performs a validated warehouse-backed craft on the server.
 */
public record WorkshopCraftMessage(BlockPos buildingPos, List<ItemStack> grid, int craftCount, int requestedOutputCount, int craftType, ResourceLocation recipeId) implements IServerboundPayload
{
    public static final int CRAFT_TYPE_CRAFTING = 0;
    public static final int CRAFT_TYPE_DOMUM = 1;

    @SuppressWarnings("null")
    public static final Type<WorkshopCraftMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_craft"));
    
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkshopCraftMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        WorkshopCraftMessage::buildingPos,
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        WorkshopCraftMessage::grid,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::craftCount,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::requestedOutputCount,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::craftType,
        ResourceLocation.STREAM_CODEC,
        WorkshopCraftMessage::recipeId,
        WorkshopCraftMessage::new);

    /**
     * Returns the registered payload type for this message.
     *
     * @return the workshop craft payload type
     */
    @Override
    public Type<WorkshopCraftMessage> type()
    {
        return ID;
    }

    /**
     * Schedules this craft request for execution on the server thread.
     *
     * @param context the network payload context
     */
    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    /**
     * Executes a validated workshop craft on the server.
     * @param player The player performing the craft
     * @param building The building that the player is currently viewing
     * @param module The workshop module that is handling the craft
     * @param normalizedGrid The normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
    private void execute(final Player player)
    {
        if (player == null || grid.size() != 9 || craftCount <= 0)
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
        final WorkshopModule module = building == null ? null : building.getModule(WorkshopModule.class, candidate -> true);
        if (building == null || module == null)
        {
            return;
        }

        final List<ItemStack> normalizedGrid = new ArrayList<>(9);
        for (final ItemStack stack : grid)
        {
            if (stack == null || stack.isEmpty())
            {
                normalizedGrid.add(ItemStack.EMPTY);
                continue;
            }

            final ItemStack single = stack.copy();
            single.setCount(1);
            normalizedGrid.add(single);
        }

        if (craftType == CRAFT_TYPE_DOMUM)
        {
            executeDomumCraft(player, building, module, normalizedGrid);
            return;
        }

        if (craftType != CRAFT_TYPE_CRAFTING)
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        executeCraftingGridCraft(player, building, module, normalizedGrid);
    }

    /**
     * Executes a validated grid-based craft on the server.
     * @param player the player performing the craft
     * @param building the building that the player is currently viewing
     * @param module the workshop module that is handling the craft
     * @param normalizedGrid the normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
    private void executeCraftingGridCraft(
        final Player player,
        final IBuilding building,
        final WorkshopModule module,
        final List<ItemStack> normalizedGrid)
    {
        final CraftingInput input = CraftingInput.of(3, 3, normalizedGrid);
        final Optional<RecipeHolder<?>> recipe = player.level().getRecipeManager().byKey(recipeId);
        if (recipe.isEmpty()
            || !recipe.get().id().equals(recipeId)
            || !(recipe.get().value() instanceof final CraftingRecipe craftingRecipe)
            || !craftingRecipe.matches(input, player.level()))
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<CraftingIngredientRequirement> requiredIngredients = buildCraftingIngredientRequirements(craftingRecipe, normalizedGrid);
        final WorkshopPlayerSettings settings = WorkshopPlayerSettings.get(player, buildingPos);
        final boolean includePlayerInventory = settings.includePlayerInventory();
        final IItemHandler warehouseInventory = building.getItemHandlerCap();
        final IItemHandler playerInventory = new PlayerMainInvWrapper(player.getInventory());
        if (!hasCraftingIngredientRequirements(requiredIngredients, warehouseInventory, playerInventory, includePlayerInventory))
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        final ItemStack craftedResult = craftingRecipe.assemble(input, player.level().registryAccess()).copy();
        final List<ItemStack> remainingItems = craftingRecipe.getRemainingItems(input);
        final OutputTarget outputTarget = settings.outputTarget();
        int crafted = 0;

        for (int i = 0; i < craftCount; i++)
        {
            if (!hasCraftingIngredientRequirements(requiredIngredients, warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            final List<ExtractedCraftingIngredient> extractedIngredients = removeCraftingIngredientRequirements(
                requiredIngredients,
                warehouseInventory,
                playerInventory,
                includePlayerInventory);
            if (extractedIngredients == null)
            {
                break;
            }

            for (final ExtractedCraftingIngredient extracted : extractedIngredients)
            {
                final ItemStack remainder = remainingItems.get(extracted.gridSlot());
                if (!remainder.isEmpty() && remainder.is(extracted.stack().getItem()))
                {
                    giveCraftingRemainder(player, warehouseInventory, remainder.copy(), extracted.origin());
                }
            }

            giveCraftingOutput(player, warehouseInventory, craftedResult.copy(), outputTarget);
            for (int slot = 0; slot < remainingItems.size(); slot++)
            {
                final int currentSlot = slot;
                final ItemStack remainder = remainingItems.get(slot);
                if (!remainder.isEmpty() && extractedIngredients.stream().noneMatch(extracted ->
                    extracted.gridSlot() == currentSlot && remainder.is(extracted.stack().getItem())))
                {
                    giveCraftingOutput(player, warehouseInventory, remainder.copy(), outputTarget);
                }
            }
            crafted++;
        }

        if (crafted <= 0)
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        player.displayClientMessage(getCraftedStatusText(crafted, craftedResult), true);
        sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
    }

    /**
     * Executes a validated Domum craft on the server.
     * @param player The player performing the craft
     * @param building The building that the player is currently viewing
     * @param module The workshop module that is handling the craft
     * @param normalizedGrid The normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
    private void executeDomumCraft(
        final Player player,
        final IBuilding building,
        final WorkshopModule module,
        final List<ItemStack> normalizedGrid)
    {
        final Optional<RecipeHolder<ArchitectsCutterRecipe>> recipe = player.level().getRecipeManager()
            .getAllRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get())
            .stream()
            .filter(candidate -> candidate.id().equals(recipeId))
            .findFirst();

        if (recipe.isEmpty())
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<IMateriallyTexturedBlockComponent> components = getDomumComponents(recipe.get().value());
        if (components.isEmpty() || components.size() > 2)
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<ItemStack> baseIngredients = getDomumIngredients(normalizedGrid, components.size());
        final ArchitectsCutterRecipeInput input = buildDomumInput(baseIngredients);
        if (!recipe.get().value().matches(input, player.level()))
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final ItemStack craftedResult = recipe.get().value().assemble(input, player.level().registryAccess()).copy();
        if (craftedResult.isEmpty())
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final WorkshopPlayerSettings settings = WorkshopPlayerSettings.get(player, buildingPos);
        final boolean includePlayerInventory = settings.includePlayerInventory();
        final IItemHandler warehouseInventory = building.getItemHandlerCap();
        final IItemHandler playerInventory = new PlayerMainInvWrapper(player.getInventory());
        if (!hasCraftingIngredients(copyIngredients(baseIngredients), warehouseInventory, playerInventory, includePlayerInventory))
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        final OutputTarget outputTarget = settings.outputTarget();
        int crafted = 0;
        for (int i = 0; i < craftCount; i++)
        {
            final List<ItemStack> ingredients = copyIngredients(baseIngredients);
            if (!hasCraftingIngredients(copyIngredients(ingredients), warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            if (!removeIngredients(ingredients, warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            giveCraftingOutput(player, warehouseInventory, craftedResult.copy(), outputTarget);
            crafted++;
        }

        if (crafted <= 0)
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        player.displayClientMessage(getCraftedStatusText(crafted, craftedResult), true);
        sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
    }

    /**
     * Builds the localized completion message for a successful crafting operation.
     *
     * @param crafted the number of recipe executions completed
     * @param craftedResult the result produced by one recipe execution
     * @return the completion status message
     */
    private Component getCraftedStatusText(final int crafted, final ItemStack craftedResult)
    {
        final int producedCount = crafted * Math.max(1, craftedResult.getCount());
        if (requestedOutputCount <= 0)
        {
            return Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted", crafted);
        }

        if (producedCount >= requestedOutputCount)
        {
            return Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted.request_full", crafted, requestedOutputCount);
        }

        return Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted.request_partial", crafted, producedCount, requestedOutputCount);
    }

    /**
     * Sends updated warehouse and player inventory contents to the crafting client.
     *
     * @param player the player receiving the snapshot
     * @param warehouseInventory the current warehouse inventory
     * @param playerInventory the current player inventory
     */
    private void sendCraftingContentsSnapshot(
        final Player player,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory)
    {
        if (player instanceof final ServerPlayer serverPlayer)
        {
            ClientboundWorkshopCraftedMessage.sendToPlayer(serverPlayer, buildingPos, warehouseInventory, playerInventory);
        }
    }

    /**
     * Gets the material components required by an architect's cutter recipe.
     *
     * @param recipe the architect's cutter recipe
     * @return the textured block components, or an empty list for an unsupported block
     */
    private static List<IMateriallyTexturedBlockComponent> getDomumComponents(final ArchitectsCutterRecipe recipe)
    {
        if (!(recipe.getBlock() instanceof final IMateriallyTexturedBlock texturedBlock))
        {
            return List.of();
        }

        return new ArrayList<>(texturedBlock.getComponents());
    }

    /**
     * Reads the architect's cutter ingredients from their designated crafting-grid slots.
     *
     * @param normalizedGrid the normalized crafting grid
     * @param componentCount the number of material components required
     * @return copies of the selected material ingredients
     */
    private static List<ItemStack> getDomumIngredients(final List<ItemStack> normalizedGrid, final int componentCount)
    {
        final List<ItemStack> ingredients = new ArrayList<>(componentCount);
        ingredients.add(normalizedGrid.get(1).copy());
        if (componentCount > 1)
        {
            ingredients.add(normalizedGrid.get(4).copy());
        }

        return ingredients;
    }

    /**
     * Creates an architect's cutter input from the selected ingredients.
     *
     * @param ingredients the selected material ingredients
     * @return the populated cutter recipe input
     */
    @SuppressWarnings("null")
    private static ArchitectsCutterRecipeInput buildDomumInput(final List<ItemStack> ingredients)
    {
        final SimpleContainer container = new SimpleContainer(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++)
        {
            container.setItem(i, ingredients.get(i).copy());
        }

        return new ArchitectsCutterRecipeInput(container);
    }

    /**
     * Copies all non-empty ingredient stacks.
     *
     * @param ingredients the ingredients to copy
     * @return independent copies of the non-empty stacks
     */
    private static List<ItemStack> copyIngredients(final List<ItemStack> ingredients)
    {
        final List<ItemStack> copies = new ArrayList<>(ingredients.size());
        for (final ItemStack ingredient : ingredients)
        {
            if (!ingredient.isEmpty())
            {
                copies.add(ingredient.copy());
            }
        }
        return copies;
    }

    /**
     * Builds slot-aware ingredient requirements for a crafting recipe.
     *
     * @param recipe the crafting recipe
     * @param normalizedGrid the normalized grid containing preferred ingredient variants
     * @return the requirements for all populated recipe slots
     */
    private static List<CraftingIngredientRequirement> buildCraftingIngredientRequirements(
        final CraftingRecipe recipe,
        final List<ItemStack> normalizedGrid)
    {
        final List<CraftingSlotRequirement> slotRequirements = OptionalRecipeSupport.buildCraftingSlotRequirements(recipe, 9);
        final List<CraftingIngredientRequirement> requirements = new ArrayList<>(slotRequirements.size());
        for (int slot = 0; slot < Math.min(slotRequirements.size(), normalizedGrid.size()); slot++)
        {
            final CraftingSlotRequirement requirement = slotRequirements.get(slot);
            final ItemStack preferred = normalizedGrid.get(slot);
            if (!requirement.ingredient().isEmpty() && !preferred.isEmpty())
            {
                requirements.add(new CraftingIngredientRequirement(slot, requirement.ingredient(), preferred.copy(), requirement.uniqueGroup()));
            }
        }

        return requirements;
    }

    /**
     * Checks if all the ingredients in the given list are available in the given item handlers.
     * If includePlayerInventory is true, the player's inventory is also checked.
     * 
     * @param ingredients the list of ingredients to check
     * @param warehouseInventory the item handler for the warehouse inventory
     * @param playerInventory the item handler for the player inventory
     * @param includePlayerInventory whether to include the player inventory in the check
     * @return true if all ingredients are available, false otherwise
     */
    private static boolean hasCraftingIngredients(
        final List<ItemStack> ingredients,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        if (!includePlayerInventory)
        {
            return InventoryUtils.areAllItemsInItemHandler(ingredients, warehouseInventory);
        }

        final Map<ItemStack, Integer> required = countRequiredIngredients(ingredients);
        for (final Map.Entry<ItemStack, Integer> entry : required.entrySet())
        {
            ItemStack key = entry.getKey();

            if (key == null || key.isEmpty())
            {
                continue;
            }

            final int available = countMatchingItems(warehouseInventory, key) + countMatchingItems(playerInventory, key);
            if (available < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether the available inventories can satisfy all slot-aware requirements.
     *
     * @param requirements the crafting-slot requirements
     * @param warehouseInventory the warehouse inventory
     * @param playerInventory the player inventory
     * @param includePlayerInventory whether player stock may be used
     * @return true when every requirement has an available match
     */
    private static boolean hasCraftingIngredientRequirements(
        final List<CraftingIngredientRequirement> requirements,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        final List<ItemStack> available = snapshotInventory(warehouseInventory);
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems = new HashMap<>();
        if (includePlayerInventory)
        {
            available.addAll(snapshotInventory(playerInventory));
        }

        for (final CraftingIngredientRequirement requirement : requirements)
        {
            final int matchIndex = findMatchingStackIndex(available, requirement, usedUniqueItems);
            if (matchIndex < 0)
            {
                return false;
            }

            final ItemStack matched = available.get(matchIndex);
            markUniqueIngredientUse(requirement, matched, usedUniqueItems);
            matched.shrink(1);
            if (matched.isEmpty())
            {
                available.remove(matchIndex);
            }
        }

        return true;
    }

    /**
     * Removes the given ingredients from the given item handlers.
     * If includePlayerInventory is true, both the warehouse and player inventory are checked.
     * 
     * @param ingredients the list of ingredients to remove
     * @param warehouseInventory the item handler for the warehouse inventory
     * @param playerInventory the item handler for the player inventory
     * @param includePlayerInventory whether to include the player inventory in the removal
     * @return true if all ingredients were removed, false otherwise
     */
    private static boolean removeIngredients(
        final List<ItemStack> ingredients,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        if (!includePlayerInventory)
        {
            return InventoryUtils.removeStacksFromItemHandler(warehouseInventory, ingredients);
        }

        for (final Map.Entry<ItemStack, Integer> entry : countRequiredIngredients(ingredients).entrySet())
        {
            ItemStack key = entry.getKey();

            if (key == null || key.isEmpty())
            {
                continue;
            }

            int remaining = removeMatchingItems(warehouseInventory, key, entry.getValue());
            if (remaining > 0)
            {
                remaining = removeMatchingItems(playerInventory, key, remaining);
            }

            if (remaining > 0)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts all slot-aware ingredients and records the source of each extracted stack.
     *
     * @param requirements the crafting-slot requirements
     * @param warehouseInventory the warehouse inventory
     * @param playerInventory the player inventory
     * @param includePlayerInventory whether player stock may be used
     * @return extraction receipts in requirement order, or null if a requirement cannot be extracted
     */
    private static List<ExtractedCraftingIngredient> removeCraftingIngredientRequirements(
        final List<CraftingIngredientRequirement> requirements,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems = new HashMap<>();
        final List<ExtractedCraftingIngredient> extractedIngredients = new ArrayList<>(requirements.size());
        for (final CraftingIngredientRequirement requirement : requirements)
        {
            ItemStack extracted = removeMatchingIngredient(warehouseInventory, requirement, usedUniqueItems, false);
            if (!extracted.isEmpty())
            {
                extractedIngredients.add(new ExtractedCraftingIngredient(requirement.gridSlot(), extracted, IngredientOrigin.WAREHOUSE));
                continue;
            }

            extracted = includePlayerInventory
                ? removeMatchingIngredient(playerInventory, requirement, usedUniqueItems, false)
                : ItemStack.EMPTY;
            if (!extracted.isEmpty())
            {
                extractedIngredients.add(new ExtractedCraftingIngredient(requirement.gridSlot(), extracted, IngredientOrigin.PLAYER));
                continue;
            }

            extracted = removeMatchingIngredient(warehouseInventory, requirement, usedUniqueItems, true);
            if (!extracted.isEmpty())
            {
                extractedIngredients.add(new ExtractedCraftingIngredient(requirement.gridSlot(), extracted, IngredientOrigin.WAREHOUSE));
                continue;
            }

            extracted = includePlayerInventory
                ? removeMatchingIngredient(playerInventory, requirement, usedUniqueItems, true)
                : ItemStack.EMPTY;
            if (!extracted.isEmpty())
            {
                extractedIngredients.add(new ExtractedCraftingIngredient(requirement.gridSlot(), extracted, IngredientOrigin.PLAYER));
                continue;
            }

            return null;
        }

        return extractedIngredients;
    }

    /**
     * Counts the number of required ingredients for the given list of ingredients.
     * 
     * The method iterates over the given list of ingredients, and for each ingredient,
     * it creates a new ItemStack with a count of 1 and merges it into the given map
     * with the count of the original ingredient. If the map already contains the
     * ingredient, the count is added to the existing value. Otherwise, the ingredient is
     * added to the map with the given count.
     * 
     * @param ingredients the list of ingredients to count
     * @return a map of ingredients to their required counts
     */
    private static Map<ItemStack, Integer> countRequiredIngredients(final List<ItemStack> ingredients)
    {
        final Map<ItemStack, Integer> required = new HashMap<>();
        for (final ItemStack ingredient : ingredients)
        {
            if (ingredient.isEmpty())
            {
                continue;
            }

            final ItemStack single = ingredient.copy();
            single.setCount(1);
            mergeRequiredIngredient(required, single, ingredient.getCount());
        }

        return required;
    }

    /**
     * Merges the given ingredient and count into the given map of required ingredients.
     * If the map already contains the given ingredient, the count is added to the existing value.
     * Otherwise, the ingredient is added to the map with the given count.
     *
     * @param required the map of required ingredients to merge into
     * @param ingredient the ingredient to merge
     * @param count the count of the ingredient to merge
     */
    private static void mergeRequiredIngredient(final Map<ItemStack, Integer> required, final @Nonnull ItemStack ingredient, final int count)
    {
        for (final Map.Entry<ItemStack, Integer> entry : required.entrySet())
        {
            ItemStack key = entry.getKey();
            if (key == null)
            {
                continue;
            }

            if (ItemStack.isSameItemSameComponents(key, ingredient))
            {
                entry.setValue(entry.getValue() + count);
                return;
            }
        }

        required.put(ingredient.copy(), count);
    }

    /**
     * Counts the number of matching items in the given inventory.
     * The method iterates over all slots in the inventory and checks if the stack in the slot
     * matches the given target item (ignoring damage value and NBT). If the stack matches, its count is added to the total count.
     *
     * @param inventory the inventory to count matching items in
     * @param target the target item to count
     * @return the total count of matching items in the inventory
     */
    private static int countMatchingItems(final IItemHandler inventory, final @Nonnull ItemStack target)
    {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target))
            {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Removes matching items from the given inventory.
     * The method iterates over all slots in the inventory and checks if the stack in the slot
     * matches the given target item (ignoring damage value and NBT). If the stack matches, it is removed
     * from the inventory up to the given count.
     *
     * @param inventory the inventory to remove matching items from
     * @param target the target item to remove
     * @param count the maximum number of items to remove
     * @return the number of items remaining after the removal
     */
    private static int removeMatchingItems(final IItemHandler inventory, final @Nonnull ItemStack target, final int count)
    {
        int remaining = count;
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target))
            {
                continue;
            }

            remaining -= inventory.extractItem(slot, remaining, false).getCount();
        }

        return remaining;
    }

    /**
     * Copies every non-empty stack in an item handler.
     *
     * @param inventory the inventory to snapshot
     * @return mutable copies of the inventory's non-empty stacks
     */
    private static List<ItemStack> snapshotInventory(final IItemHandler inventory)
    {
        final List<ItemStack> snapshot = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty())
            {
                snapshot.add(stack.copy());
            }
        }

        return snapshot;
    }

    /**
     * Finds the index of a matching stack in the given list of available stacks.
     * If the requirement has a preferred stack, the method will first try to find a stack
     * that matches the preferred stack. If no matching stack is found, the method will
     * then try to find a stack that matches the given ingredient.
     *
     * @param available the list of available stacks to search
     * @param requirement the requirement to search for
     * @return the index of the matching stack, or -1 if no matching stack is found
     */
    @SuppressWarnings("null")
    private static int findMatchingStackIndex(
        final List<ItemStack> available,
        final CraftingIngredientRequirement requirement,
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems)
    {
        for (int i = 0; i < available.size(); i++)
        {
            final ItemStack stack = available.get(i);
            if (ItemStack.isSameItemSameComponents(stack, requirement.preferredStack()) && canUseUniqueIngredient(requirement, stack, usedUniqueItems))
            {
                return i;
            }
        }

        for (int i = 0; i < available.size(); i++)
        {
            final ItemStack stack = available.get(i);
            if (requirement.ingredient().test(stack) && canUseUniqueIngredient(requirement, stack, usedUniqueItems))
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Removes a matching ingredient from the given inventory.
     * The ingredientFallback flag selects whether this pass removes only the preferred stack or only an ingredient fallback.
     *
     * @param inventory the inventory to remove the matching ingredient from
     * @param requirement the requirement to remove a matching ingredient for
     * @param ingredientFallback whether to remove any ingredient match instead of the preferred stack
     * @return true if a matching ingredient was removed, false otherwise
     */
    private static ItemStack removeMatchingIngredient(
        final IItemHandler inventory,
        final CraftingIngredientRequirement requirement,
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems,
        final boolean ingredientFallback)
    {
        ItemStack preferredStack = requirement.preferredStack();

        if (preferredStack == null || preferredStack.isEmpty())
        {
            return ItemStack.EMPTY;
        }   

        final int slot = findMatchingInventorySlot(inventory, requirement, usedUniqueItems, ingredientFallback);
        if (slot < 0)
        {
            return ItemStack.EMPTY;
        }

        final ItemStack extracted = inventory.extractItem(slot, 1, false);
        if (extracted.getCount() == 1)
        {
            markUniqueIngredientUse(requirement, extracted, usedUniqueItems);
            return extracted;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Finds an inventory slot matching either the preferred stack or ingredient fallback.
     *
     * @param inventory the inventory to search
     * @param requirement the slot requirement to satisfy
     * @param usedUniqueItems item identifiers already assigned to unique groups
     * @param ingredientFallback whether to accept any ingredient match
     * @return the matching inventory slot, or -1 if none is available
     */
    private static int findMatchingInventorySlot(
        final IItemHandler inventory,
        final CraftingIngredientRequirement requirement,
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems,
        final boolean ingredientFallback)
    {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }

            final ItemStack preferredStack = requirement.preferredStack();
            if (preferredStack == null)
            {
                continue;
            }

            if (!ingredientFallback
                && ItemStack.isSameItemSameComponents(stack, preferredStack)
                && canUseUniqueIngredient(requirement, stack, usedUniqueItems))
            {
                return slot;
            }

            if (ingredientFallback
                && requirement.ingredient().test(stack)
                && canUseUniqueIngredient(requirement, stack, usedUniqueItems))
            {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Checks if the given ItemStack can be used to fulfill the given crafting ingredient requirement,
     * taking into account the unique group of the requirement and the items that have already been used.
     * If the requirement does not have a unique group, this method will always return true.
     *
     * @param requirement the crafting ingredient requirement to check
     * @param stack the ItemStack to check
     * @param usedUniqueItems a map of unique groups to the items that have already been used
     * @return true if the ItemStack can be used, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean canUseUniqueIngredient(
        final CraftingIngredientRequirement requirement,
        final ItemStack stack,
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems)
    {
        if (requirement.uniqueGroup() == null)
        {
            return true;
        }

        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && !usedUniqueItems.getOrDefault(requirement.uniqueGroup(), Set.of()).contains(itemId);
    }

    /**
     * Marks the given ItemStack as used to fulfill the given crafting ingredient requirement,
     * so that it cannot be used again to fulfill the same requirement.
     * If the requirement does not have a unique group, this method will do nothing.
     *
     * @param requirement the crafting ingredient requirement to mark the item stack as used for
     * @param stack the ItemStack to mark as used
     * @param usedUniqueItems a map of unique groups to the items that have already been used
     */
    private static void markUniqueIngredientUse(
        final CraftingIngredientRequirement requirement,
        final ItemStack stack,
        final Map<ResourceLocation, Set<ResourceLocation>> usedUniqueItems)
    {
        if (requirement.uniqueGroup() == null)
        {
            return;
        }

        @SuppressWarnings("null")
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null)
        {
            usedUniqueItems.computeIfAbsent(requirement.uniqueGroup(), ignored -> new HashSet<>()).add(itemId);
        }
    }

    private record CraftingIngredientRequirement(int gridSlot, Ingredient ingredient, ItemStack preferredStack, ResourceLocation uniqueGroup)
    {
    }

    private enum IngredientOrigin
    {
        WAREHOUSE,
        PLAYER
    }

    private record ExtractedCraftingIngredient(int gridSlot, ItemStack stack, IngredientOrigin origin)
    {
    }

    /**
     * Builds the localized missing-ingredients status for the active inventory setting.
     *
     * @param includePlayerInventory whether player stock was included
     * @return the missing-ingredients status message
     */
    private static Component getMissingStatusText(final boolean includePlayerInventory)
    {
        return Component.translatable(includePlayerInventory
            ? "com.warehouseworkshop.core.gui.workshop.status.missing.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.missing");
    }

    /**
     * Adds a stack to the player's inventory and drops any overflow nearby.
     *
     * @param player the destination player
     * @param stack the stack to deliver
     */
    private static void giveToPlayer(final Player player, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        final boolean added = player.getInventory().add(stack);
        if (!added && !stack.isEmpty())
        {
            player.drop(stack, false);
        }
    }

    /**
     * Delivers a crafted result or transformed remainder to the configured output target.
     *
     * @param player the crafting player and warehouse-overflow recipient
     * @param warehouseInventory the warehouse inventory
     * @param stack the stack to deliver
     * @param outputTarget the configured output destination
     */
    private static void giveCraftingOutput(final Player player, final @Nonnull IItemHandler warehouseInventory, final ItemStack stack, final OutputTarget outputTarget)
    {
        if (outputTarget.isWarehouse())
        {
            giveToWarehouse(player, warehouseInventory, stack);
            return;
        }

        giveToPlayer(player, stack);
    }

    /**
     * Returns a non-consumed ingredient to the inventory from which it was extracted.
     *
     * @param player the crafting player and warehouse-overflow recipient
     * @param warehouseInventory the warehouse inventory
     * @param stack the recipe remainder to return
     * @param origin the inventory that supplied the ingredient
     */
    private static void giveCraftingRemainder(
        final Player player,
        final @Nonnull IItemHandler warehouseInventory,
        final ItemStack stack,
        final IngredientOrigin origin)
    {
        if (origin == IngredientOrigin.WAREHOUSE)
        {
            giveToWarehouse(player, warehouseInventory, stack);
            return;
        }

        giveToPlayer(player, stack);
    }

    /**
     * Inserts a stack into warehouse storage and gives any overflow to the player.
     *
     * @param player the overflow recipient
     * @param warehouseInventory the warehouse inventory
     * @param stack the stack to insert
     */
    private static void giveToWarehouse(final Player player, final @Nonnull IItemHandler warehouseInventory, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        final ItemStack remainder = ItemHandlerHelper.insertItemStacked(warehouseInventory, stack, false);
        if (!remainder.isEmpty())
        {
            giveToPlayer(player, remainder);
        }
    }
}
