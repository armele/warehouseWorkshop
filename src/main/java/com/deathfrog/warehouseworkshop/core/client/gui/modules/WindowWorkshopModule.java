package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.network.SetWorkshopIncludePlayerInventoryMessage;
import com.deathfrog.warehouseworkshop.core.network.SetWorkshopOutputTargetMessage;
import com.deathfrog.warehouseworkshop.core.network.WorkshopCraftMessage;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Gradient;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.tileentities.TileEntityRack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Workshop module window styled after the MineColonies recipe teaching UI.
 */
public class WindowWorkshopModule extends AbstractModuleWindow<WorkshopModuleView>
{
    private static final int GRID_SIZE = 9;
    private static final int DOMUM_FIRST_SLOT = 1;
    private static final int DOMUM_SECOND_SLOT = 4;
    private static final int STATUS_TEXT_COLOR = Color.getByName("black", 0x000000);
    private static final int STATUS_MISMATCH_TEXT_COLOR = Color.getByName("red", 0xFF0000);
    private static final int SLOT_BACKGROUND_ALPHA = 96;
    private static final int PRESENT_SLOT_RED = 48;
    private static final int PRESENT_SLOT_GREEN = 160;
    private static final int PRESENT_SLOT_BLUE = 64;
    private static final int MISSING_SLOT_RED = 180;
    private static final int MISSING_SLOT_GREEN = 48;
    private static final int MISSING_SLOT_BLUE = 48;

    private final List<Gradient> gridBackgrounds = new ArrayList<>(GRID_SIZE);
    private final List<ItemIcon> gridIcons = new ArrayList<>(GRID_SIZE);
    private final List<Button> gridButtons = new ArrayList<>(GRID_SIZE);
    private final List<ItemStack> selectedGrid = new ArrayList<>(Collections.nCopies(GRID_SIZE, ItemStack.EMPTY));
    private final List<ItemStack> displayGrid = new ArrayList<>(Collections.nCopies(GRID_SIZE, ItemStack.EMPTY));
    private final List<SlotState> slotStates = new ArrayList<>(Collections.nCopies(GRID_SIZE, SlotState.EMPTY));
    private final List<List<ItemStorage>> slotMatches = new ArrayList<>(GRID_SIZE);

    private final ItemIcon requestIcon;
    private final ItemIcon outputIcon;
    private final Text requestLabel;
    private final Text recipeLabel;
    private final Text outputLabel;
    private final Text statusLabel;
    private final Button craftButton;
    private final Button craftAllButton;
    private final Button outputTargetButton;
    private final Button includePlayerInventoryButton;

    private final Map<ItemStorage, Integer> warehouseStock = new HashMap<>();
    private final Map<ItemStorage, Integer> playerStock = new HashMap<>();
    private final List<ItemStack> requestOutputs = new ArrayList<>();
    private final List<WorkshopRecipe> matchingRecipes = new ArrayList<>();

    private @Nullable IRequest<?> selectedRequest;
    private ItemStack jeiSearchOutput = ItemStack.EMPTY;
    private int selectedRecipeIndex = -1;

    public WindowWorkshopModule(final WorkshopModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "gui/layouthuts/layoutworkshopmodule.xml"));

        for (int i = 0; i < GRID_SIZE; i++)
        {
            slotMatches.add(new ArrayList<>());
            final int slot = i;
            gridBackgrounds.add(window.findPaneOfTypeByID("gridBackground" + i, Gradient.class));
            gridIcons.add(window.findPaneOfTypeByID("gridIcon" + i, ItemIcon.class));
            gridButtons.add(window.findPaneOfTypeByID("gridButton" + i, Button.class));
            registerButton("gridButton" + i, () -> setActiveSlot(slot));
        }

        this.requestIcon = window.findPaneOfTypeByID("requestPreview", ItemIcon.class);
        this.outputIcon = window.findPaneOfTypeByID("outputPreview", ItemIcon.class);
        this.requestLabel = window.findPaneOfTypeByID("requestLabel", Text.class);
        this.recipeLabel = window.findPaneOfTypeByID("recipeLabel", Text.class);
        this.outputLabel = window.findPaneOfTypeByID("outputLabel", Text.class);
        this.statusLabel = window.findPaneOfTypeByID("statusLabel", Text.class);
        this.craftButton = window.findPaneOfTypeByID("craft", Button.class);
        this.craftAllButton = window.findPaneOfTypeByID("craftAll", Button.class);
        this.outputTargetButton = window.findPaneOfTypeByID("outputTarget", Button.class);
        this.includePlayerInventoryButton = window.findPaneOfTypeByID("includePlayerInventory", Button.class);

        registerButton("request", this::showRequests);
        registerButton("clear", this::clearGrid);
        registerButton("craft", this::craft);
        registerButton("craftAll", this::craftAll);
        registerButton("prevRecipe", this::selectPreviousRecipe);
        registerButton("nextRecipe", this::selectNextRecipe);
        registerButton("outputTarget", this::toggleOutputTarget);
        registerButton("includePlayerInventory", this::toggleIncludePlayerInventory);

        Button requestButton = window.findPaneOfTypeByID("request", Button.class);
        PaneBuilders.tooltipBuilder()
                .append(Component.translatable("com.warehouseworkshop.core.gui.workshop.request.tooltip"))
                .hoverPane(requestButton)
                .build();

        if (Compatibility.jeiProxy.isLoaded())
        {
            PaneBuilders.tooltipBuilder()
                    .append(Component.translatable("com.warehouseworkshop.core.gui.workshop.output.tooltip"))
                    .hoverPane(requestIcon)
                    .build();
        }
    }

    private enum RecipeKind
    {
        CRAFTING(WorkshopCraftMessage.CRAFT_TYPE_CRAFTING),
        DOMUM(WorkshopCraftMessage.CRAFT_TYPE_DOMUM);

        private final int networkId;

        RecipeKind(final int networkId)
        {
            this.networkId = networkId;
        }
    }

    private enum SlotState
    {
        EMPTY,
        PRESENT,
        MISSING
    }

    private record WorkshopRecipe(
        RecipeKind kind,
        ResourceLocation id,
        ItemStack output,
        @Nullable RecipeHolder<CraftingRecipe> craftingRecipe,
        @Nullable RecipeHolder<ArchitectsCutterRecipe> domumRecipe)
    {
        static WorkshopRecipe crafting(final RecipeHolder<CraftingRecipe> recipe, final Level level)
        {
            return new WorkshopRecipe(
                RecipeKind.CRAFTING,
                recipe.id(),
                recipe.value().getResultItem(level.registryAccess()).copy(),
                recipe,
                null);
        }

        static WorkshopRecipe domum(final RecipeHolder<ArchitectsCutterRecipe> recipe, final ItemStack output)
        {
            final ItemStack outputCopy = output.copy();
            outputCopy.setCount(Math.max(1, outputCopy.getCount()));
            return new WorkshopRecipe(RecipeKind.DOMUM, recipe.id(), outputCopy, null, recipe);
        }
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        refreshWarehouseStock();
        refreshPlayerInventoryStock();
        updateOutputTargetButton();
        updateIncludePlayerInventoryButton();
        updateRequestDetails();
        updateGridIcons();
    }

    private void toggleOutputTarget()
    {
        final OutputTarget outputTarget = moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? OutputTarget.PLAYER_INVENTORY
            : OutputTarget.WAREHOUSE_INVENTORY;
        moduleView.setOutputTarget(outputTarget);
        new SetWorkshopOutputTargetMessage(buildingView.getPosition(), outputTarget.getId()).sendToServer();
        updateOutputTargetButton();
    }

    private void toggleIncludePlayerInventory()
    {
        final boolean includePlayerInventory = !moduleView.shouldIncludePlayerInventory();
        moduleView.setIncludePlayerInventory(includePlayerInventory);
        new SetWorkshopIncludePlayerInventoryMessage(buildingView.getPosition(), includePlayerInventory).sendToServer();
        refreshPlayerInventoryStock();
        updateIncludePlayerInventoryButton();
        applySelectedRecipe();
    }

    private void updateOutputTargetButton()
    {
        outputTargetButton.setText(Component.translatable(moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? "com.warehouseworkshop.core.gui.workshop.output_target.warehouse"
            : "com.warehouseworkshop.core.gui.workshop.output_target.inventory"));
    }

    private void updateIncludePlayerInventoryButton()
    {
        includePlayerInventoryButton.setText(Component.translatable(moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.include_player_inventory.yes"
            : "com.warehouseworkshop.core.gui.workshop.include_player_inventory.no"));
    }

    private void showRequests()
    {
        refreshWarehouseStock();
        refreshPlayerInventoryStock();
        new WindowWorkshopSelectRequest(moduleView, this::matchingRequest, this::reopenWithRequest).open();
    }

    private boolean matchingRequest(@NotNull final IRequest<?> request)
    {
        final List<ItemStack> outputs = extractRequestedOutputs(request);
        return !outputs.isEmpty() && !findMatchingRecipes(outputs, false).isEmpty();
    }

    private void reopenWithRequest(@Nullable final IRequest<?> request)
    {
        if (request != null)
        {
            selectRequest(request);
        }

        open();
    }

    private void selectRequest(@NotNull final IRequest<?> request)
    {
        clearJeiSelection(true);
        outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.request_item"));
        this.selectedRequest = request;
        this.requestOutputs.clear();
        this.requestOutputs.addAll(extractRequestedOutputs(request));
        refreshPlayerInventoryStock();
        this.matchingRecipes.addAll(findMatchingRecipes(this.requestOutputs, false));
        this.selectedRecipeIndex = this.matchingRecipes.isEmpty() ? -1 : 0;
        applySelectedRecipe();
    }

    public void selectJeiOutput(@NotNull final ItemStack output)
    {
        if (output.isEmpty())
        {
            return;
        }

        clearRequestSelection();
        refreshPlayerInventoryStock();
        this.jeiSearchOutput = output.copy();
        this.jeiSearchOutput.setCount(1);
        outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.search_item"));
        this.matchingRecipes.clear();
        this.matchingRecipes.addAll(findMatchingRecipes(List.of(output), false));
        this.selectedRecipeIndex = this.matchingRecipes.isEmpty() ? -1 : 0;
        applySelectedRecipe();
    }

    public void applyJeiIngredientToSlot(final int slot, @NotNull final ItemStack stack)
    {
        if (slot < 0 || slot >= GRID_SIZE || stack.isEmpty() || !canAcceptIngredientInSlot(slot))
        {
            return;
        }

        clearRequestSelection();
        clearJeiSelection(false);
        outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.search_item"));

        final ItemStack selected = stack.copy();
        selected.setCount(1);
        selectedGrid.set(slot, selected);
        displayGrid.set(slot, selected.copy());
        slotStates.set(slot, SlotState.PRESENT);
        updateGridIcons();
    }

    private void selectPreviousRecipe()
    {
        if (matchingRecipes.isEmpty())
        {
            return;
        }

        selectedRecipeIndex = (selectedRecipeIndex - 1 + matchingRecipes.size()) % matchingRecipes.size();
        applySelectedRecipe();
    }

    private void selectNextRecipe()
    {
        if (matchingRecipes.isEmpty())
        {
            return;
        }

        selectedRecipeIndex = (selectedRecipeIndex + 1) % matchingRecipes.size();
        applySelectedRecipe();
    }

    private void applySelectedRecipe()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            slotMatches.get(i).clear();
            selectedGrid.set(i, ItemStack.EMPTY);
            displayGrid.set(i, ItemStack.EMPTY);
            slotStates.set(i, SlotState.EMPTY);
        }

        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        if (selectedRecipe == null)
        {
            updateRequestDetails();
            updateGridIcons();
            return;
        }

        if (selectedRecipe.kind() == RecipeKind.DOMUM)
        {
            applySelectedDomumRecipe(selectedRecipe);
            updateRequestDetails();
            updateGridIcons();
            return;
        }

        final List<Ingredient> slottedIngredients = buildSlottedIngredients(selectedRecipe.craftingRecipe().value());
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);

        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final Ingredient ingredient = slottedIngredients.get(slot);
            if (ingredient.isEmpty())
            {
                continue;
            }

            displayGrid.set(slot, getIngredientDisplayStack(ingredient));
            final List<ItemStorage> matches = findWarehouseMatches(ingredient);
            slotMatches.get(slot).addAll(matches);

            if (tryReserveSlotFromMatches(slot, matches, remainingStock))
            {
                setSlotPresent(slot);
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerMatches = findPlayerInventoryMatches(ingredient);
                slotMatches.get(slot).addAll(playerMatches);
                if (tryReserveSlotFromMatches(slot, playerMatches, remainingPlayerStock))
                {
                    setSlotPresent(slot);
                    continue;
                }
            }

            slotStates.set(slot, SlotState.MISSING);
        }

        updateRequestDetails();
        updateGridIcons();
    }

    private void applySelectedDomumRecipe(final WorkshopRecipe selectedRecipe)
    {
        final List<DomumSlotRequirement> requirements = getDomumRequirements(selectedRecipe);
        if (requirements.isEmpty())
        {
            return;
        }

        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        for (int component = 0; component < requirements.size(); component++)
        {
            final int slot = domumGridSlot(component);
            displayGrid.set(slot, getDomumRequirementDisplayStack(requirements.get(component)));
            final List<ItemStorage> matches = findDomumMatches(requirements.get(component).material(), warehouseStock);
            slotMatches.get(slot).addAll(matches);

            if (tryReserveSlotFromMatches(slot, matches, remainingStock))
            {
                setSlotPresent(slot);
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerMatches = findDomumMatches(requirements.get(component).material(), playerStock);
                slotMatches.get(slot).addAll(playerMatches);
                if (tryReserveSlotFromMatches(slot, playerMatches, remainingPlayerStock))
                {
                    setSlotPresent(slot);
                    continue;
                }
            }

            slotStates.set(slot, SlotState.MISSING);
        }
    }

    private void setSlotPresent(final int slot)
    {
        displayGrid.set(slot, selectedGrid.get(slot));
        slotStates.set(slot, SlotState.PRESENT);
    }

    private boolean tryReserveSlotFromMatches(final int slot, final List<ItemStorage> matches, final Map<ItemStorage, Integer> remainingStock)
    {
        for (final ItemStorage match : matches)
        {
            final int available = remainingStock.getOrDefault(match, 0);
            if (available > 0)
            {
                final ItemStack chosen = match.getItemStack().copy();
                chosen.setCount(1);
                selectedGrid.set(slot, chosen);
                remainingStock.put(match, available - 1);
                return true;
            }
        }

        return false;
    }

    private void setActiveSlot(final int slot)
    {
        if (!canAcceptIngredientInSlot(slot))
        {
            return;
        }

        if (slotStates.get(slot) == SlotState.MISSING)
        {
            selectJeiOutput(displayGrid.get(slot));
            return;
        }

        updateRequestDetails();
    }

    public boolean canAcceptIngredientInSlot(final int slot)
    {
        if (slot < 0 || slot >= GRID_SIZE)
        {
            return false;
        }

        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        return selectedRecipe == null || selectedRecipe.kind() != RecipeKind.DOMUM || slot == DOMUM_FIRST_SLOT || slot == DOMUM_SECOND_SLOT;
    }

    private void clearGrid()
    {
        clearRequestSelection();
        clearJeiSelection(true);
        updateGridIcons();
    }

    private void craft()
    {
        performCraft(1);
    }

    private void craftAll()
    {
        final int craftCount = getCraftAllCount();
        if (craftCount <= 0)
        {
            setStatusText(getMissingStatusText());
            return;
        }

        performCraft(craftCount);
    }

    private void performCraft(final int craftCount)
    {
        if (getActiveRecipe() == null)
        {
            setStatusText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.norecipe"));
            return;
        }

        if (!isGridCraftable())
        {
            setStatusText(getMissingStatusText());
            return;
        }

        final WorkshopRecipe activeRecipe = getActiveRecipe();
        if (activeRecipe == null)
        {
            setStatusText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.norecipe"));
            return;
        }

        new WorkshopCraftMessage(buildingView.getPosition(), List.copyOf(selectedGrid), craftCount, activeRecipe.kind().networkId, activeRecipe.id()).sendToServer();
        setStatusText(Component.translatable(
            craftCount == 1
                ? "com.warehouseworkshop.core.gui.workshop.status.sent"
                : "com.warehouseworkshop.core.gui.workshop.status.sent.all",
            craftCount));
    }

    public void refreshCraftingContents(
        final BlockPos buildingPos,
        final List<ItemStack> warehouseStockSnapshot,
        final List<ItemStack> playerStockSnapshot)
    {
        if (!Objects.equals(buildingView.getPosition(), buildingPos))
        {
            return;
        }

        applyStockSnapshot(warehouseStock, warehouseStockSnapshot);
        applyStockSnapshot(playerStock, playerStockSnapshot);
        applySelectedRecipe();
    }

    private void applyStockSnapshot(final Map<ItemStorage, Integer> stock, final List<ItemStack> snapshot)
    {
        stock.clear();
        for (final ItemStack stack : snapshot)
        {
            if (!stack.isEmpty())
            {
                stock.merge(new ItemStorage(stack.copy()), stack.getCount(), Integer::sum);
            }
        }
    }

    private int getCraftAllCount()
    {
        final WorkshopRecipe selectedRecipe = getActiveRecipe();
        if (selectedRecipe == null)
        {
            return 0;
        }

        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return 0;
        }

        final ItemStack recipeOutput = getCurrentGridOutput();
        final int requestedCrafts = getRequestedCraftCount(recipeOutput);
        final int supportedCrafts = getSupportedCraftCount();
        if (requestedCrafts <= 0)
        {
            return supportedCrafts;
        }

        return Math.min(requestedCrafts, supportedCrafts);
    }

    private int getRequestedCraftCount(final ItemStack recipeOutput)
    {
        final int requestedOutputCount = getRequestedOutputCount(recipeOutput);
        if (requestedOutputCount <= 0)
        {
            return 0;
        }

        final int outputPerCraft = Math.max(1, recipeOutput.getCount());
        return (requestedOutputCount + outputPerCraft - 1) / outputPerCraft;
    }

    private int getRequestedOutputCount(final ItemStack recipeOutput)
    {
        if (recipeOutput.isEmpty())
        {
            return 0;
        }

        int requestedOutputCount = 0;
        for (final ItemStack requested : requestOutputs)
        {
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(recipeOutput, requested, false, true))
            {
                requestedOutputCount += requested.getCount();
            }
        }

        if (requestedOutputCount <= 0)
        {
            return 0;
        }

        return requestedOutputCount;
    }

    private int getSupportedCraftCount()
    {
        final Map<ItemStorage, Integer> requiredPerCraft = new HashMap<>();
        for (final ItemStack stack : selectedGrid)
        {
            if (!stack.isEmpty())
            {
                requiredPerCraft.merge(new ItemStorage(stack.copy()), stack.getCount(), Integer::sum);
            }
        }

        if (requiredPerCraft.isEmpty())
        {
            return 0;
        }

        int supportedCrafts = Integer.MAX_VALUE;
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            supportedCrafts = Math.min(supportedCrafts, getAvailableIngredientCount(entry.getKey()) / entry.getValue());
        }

        return supportedCrafts == Integer.MAX_VALUE ? 0 : supportedCrafts;
    }

    private boolean isGridCraftable()
    {
        return getActiveRecipe() != null && !getCurrentGridOutput().isEmpty() && hasCraftingContents(List.copyOf(selectedGrid));
    }

    private @Nullable RecipeHolder<CraftingRecipe> getCurrentGridRecipe()
    {
        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return null;
        }

        final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
    }

    private ItemStack getCurrentGridOutput()
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        if (selectedRecipe != null && selectedRecipe.kind() == RecipeKind.DOMUM)
        {
            return assembleDomumOutput(selectedRecipe);
        }

        final RecipeHolder<CraftingRecipe> currentRecipe = getCurrentGridRecipe();
        final Level level = Minecraft.getInstance().level;
        if (currentRecipe == null || level == null)
        {
            return ItemStack.EMPTY;
        }

        return currentRecipe.value().getResultItem(level.registryAccess()).copy();
    }

    private boolean doesCurrentGridMatchRequest()
    {
        final ItemStack gridOutput = getCurrentGridOutput();
        if (gridOutput.isEmpty())
        {
            return false;
        }

        if (selectedRequest != null)
        {
            return getRequestedOutputCount(gridOutput) > 0;
        }

        return jeiSearchOutput.isEmpty()
            || ItemStackUtils.compareItemStacksIgnoreStackSize(gridOutput, jeiSearchOutput, false, true);
    }

    private ItemStack getRequestPreviewOutput()
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        if (selectedRecipe != null)
        {
            final ItemStack recipeOutput = selectedRecipe.output().copy();
            final int requestedOutputCount = getRequestedOutputCount(recipeOutput);
            if (requestedOutputCount > 0)
            {
                recipeOutput.setCount(requestedOutputCount);
            }
            return recipeOutput;
        }

        if (requestOutputs.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        return requestOutputs.get(0).copy();
    }

    private void refreshWarehouseStock()
    {
        warehouseStock.clear();

        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return;
        }

        final List<BlockPos> containers = new ArrayList<>(buildingView.getContainers());
        containers.add(buildingView.getPosition());

        for (final BlockPos pos : containers)
        {
            final BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof final TileEntityRack rack)
            {
                rack.getAllContent().forEach((storage, amount) -> warehouseStock.merge(storage, amount, Integer::sum));
            }
        }
    }

    private void refreshPlayerInventoryStock()
    {
        playerStock.clear();

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
        {
            return;
        }

        for (final ItemStack stack : minecraft.player.getInventory().items)
        {
            if (!stack.isEmpty())
            {
                playerStock.merge(new ItemStorage(stack.copy()), stack.getCount(), Integer::sum);
            }
        }
    }

    private List<WorkshopRecipe> findMatchingRecipes(final List<ItemStack> requestedOutputs, final boolean requireWarehouseContents)
    {
        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return List.of();
        }

        final RecipeManager recipeManager = level.getRecipeManager();
        final List<WorkshopRecipe> matches = new ArrayList<>();

        for (final RecipeHolder<CraftingRecipe> recipe : recipeManager.getAllRecipesFor(RecipeType.CRAFTING))
        {
            final CraftingRecipe craftingRecipe = recipe.value();
            if (!craftingRecipe.canCraftInDimensions(3, 3))
            {
                continue;
            }

            final ItemStack output = craftingRecipe.getResultItem(level.registryAccess());
            if (requestedOutputs.stream().noneMatch(requested -> ItemStackUtils.compareItemStacksIgnoreStackSize(output, requested, false, true)))
            {
                continue;
            }

            if (!requireWarehouseContents || buildSlotMatches(craftingRecipe) != null)
            {
                matches.add(WorkshopRecipe.crafting(recipe, level));
            }
        }

        for (final RecipeHolder<ArchitectsCutterRecipe> recipe : recipeManager.getAllRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get()))
        {
            for (final ItemStack requested : requestedOutputs)
            {
                final ItemStack output = buildDomumOutputForRequest(recipe.value(), requested, level);
                if (output.isEmpty())
                {
                    continue;
                }

                final WorkshopRecipe workshopRecipe = WorkshopRecipe.domum(recipe, output);
                if (!requireWarehouseContents || buildDomumSlotMatches(workshopRecipe) != null)
                {
                    matches.add(workshopRecipe);
                }
            }
        }

        matches.sort(Comparator.comparing((WorkshopRecipe recipe) -> recipe.output().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(recipe -> recipe.kind().ordinal())
            .thenComparing(recipe -> recipe.id().toString()));
        return matches;
    }

    private @Nullable List<List<ItemStorage>> buildSlotMatches(final CraftingRecipe recipe)
    {
        final List<Ingredient> slottedIngredients = buildSlottedIngredients(recipe);
        final List<List<ItemStorage>> matches = new ArrayList<>(GRID_SIZE);
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);

        for (int i = 0; i < GRID_SIZE; i++)
        {
            matches.add(new ArrayList<>());
        }

        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final Ingredient ingredient = slottedIngredients.get(slot);
            if (ingredient.isEmpty())
            {
                continue;
            }

            final List<ItemStorage> candidates = findWarehouseMatches(ingredient);
            matches.get(slot).addAll(candidates);

            if (reserveFirstAvailable(candidates, remainingStock))
            {
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerCandidates = findPlayerInventoryMatches(ingredient);
                matches.get(slot).addAll(playerCandidates);
                if (reserveFirstAvailable(playerCandidates, remainingPlayerStock))
                {
                    continue;
                }
            }

            return null;
        }

        return matches;
    }

    private @Nullable List<List<ItemStorage>> buildDomumSlotMatches(final WorkshopRecipe recipe)
    {
        final List<DomumSlotRequirement> requirements = getDomumRequirements(recipe);
        if (requirements.isEmpty())
        {
            return null;
        }

        final List<List<ItemStorage>> matches = new ArrayList<>(GRID_SIZE);
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        for (int i = 0; i < GRID_SIZE; i++)
        {
            matches.add(new ArrayList<>());
        }

        for (int component = 0; component < requirements.size(); component++)
        {
            final int slot = domumGridSlot(component);
            final List<ItemStorage> candidates = findDomumMatches(requirements.get(component).material(), warehouseStock);
            matches.get(slot).addAll(candidates);

            if (reserveFirstAvailable(candidates, remainingStock))
            {
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerCandidates = findDomumMatches(requirements.get(component).material(), playerStock);
                matches.get(slot).addAll(playerCandidates);
                if (reserveFirstAvailable(playerCandidates, remainingPlayerStock))
                {
                    continue;
                }
            }

            return null;
        }

        return matches;
    }

    private record DomumSlotRequirement(Block material)
    {
    }

    private boolean reserveFirstAvailable(final List<ItemStorage> candidates, final Map<ItemStorage, Integer> remainingStock)
    {
        for (final ItemStorage candidate : candidates)
        {
            final int available = remainingStock.getOrDefault(candidate, 0);
            if (available > 0)
            {
                remainingStock.put(candidate, available - 1);
                return true;
            }
        }

        return false;
    }

    private List<Ingredient> buildSlottedIngredients(final CraftingRecipe recipe)
    {
        final List<Ingredient> slots = new ArrayList<>(Collections.nCopies(GRID_SIZE, Ingredient.EMPTY));
        final List<Ingredient> ingredients = recipe.getIngredients();

        if (recipe instanceof final ShapedRecipe shapedRecipe)
        {
            final int width = shapedRecipe.getWidth();
            final int height = shapedRecipe.getHeight();
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    slots.set((y * 3) + x, ingredients.get((y * width) + x));
                }
            }
        }
        else if (recipe instanceof final ShapelessRecipe shapelessRecipe)
        {
            for (int i = 0; i < Math.min(GRID_SIZE, shapelessRecipe.getIngredients().size()); i++)
            {
                slots.set(i, shapelessRecipe.getIngredients().get(i));
            }
        }
        else
        {
            for (int i = 0; i < Math.min(GRID_SIZE, ingredients.size()); i++)
            {
                slots.set(i, ingredients.get(i));
            }
        }

        return slots;
    }

    private List<ItemStorage> findWarehouseMatches(final Ingredient ingredient)
    {
        return findMatches(ingredient, warehouseStock);
    }

    private List<ItemStorage> findPlayerInventoryMatches(final Ingredient ingredient)
    {
        return findMatches(ingredient, playerStock);
    }

    private List<ItemStorage> findMatches(final Ingredient ingredient, final Map<ItemStorage, Integer> stock)
    {
        final List<ItemStorage> matches = new ArrayList<>();

        for (final Map.Entry<ItemStorage, Integer> entry : stock.entrySet())
        {
            if (entry.getValue() > 0 && ingredient.test(entry.getKey().getItemStack()))
            {
                final ItemStorage display = new ItemStorage(entry.getKey().getItemStack().copy(), entry.getKey().ignoreDamageValue(), entry.getKey().ignoreNBT());
                display.setAmount(entry.getValue());
                matches.add(display);
            }
        }

        matches.sort(Comparator.comparingInt(ItemStorage::getAmount).reversed()
            .thenComparing(storage -> storage.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private List<ItemStorage> findDomumMatches(final Block material, final Map<ItemStorage, Integer> stock)
    {
        final List<ItemStorage> matches = new ArrayList<>();

        for (final Map.Entry<ItemStorage, Integer> entry : stock.entrySet())
        {
            final ItemStack stack = entry.getKey().getItemStack();
            if (entry.getValue() > 0 && stack.getItem() instanceof final BlockItem blockItem && blockItem.getBlock() == material)
            {
                final ItemStorage display = new ItemStorage(stack.copy(), entry.getKey().ignoreDamageValue(), entry.getKey().ignoreNBT());
                display.setAmount(entry.getValue());
                matches.add(display);
            }
        }

        matches.sort(Comparator.comparingInt(ItemStorage::getAmount).reversed()
            .thenComparing(storage -> storage.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private ItemStack getIngredientDisplayStack(final Ingredient ingredient)
    {
        for (final ItemStack stack : ingredient.getItems())
        {
            if (!stack.isEmpty())
            {
                final ItemStack displayStack = stack.copy();
                displayStack.setCount(1);
                return displayStack;
            }
        }

        return ItemStack.EMPTY;
    }

    private ItemStack getDomumRequirementDisplayStack(final DomumSlotRequirement requirement)
    {
        final ItemStack displayStack = new ItemStack(requirement.material());
        if (!displayStack.isEmpty())
        {
            displayStack.setCount(1);
        }
        return displayStack;
    }

    private ItemStack buildDomumOutputForRequest(final ArchitectsCutterRecipe recipe, final ItemStack requested, final Level level)
    {
        if (requested.isEmpty() || !matchesDomumRecipeResult(recipe.getResultItem(level.registryAccess()), requested))
        {
            return ItemStack.EMPTY;
        }

        final List<IMateriallyTexturedBlockComponent> components = getDomumComponents(recipe);
        if (components.isEmpty() || components.size() > 2)
        {
            return ItemStack.EMPTY;
        }

        final ItemStack output = requested.copy();
        if (MaterialTextureData.readFromItemStack(output).isEmpty())
        {
            final MaterialTextureData.Builder builder = MaterialTextureData.builder();
            for (final IMateriallyTexturedBlockComponent component : components)
            {
                builder.setComponent(component.getId(), component.getDefault());
            }
            builder.writeToItemStack(output);
        }

        output.setCount(Math.max(components.size(), recipe.getCount()));
        return output;
    }

    private boolean matchesDomumRecipeResult(final ItemStack recipeResult, final ItemStack requested)
    {
        if (!ItemStack.isSameItem(recipeResult, requested))
        {
            return false;
        }

        final BlockItemStateProperties recipeState = recipeResult.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        final BlockItemStateProperties requestedState = requested.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        return recipeState.isEmpty() && requestedState.isEmpty() || recipeState.equals(requestedState);
    }

    private List<DomumSlotRequirement> getDomumRequirements(final WorkshopRecipe recipe)
    {
        if (recipe.domumRecipe() == null)
        {
            return List.of();
        }

        final List<IMateriallyTexturedBlockComponent> components = getDomumComponents(recipe.domumRecipe().value());
        if (components.isEmpty() || components.size() > 2)
        {
            return List.of();
        }

        final MaterialTextureData textureData = MaterialTextureData.readFromItemStack(recipe.output());
        final Map<ResourceLocation, Block> materials = textureData.getTexturedComponents();
        final List<DomumSlotRequirement> requirements = new ArrayList<>(components.size());
        for (final IMateriallyTexturedBlockComponent component : components)
        {
            final Block material = materials.getOrDefault(component.getId(), component.getDefault());
            if (!material.defaultBlockState().is(component.getValidSkins()))
            {
                return List.of();
            }
            requirements.add(new DomumSlotRequirement(material));
        }

        return requirements;
    }

    private List<IMateriallyTexturedBlockComponent> getDomumComponents(final ArchitectsCutterRecipe recipe)
    {
        if (!(recipe.getBlock() instanceof final IMateriallyTexturedBlock texturedBlock))
        {
            return List.of();
        }

        return new ArrayList<>(texturedBlock.getComponents());
    }

    private ItemStack assembleDomumOutput(final WorkshopRecipe recipe)
    {
        if (recipe.domumRecipe() == null)
        {
            return ItemStack.EMPTY;
        }

        final List<DomumSlotRequirement> requirements = getDomumRequirements(recipe);
        if (requirements.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return ItemStack.EMPTY;
        }

        final SimpleContainer input = new SimpleContainer(requirements.size());
        for (int component = 0; component < requirements.size(); component++)
        {
            final ItemStack stack = selectedGrid.get(domumGridSlot(component));
            if (stack.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            input.setItem(component, stack.copy());
        }

        final ArchitectsCutterRecipeInput recipeInput = new ArchitectsCutterRecipeInput(input);
        if (!recipe.domumRecipe().value().matches(recipeInput, level))
        {
            return ItemStack.EMPTY;
        }

        return recipe.domumRecipe().value().assemble(recipeInput, level.registryAccess()).copy();
    }

    private int domumGridSlot(final int component)
    {
        return component == 0 ? DOMUM_FIRST_SLOT : DOMUM_SECOND_SLOT;
    }

    private List<ItemStack> extractRequestedOutputs(final IRequest<?> request)
    {
        if (request.getRequest() instanceof final IConcreteDeliverable concreteDeliverable)
        {
            final List<ItemStack> outputs = new ArrayList<>();
            final int requestedCount = request.getRequest() instanceof final IDeliverable deliverable
                ? deliverable.getCount()
                : 1;
            final List<ItemStack> requestedItems = concreteDeliverable.getRequestedItems();

            for (int i = 0; i < requestedItems.size(); i++)
            {
                final ItemStack requested = requestedItems.get(i);
                if (!requested.isEmpty())
                {
                    final ItemStack output = requested.copy();
                    if (requestedItems.size() == 1 && requestedCount > 0)
                    {
                        output.setCount(requestedCount);
                    }
                    outputs.add(output);
                }
            }

            if (!outputs.isEmpty())
            {
                return outputs;
            }
        }

        final List<ItemStack> displayStacks = request.getDisplayStacks();
        if (displayStacks.isEmpty())
        {
            return List.of();
        }

        final List<ItemStack> outputs = new ArrayList<>();
        for (final ItemStack displayStack : displayStacks)
        {
            if (!displayStack.isEmpty())
            {
                outputs.add(displayStack.copy());
            }
        }

        return outputs;
    }

    private boolean hasCraftingContents(final List<ItemStack> stacks)
    {
        final Map<ItemStorage, Integer> required = new HashMap<>();

        for (final ItemStack stack : stacks)
        {
            if (!stack.isEmpty())
            {
                required.merge(new ItemStorage(stack.copy()), stack.getCount(), Integer::sum);
            }
        }

        for (final Map.Entry<ItemStorage, Integer> entry : required.entrySet())
        {
            if (getAvailableIngredientCount(entry.getKey()) < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    private int getAvailableIngredientCount(final ItemStorage ingredient)
    {
        int available = getStockCount(warehouseStock, ingredient);
        if (moduleView.shouldIncludePlayerInventory())
        {
            available += getStockCount(playerStock, ingredient);
        }

        return available;
    }

    private int getStockCount(final Map<ItemStorage, Integer> stock, final ItemStorage ingredient)
    {
        return stock.entrySet().stream()
            .filter(candidate -> Objects.equals(candidate.getKey(), ingredient))
            .mapToInt(Map.Entry::getValue)
            .sum();
    }

    private @Nullable WorkshopRecipe getSelectedRecipe()
    {
        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= matchingRecipes.size())
        {
            return null;
        }

        return matchingRecipes.get(selectedRecipeIndex);
    }

    private @Nullable WorkshopRecipe getActiveRecipe()
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        final Level level = Minecraft.getInstance().level;
        if ((selectedRecipe != null && selectedRecipe.kind() == RecipeKind.DOMUM) || level == null)
        {
            return selectedRecipe;
        }

        final RecipeHolder<CraftingRecipe> currentRecipe = getCurrentGridRecipe();
        return currentRecipe == null ? selectedRecipe : WorkshopRecipe.crafting(currentRecipe, level);
    }

    private void clearRequestSelection()
    {
        this.selectedRequest = null;
        this.requestOutputs.clear();
    }

    private void clearJeiSelection(final boolean clearGrid)
    {
        this.matchingRecipes.clear();
        this.selectedRecipeIndex = -1;
        this.jeiSearchOutput = ItemStack.EMPTY;

        if (clearGrid)
        {
            for (int i = 0; i < GRID_SIZE; i++)
            {
                slotMatches.get(i).clear();
                selectedGrid.set(i, ItemStack.EMPTY);
                displayGrid.set(i, ItemStack.EMPTY);
                slotStates.set(i, SlotState.EMPTY);
            }
        }
    }

    private void updateRequestDetails()
    {
        updateCraftButtons();

        if (selectedRequest == null)
        {
            outputLabel.setText(jeiSearchOutput.isEmpty()
                ? Component.translatable("com.warehouseworkshop.core.gui.workshop.request_item")
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.search_item"));

            requestLabel.setText(jeiSearchOutput.isEmpty()
                ? Component.translatable("com.warehouseworkshop.core.gui.workshop.request.none")
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.jei_search_item"));

            requestIcon.setItem(jeiSearchOutput);
        }
        else
        {
            outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.request_item"));

            requestLabel.setText(jeiSearchOutput.isEmpty()
                ? selectedRequest.getShortDisplayString().copy()
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.jei_search_item"));

            requestIcon.setItem(getRequestPreviewOutput());
        }

        // slotLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.slot", activeSlot + 1));

        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        final WorkshopRecipe activeRecipe = getActiveRecipe();
        if (activeRecipe == null)
        {
            recipeLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.none"));
            outputIcon.setItem(ItemStack.EMPTY);
            setStatusText(selectedGrid.stream().allMatch(ItemStack::isEmpty)
                ? Component.translatable("com.warehouseworkshop.core.gui.workshop.status.selectrequest")
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"));
            return;
        }

        final ItemStack currentGridOutput = getCurrentGridOutput();
        recipeLabel.setText(selectedRecipe != null
            ? Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.index", selectedRecipeIndex + 1, matchingRecipes.size())
            : Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.manual"));
        outputIcon.setItem(currentGridOutput);
        if (!isGridCraftable())
        {
            setStatusText(getMissingStatusText());
            return;
        }

        if (doesCurrentGridMatchRequest())
        {
            setStatusText(getReadyStatusText());
            return;
        }

        setStatusText(
            Component.translatable("com.warehouseworkshop.core.gui.workshop.status.validmismatch"),
            STATUS_MISMATCH_TEXT_COLOR);
    }

    private void updateCraftButtons()
    {
        final boolean craftable = isGridCraftable();
        craftButton.setEnabled(craftable);
        craftAllButton.setEnabled(craftable);
    }

    private Component getReadyStatusText()
    {
        return Component.translatable(moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.status.ready.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.ready");
    }

    private Component getMissingStatusText()
    {
        return Component.translatable(moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.status.missing.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.missing");
    }

    private void setStatusText(final Component status)
    {
        setStatusText(status, STATUS_TEXT_COLOR);
    }
    
    private void setStatusText(final Component status, final int color)
    {
        statusLabel.setTextColor(color);
        statusLabel.setText(status);
    }

    private void updateGridIcons()
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        final boolean domumRecipe = selectedRecipe != null && selectedRecipe.kind() == RecipeKind.DOMUM;
        for (int i = 0; i < GRID_SIZE; i++)
        {
            gridIcons.get(i).setItem(displayGrid.get(i));
            updateGridBackground(i);
            gridButtons.get(i).setEnabled(!domumRecipe || i == DOMUM_FIRST_SLOT || i == DOMUM_SECOND_SLOT);
        }

        updateRequestDetails();
    }

    private void updateGridBackground(final int slot)
    {
        final Gradient background = gridBackgrounds.get(slot);
        switch (slotStates.get(slot))
        {
            case PRESENT:
                background.setGradientStart(PRESENT_SLOT_RED, PRESENT_SLOT_GREEN, PRESENT_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.setGradientEnd(PRESENT_SLOT_RED, PRESENT_SLOT_GREEN, PRESENT_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.show();
                break;
            case MISSING:
                background.setGradientStart(MISSING_SLOT_RED, MISSING_SLOT_GREEN, MISSING_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.setGradientEnd(MISSING_SLOT_RED, MISSING_SLOT_GREEN, MISSING_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.show();
                break;
            default:
                background.hide();
                break;
        }
    }
}
