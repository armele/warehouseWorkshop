package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.network.RequestWorkshopSettingsMessage;
import com.deathfrog.warehouseworkshop.core.network.SetWorkshopIncludePlayerInventoryMessage;
import com.deathfrog.warehouseworkshop.core.network.SetWorkshopOutputTargetMessage;
import com.deathfrog.warehouseworkshop.core.network.WorkshopCraftMessage;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Gradient;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
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
import net.minecraft.tags.TagKey;
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
    private static final String DEFAULT_CRAFT_AMOUNT = "1";

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
    private final TextField craftAmountInput;

    private final Map<ItemStorage, Integer> warehouseStock = new HashMap<>();
    private final Map<ItemStorage, Integer> playerStock = new HashMap<>();
    private final List<ItemStack> requestOutputs = new ArrayList<>();
    private final List<WorkshopRecipe> matchingRecipes = new ArrayList<>();

    private @Nullable IRequest<?> selectedRequest;
    private ItemStack jeiSearchOutput = ItemStack.EMPTY;
    private int selectedRecipeIndex = -1;
    private boolean playerSettingsLoaded;

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
        this.craftAmountInput = window.findPaneOfTypeByID("craftAmount", TextField.class);
        this.craftAmountInput.setFilter(new TextField.Filter()
        {
            @Override
            public String filter(final String input)
            {
                return sanitizeCraftAmountText(input);
            }

            @Override
            public boolean isAllowedCharacter(final char c)
            {
                return Character.isDigit(c);
            }
        });
        this.craftAmountInput.setMaxTextLength(9);
        this.craftAmountInput.setHandler(input -> updateCraftButtons());
        this.craftAmountInput.setText(DEFAULT_CRAFT_AMOUNT);

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

        PaneBuilders.tooltipBuilder()
                .append(Component.translatable("com.warehouseworkshop.core.gui.workshop.craft.tooltip"))
                .hoverPane(craftButton)
                .build();

        PaneBuilders.tooltipBuilder()
                .append(Component.translatable("com.warehouseworkshop.core.gui.workshop.craftall.tooltip"))
                .hoverPane(craftAllButton)
                .build();
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

        @SuppressWarnings("null")
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
        playerSettingsLoaded = false;
        new RequestWorkshopSettingsMessage(buildingView.getPosition()).sendToServer();
        refreshWarehouseStock();
        refreshPlayerInventoryStock();
        updateOutputTargetButton();
        updateIncludePlayerInventoryButton();
        updateRequestDetails();
        updateGridIcons();
    }

    public void refreshPlayerSettings(final BlockPos buildingPos, final OutputTarget outputTarget, final boolean includePlayerInventory)
    {
        if (!Objects.equals(buildingView.getPosition(), buildingPos))
        {
            return;
        }

        final boolean ingredientSourceChanged = !playerSettingsLoaded || moduleView.shouldIncludePlayerInventory() != includePlayerInventory;
        playerSettingsLoaded = true;
        moduleView.setOutputTarget(outputTarget);
        moduleView.setIncludePlayerInventory(includePlayerInventory);
        updateOutputTargetButton();
        updateIncludePlayerInventoryButton();
        if (ingredientSourceChanged)
        {
            applySelectedRecipe();
        }
    }

    private void toggleOutputTarget()
    {
        if (!playerSettingsLoaded)
        {
            return;
        }

        final OutputTarget outputTarget = moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? OutputTarget.PLAYER_INVENTORY
            : OutputTarget.WAREHOUSE_INVENTORY;
        moduleView.setOutputTarget(outputTarget);
        new SetWorkshopOutputTargetMessage(buildingView.getPosition(), outputTarget.getId()).sendToServer();
        updateOutputTargetButton();
    }

    private void toggleIncludePlayerInventory()
    {
        if (!playerSettingsLoaded)
        {
            return;
        }

        final boolean includePlayerInventory = !moduleView.shouldIncludePlayerInventory();
        moduleView.setIncludePlayerInventory(includePlayerInventory);
        new SetWorkshopIncludePlayerInventoryMessage(buildingView.getPosition(), includePlayerInventory).sendToServer();
        refreshPlayerInventoryStock();
        updateIncludePlayerInventoryButton();
        applySelectedRecipe();
    }

    private void updateOutputTargetButton()
    {
        outputTargetButton.setEnabled(playerSettingsLoaded);
        outputTargetButton.setText(Component.translatable(!playerSettingsLoaded
            ? "com.warehouseworkshop.core.gui.workshop.settings.loading"
            : moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? "com.warehouseworkshop.core.gui.workshop.output_target.warehouse"
            : "com.warehouseworkshop.core.gui.workshop.output_target.inventory"));
    }

    private void updateIncludePlayerInventoryButton()
    {
        includePlayerInventoryButton.setEnabled(playerSettingsLoaded);
        includePlayerInventoryButton.setText(Component.translatable(!playerSettingsLoaded
            ? "com.warehouseworkshop.core.gui.workshop.settings.loading"
            : moduleView.shouldIncludePlayerInventory()
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
        populateCraftAmountFromRequest();
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
            updateGridIcons();
            return;
        }

        if (selectedRecipe.kind() == RecipeKind.DOMUM)
        {
            applySelectedDomumRecipe(selectedRecipe);
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
            final int clickThroughAmount = getClickThroughCraftAmount(slot);
            selectJeiOutput(displayGrid.get(slot));
            if (clickThroughAmount > 0)
            {
                craftAmountInput.setText(Integer.toString(clickThroughAmount));
            }
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
            setStatusText(hasValidCraftAmount()
                ? getMissingStatusText()
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid_count"));
            return;
        }

        performCraft(craftCount);
    }

    private void performCraft(final int craftCount)
    {
        if (!playerSettingsLoaded)
        {
            setStatusText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.settings_loading"));
            return;
        }

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

    /**
     * Updates the crafting contents of the workshop window with the given snapshots of the warehouse and player inventory.
     * This method is only effective if the given building position matches the current position of the workshop window.
     * If the positions do not match, this method does nothing.
     * @param buildingPos The position of the building that the workshop window is currently viewing.
     * @param warehouseStockSnapshot The snapshot of the warehouse inventory.
     * @param playerStockSnapshot The snapshot of the player inventory.
     */
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

    /**
     * Applies a snapshot of the warehouse and player stock to the current stock.
     * This will clear the current stock and replace it with the items from the snapshot.
     * The count of each item in the snapshot will be added to the count of the same item in the stock.
     * If an item is not present in the snapshot, it will be removed from the stock.
     * @param stock the map of items to their counts in the stock
     * @param snapshot the list of items to be applied to the stock
     */
    @SuppressWarnings("null")
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

    /**
     * Calculates the maximum number of crafts that can be performed using the currently active recipe and current stock levels.
     * The calculation takes into account the number of crafts requested by the player, the number of crafts supported by the recipe,
     * and the number of crafts that can be supported by the current stock levels.
     * 
     * @return The maximum number of crafts that can be performed.
     */
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
        final int requestedOutputCount = getCraftAmount();
        if (requestedOutputCount <= 0)
        {
            return 0;
        }

        final int requestedCrafts = getRequestedCraftCount(recipeOutput, requestedOutputCount);
        final int supportedCrafts = getSupportedCraftCount(requestedCrafts);
        return requestedCrafts <= 0 ? 0 : Math.min(requestedCrafts, supportedCrafts);
    }

    /**
     * Calculates the number of crafts that need to be performed on the currently selected recipe in order to fulfill the given requested output count.
     * The calculation takes into account the number of outputs per craft of the currently selected recipe.
     * If the currently selected recipe is invalid, or the requested output count is 0 or less, 0 is returned.
     * @param recipeOutput the output item stack of the currently selected recipe
     * @param requestedOutputCount the number of outputs requested by the player
     * @return the number of crafts that need to be performed on the currently selected recipe in order to fulfill the given requested output count
     */
    private int getRequestedCraftCount(final ItemStack recipeOutput, final int requestedOutputCount)
    {
        if (recipeOutput.isEmpty() || requestedOutputCount <= 0)
        {
            return 0;
        }

        final int outputPerCraft = Math.max(1, recipeOutput.getCount());
        return (requestedOutputCount + outputPerCraft - 1) / outputPerCraft;
    }

    /**
     * Calculates the number of crafts that need to be performed on the currently selected recipe in order to fulfill the click-through on the given slot.
     * The calculation takes into account the number of required ingredients for the click-through, and the number of outputs per craft of the currently selected recipe.
     * If the currently selected recipe is invalid, or the required ingredient count is 0 or less, 0 is returned.
     * 
     * @param slot the slot to calculate the click-through craft amount for
     * @return the number of crafts that need to be performed on the currently selected recipe in order to fulfill the click-through on the given slot
     */
    private int getClickThroughCraftAmount(final int slot)
    {
        final int requiredIngredientCount = getRequiredIngredientCountForClickThrough(slot);
        if (requiredIngredientCount <= 0)
        {
            return 0;
        }

        final WorkshopRecipe childRecipe = getSelectedRecipe();
        if (childRecipe == null)
        {
            return requiredIngredientCount;
        }

        final int outputPerCraft = Math.max(1, childRecipe.output().getCount());
        return (requiredIngredientCount + outputPerCraft - 1) / outputPerCraft;
    }

    /**
     * Calculates the total number of ingredients required to fulfill the given slot for the given parent craft.
     * If the parent craft is invalid, or the slot is not applicable to the parent craft, 0 is returned.
     * 
     * @param slot the slot to calculate the required ingredient count for
     * @return the total number of ingredients required to fulfill the given slot for the given parent craft
     */
    private int getRequiredIngredientCountForClickThrough(final int slot)
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        if (selectedRecipe == null)
        {
            return 0;
        }

        final ItemStack currentOutput = selectedRecipe.output().copy();
        final int parentOutputAmount = getCraftAmount();
        final int parentCraftCount = getRequestedCraftCount(currentOutput, parentOutputAmount);
        if (parentCraftCount <= 0)
        {
            return 0;
        }

        final int ingredientCountPerCraft = getIngredientCountPerCraft(selectedRecipe, slot);
        if (ingredientCountPerCraft <= 0)
        {
            return 0;
        }

        return ingredientCountPerCraft * parentCraftCount;
    }

    /**
     * Returns the total number of items requested in the currently selected request that matches the given recipe output.
     * If the recipe output is empty, or there are no matching requested items, 0 is returned.
     * @param recipeOutput the recipe output to check against the currently selected request
     * @return the total number of items requested in the currently selected request that matches the given recipe output
     */
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

    /**
     * Calculates the maximum number of crafts that can be performed using the currently active recipe and current stock levels.
     * The calculation takes into account the number of crafts supported by the recipe, and the number of crafts that can be
     * supported by the current stock levels.
     * 
     * @return The maximum number of crafts that can be performed.
     */
    @SuppressWarnings("null")
    private int getSupportedCraftCount(final int requestedCrafts)
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

        final WorkshopRecipe activeRecipe = getActiveRecipe();
        final Level level = Minecraft.getInstance().level;
        if (activeRecipe == null || activeRecipe.kind() != RecipeKind.CRAFTING || level == null)
        {
            return getBasicSupportedCraftCount(requiredPerCraft);
        }

        final RecipeHolder<CraftingRecipe> currentRecipe = getCurrentGridRecipe();
        if (currentRecipe == null)
        {
            return getBasicSupportedCraftCount(requiredPerCraft);
        }

        final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));
        if (input == null || input.isEmpty())
        {
            return 0;
        }

        final List<ItemStack> remainingItems = currentRecipe.value().getRemainingItems(input);
        final Map<ItemStorage, Integer> remainingWarehouseStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        final boolean includePlayerInventory = moduleView.shouldIncludePlayerInventory();
        final boolean outputToWarehouse = moduleView.getOutputTarget().isWarehouse();
        final int craftLimit = requestedCrafts > 0 ? requestedCrafts : Integer.MAX_VALUE;

        int supportedCrafts = 0;
        while (supportedCrafts < craftLimit && canConsumeIngredients(requiredPerCraft, remainingWarehouseStock, remainingPlayerStock, includePlayerInventory))
        {
            final Map<ItemStorage, Integer> previousUsableStock = requestedCrafts > 0
                ? Map.of()
                : snapshotUsableIngredientStock(requiredPerCraft, remainingWarehouseStock, remainingPlayerStock, includePlayerInventory);

            consumeIngredients(requiredPerCraft, remainingWarehouseStock, remainingPlayerStock, includePlayerInventory);
            addReturnedItems(remainingItems, remainingWarehouseStock, remainingPlayerStock, outputToWarehouse);
            supportedCrafts++;

            if (requestedCrafts <= 0
                && previousUsableStock.equals(snapshotUsableIngredientStock(
                    requiredPerCraft,
                    remainingWarehouseStock,
                    remainingPlayerStock,
                    includePlayerInventory)))
            {
                break;
            }
        }

        return supportedCrafts;
    }

    /**
     * Calculates the maximum number of crafts that can be performed using the given required ingredients and current stock levels.
     * The calculation takes into account the number of crafts supported by the recipe, and the number of crafts that can be
     * supported by the current stock levels.
     * 
     * @param requiredPerCraft the map of required ingredients to their counts per craft
     * @return The maximum number of crafts that can be performed.
     */
    private int getBasicSupportedCraftCount(final Map<ItemStorage, Integer> requiredPerCraft)
    {
        int supportedCrafts = Integer.MAX_VALUE;
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            supportedCrafts = Math.min(supportedCrafts, getAvailableIngredientCount(entry.getKey()) / entry.getValue());
        }

        return supportedCrafts == Integer.MAX_VALUE ? 0 : supportedCrafts;
    }

    /**
     * Checks if all the ingredients in the given map are available in the given stocks.
     * The check takes into account the number of crafts requested by the player, the number of crafts supported by the recipe,
     * and the number of crafts that can be supported by the current stock levels.
     * 
     * @param requiredPerCraft the map of required ingredients to their counts per craft
     * @param remainingWarehouseStock the map of items to their counts in the remaining warehouse stock
     * @param remainingPlayerStock the map of items to their counts in the remaining player stock
     * @param includePlayerInventory whether the count of each ingredient in the remaining player stock should be included
     * @return true if all ingredients are available, false otherwise
     */
    private boolean canConsumeIngredients(
        final Map<ItemStorage, Integer> requiredPerCraft,
        final Map<ItemStorage, Integer> remainingWarehouseStock,
        final Map<ItemStorage, Integer> remainingPlayerStock,
        final boolean includePlayerInventory)
    {
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            if (getStockCount(remainingWarehouseStock, entry.getKey())
                + (includePlayerInventory ? getStockCount(remainingPlayerStock, entry.getKey()) : 0) < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Consumes the given amount of ingredients from the given stock maps.
     * If includePlayerInventory is true, both the warehouse and player inventory are checked.
     * 
     * @param requiredPerCraft the map of required ingredients to their counts per craft
     * @param remainingWarehouseStock the map of items to their counts in the remaining warehouse stock
     * @param remainingPlayerStock the map of items to their counts in the remaining player stock
     * @param includePlayerInventory whether to include the player inventory in the consumption
     */
    private void consumeIngredients(
        final Map<ItemStorage, Integer> requiredPerCraft,
        final Map<ItemStorage, Integer> remainingWarehouseStock,
        final Map<ItemStorage, Integer> remainingPlayerStock,
        final boolean includePlayerInventory)
    {
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            int remaining = decrementStock(remainingWarehouseStock, entry.getKey(), entry.getValue());
            if (remaining > 0 && includePlayerInventory)
            {
                decrementStock(remainingPlayerStock, entry.getKey(), remaining);
            }
        }
    }

    private int decrementStock(final Map<ItemStorage, Integer> stock, final ItemStorage ingredient, final int amount)
    {
        int remaining = amount;
        for (final Map.Entry<ItemStorage, Integer> entry : stock.entrySet())
        {
            if (!Objects.equals(entry.getKey(), ingredient))
            {
                continue;
            }

            final int removed = Math.min(entry.getValue(), remaining);
            final int updated = entry.getValue() - removed;
            if (updated > 0)
            {
                entry.setValue(updated);
            }
            else
            {
                entry.setValue(0);
            }

            remaining -= removed;
            if (remaining <= 0)
            {
                break;
            }
        }

        return remaining;
    }

    /**
     * Adds the returned items to the target stock.
     * If the output target is set to the warehouse, the returned items are added to the remaining warehouse stock.
     * If the output target is set to the player inventory, the returned items are added to the remaining player stock.
     * The target stock is updated with the number of items of each type that were returned.
     * @param returnedItems the list of items that were returned
     * @param remainingWarehouseStock the map of items to their counts in the remaining warehouse stock
     * @param remainingPlayerStock the map of items to their counts in the remaining player stock
     * @param outputToWarehouse whether the output target is the warehouse
     */
    @SuppressWarnings("null")
    private void addReturnedItems(
        final List<ItemStack> returnedItems,
        final Map<ItemStorage, Integer> remainingWarehouseStock,
        final Map<ItemStorage, Integer> remainingPlayerStock,
        final boolean outputToWarehouse)
    {
        final Map<ItemStorage, Integer> targetStock = outputToWarehouse ? remainingWarehouseStock : remainingPlayerStock;
        for (final ItemStack returned : returnedItems)
        {
            if (!returned.isEmpty())
            {
                targetStock.merge(new ItemStorage(returned.copy()), returned.getCount(), Integer::sum);
            }
        }
    }

    /**
     * Returns a snapshot of the usable ingredients in the given stocks.
     * The snapshot is a map of each required ingredient to the total count of that ingredient in the given stocks.
     * If includePlayerInventory is true, the count of each ingredient in the remaining player stock is added to the count in the remaining warehouse stock.
     * @param requiredPerCraft the map of required ingredients to their counts per craft
     * @param remainingWarehouseStock the map of items to their counts in the remaining warehouse stock
     * @param remainingPlayerStock the map of items to their counts in the remaining player stock
     * @param includePlayerInventory whether the count of each ingredient in the remaining player stock should be included
     * @return a snapshot of the usable ingredients in the given stocks
     */
    private Map<ItemStorage, Integer> snapshotUsableIngredientStock(
        final Map<ItemStorage, Integer> requiredPerCraft,
        final Map<ItemStorage, Integer> remainingWarehouseStock,
        final Map<ItemStorage, Integer> remainingPlayerStock,
        final boolean includePlayerInventory)
    {
        final Map<ItemStorage, Integer> snapshot = new HashMap<>();
        for (final ItemStorage ingredient : requiredPerCraft.keySet())
        {
            snapshot.put(
                ingredient,
                getStockCount(remainingWarehouseStock, ingredient)
                    + (includePlayerInventory ? getStockCount(remainingPlayerStock, ingredient) : 0));
        }

        return snapshot;
    }

    private boolean isGridCraftable()
    {
        return getActiveRecipe() != null && !getCurrentGridOutput().isEmpty() && hasCraftingContents(List.copyOf(selectedGrid));
    }

    /**
     * Retrieves the recipe associated with the currently selected grid items, or null if there is no valid recipe.
     * 
     * @return The recipe associated with the currently selected grid items, or null if there is no valid recipe.
     */
    @SuppressWarnings("null")
    private @Nullable RecipeHolder<CraftingRecipe> getCurrentGridRecipe()
    {
        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return null;
        }

        final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));

        if (input == null || input.isEmpty())
        {
            return null;
        }

        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
    }

    /**
     * Retrieves the output item stack associated with the currently selected grid items, using the currently selected recipe.
     * If the currently selected recipe is a DOMUM recipe, then the output is determined by assembling the DOMUM output based on the currently selected grid items.
     * If the currently selected recipe is a CRAFTING recipe, then the output is determined by retrieving the output item stack associated with the currently selected grid items using the currently selected recipe.
     * If there is no valid recipe associated with the currently selected grid items, then the output is an empty item stack.
     * 
     * @return The output item stack associated with the currently selected grid items, or an empty item stack if there is no valid recipe associated with the currently selected grid items.
     */
    @SuppressWarnings("null")
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

    /**
     * Determines whether the currently selected grid items match the selected request or JEI search output.
     * 
     * @return true if the currently selected grid items match the selected request or JEI search output, false otherwise.
     */
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

    /**
     * Retrieves a preview of the output item stack associated with the currently selected request.
     * If the currently selected request is a recipe, then the output is determined by retrieving the output item stack associated with the currently selected recipe, and then setting the count of the output item stack to the requested output count.
     * If the currently selected request is not a recipe, then the output is determined by retrieving the first output item stack associated with the currently selected request.
     * If there is no valid request associated with the currently selected request, then the output is an empty item stack.
     * 
     * @return A preview of the output item stack associated with the currently selected request, or an empty item stack if there is no valid request associated with the currently selected request.
     */
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

    /**
     * Refreshes the warehouse stock by iterating over all container blocks and merging their contents into the warehouse stock.
     * This is called when the player opens the workshop window, and when the player closes the workshop window.
     */
    @SuppressWarnings("null")
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
            if (pos == null)
            {
                continue;
            }

            final BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof final TileEntityRack rack)
            {
                rack.getAllContent().forEach((storage, amount) -> warehouseStock.merge(storage, amount, Integer::sum));
            }
        }
    }

    /**
     * Refreshes the player's inventory stock by iterating over all items in the player's inventory and merging their contents into the player stock.
     * This is called when the player opens the workshop window, and when the player closes the workshop window.
     */
    @SuppressWarnings("null")
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

    /**
     * Finds all recipes that match the given requested outputs. If requireWarehouseContents is true, then only recipes that can be crafted with the items in the warehouse will be returned.
     * 
     * @param requestedOutputs the list of requested output item stacks
     * @param requireWarehouseContents whether to only return recipes that can be crafted with the items in the warehouse
     * @return a list of matching recipes, sorted by their hover name and then by their kind and ID
     */
    @SuppressWarnings("null")
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

    private int getIngredientCountPerCraft(final WorkshopRecipe recipe, final int slot)
    {
        if (recipe.kind() == RecipeKind.DOMUM)
        {
            final List<DomumSlotRequirement> requirements = getDomumRequirements(recipe);
            final int componentIndex = slot == DOMUM_FIRST_SLOT ? 0 : slot == DOMUM_SECOND_SLOT ? 1 : -1;
            if (componentIndex < 0 || componentIndex >= requirements.size())
            {
                return 0;
            }

            final Block targetMaterial = requirements.get(componentIndex).material();
            int matches = 0;
            for (final DomumSlotRequirement requirement : requirements)
            {
                if (requirement.material() == targetMaterial)
                {
                    matches++;
                }
            }

            return matches;
        }

        if (recipe.craftingRecipe() == null)
        {
            return 0;
        }

        final List<Ingredient> slottedIngredients = buildSlottedIngredients(recipe.craftingRecipe().value());
        if (slot < 0 || slot >= slottedIngredients.size())
        {
            return 0;
        }

        final ItemStack targetDisplayStack = getIngredientDisplayStack(slottedIngredients.get(slot));
        if (targetDisplayStack.isEmpty())
        {
            return 0;
        }

        int matches = 0;
        for (final Ingredient ingredient : slottedIngredients)
        {
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(targetDisplayStack, getIngredientDisplayStack(ingredient), false, true))
            {
                matches++;
            }
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

    /**
     * Gets a display stack from an ingredient, used for displaying the ingredient in the JEI GUI.
     * The display stack is a copy of the first non-empty stack in the ingredient's items list, with its count set to 1.
     * If the ingredient has no non-empty stacks, an empty ItemStack is returned.
     *
     * @param ingredient the ingredient to get a display stack for
     * @return a display stack for the ingredient
     */
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

    /**
     * Gets a display stack for a domum slot requirement.
     * If the requirement's material is null, an empty stack is returned.
     * Otherwise, a stack with the material and count 1 is returned.
     *
     * @param requirement the requirement to get the display stack for
     * @return the display stack
     */
    private ItemStack getDomumRequirementDisplayStack(final DomumSlotRequirement requirement)
    {
        final Block material = requirement.material();

        if (material == null)
        {
            return ItemStack.EMPTY;
        }

        final ItemStack displayStack = new ItemStack(material);
        
        if (!displayStack.isEmpty())
        {
            displayStack.setCount(1);
        }
        return displayStack;
    }

    /**
     * Builds an output stack for a domum recipe based on the given requested output stack.
     * The output stack is a copy of the requested stack with the count set to the maximum of the component count and the recipe count.
     * If the requested stack is empty or does not match the domum recipe's result, an empty stack is returned.
     * If the domum recipe has no components or more than two components, an empty stack is returned.
     * Otherwise, the output stack is updated with the default textures of the components and returned.
     * 
     * @param recipe the domum recipe to build the output stack for
     * @param requested the requested output stack
     * @param level the level to access the recipe's result
     * @return the built output stack
     */
    private ItemStack buildDomumOutputForRequest(final ArchitectsCutterRecipe recipe, final ItemStack requested, final Level level)
    {
        ItemStack recipeResult = recipe.getResultItem(level.registryAccess());

        if (recipeResult == null || recipeResult.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (requested.isEmpty() || !matchesDomumRecipeResult(recipeResult, requested))
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

    /**
     * Checks if the given recipe result matches the given requested output stack.
     * The stacks are considered to match if they have the same item and block state.
     * If either of the stacks has no block state, the stacks are considered to match.
     * @param recipeResult the recipe result to check
     * @param requested the requested output stack to check against
     * @return true if the stacks match, false otherwise
     */
    @SuppressWarnings("null")
    private boolean matchesDomumRecipeResult(final @Nonnull ItemStack recipeResult, final @Nonnull ItemStack requested)
    {
        if (!ItemStack.isSameItem(recipeResult, requested))
        {
            return false;
        }

        final BlockItemStateProperties recipeState = recipeResult.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        final BlockItemStateProperties requestedState = requested.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        return recipeState.isEmpty() && requestedState.isEmpty() || recipeState.equals(requestedState);
    }

    /**
     * Retrieves the list of domum slot requirements for the given workshop recipe.
     * 
     * If the recipe has no domum recipe, an empty list is returned.
     * If the recipe has no components, an empty list is returned.
     * If the recipe has more than two components, an empty list is returned.
     * 
     * The list of requirements is generated by iterating over the components and
     * retrieving the material and valid skins for each component. If the material
     * does not have a valid block state in the given skins, the list is immediately
     * returned as empty.
     * 
     * @param recipe the workshop recipe to retrieve the requirements for
     * @return the list of domum slot requirements for the given recipe
     */
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
            TagKey<Block> validSkins = component.getValidSkins();

            if (validSkins == null) continue;

            if (!material.defaultBlockState().is(validSkins))
            {
                return List.of();
            }
            
            requirements.add(new DomumSlotRequirement(material));
        }

        return requirements;
    }

    /**
     * Retrieves the list of components for the given domum recipe.
     * 
     * If the given recipe's block is not an instance of IMateriallyTexturedBlock, an empty list is returned.
     * 
     * @param recipe the domum recipe to retrieve the components for
     * @return the list of components for the given domum recipe
     */
    private List<IMateriallyTexturedBlockComponent> getDomumComponents(final ArchitectsCutterRecipe recipe)
    {
        if (!(recipe.getBlock() instanceof final IMateriallyTexturedBlock texturedBlock))
        {
            return List.of();
        }

        return new ArrayList<>(texturedBlock.getComponents());
    }

    /**
     * Assembles a domum output stack from the given recipe.
     * The assembled stack is a copy of the recipe's output stack with the count set to the maximum of the component count and the recipe count.
     * If the recipe has no components, an empty stack is returned.
     * If the domum recipe has no components or more than two components, an empty stack is returned.
     * If the selected grid does not contain the required components, an empty stack is returned.
     * Otherwise, the output stack is updated with the default textures of the components and returned.
     * 
     * @param recipe the domum recipe to assemble the output stack for
     * @return the assembled output stack
     */
    @SuppressWarnings("null")
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

    /**
     * Maps a component index (0 or 1) to a grid slot (DOMUM_FIRST_SLOT or DOMUM_SECOND_SLOT)
     * for a Domum recipe.
     *
     * @param component the component index (0 or 1)
     * @return the corresponding grid slot (DOMUM_FIRST_SLOT or DOMUM_SECOND_SLOT)
     */
    private int domumGridSlot(final int component)
    {
        return component == 0 ? DOMUM_FIRST_SLOT : DOMUM_SECOND_SLOT;
    }

    /**
     * Extracts the requested outputs from the given request.
     * 
     * If the request's concrete deliverable has requested items, these items are added to the output list.
     * If the request's concrete deliverable is an instance of IDeliverable and has a count greater than 0, the count of the first requested item is set to the count.
     * If the request has display stacks, these stacks are added to the output list.
     * 
     * @param request the request to extract the requested outputs from
     * @return the list of requested outputs
     */
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

    /**
     * Checks if the given stacks contain enough items to fulfill the crafting request.
     * The method will return false if any of the required items are not present in the stacks, or if the count of the required items is less than the requested count.
     * 
     * @param stacks the list of stacks to check
     * @return true if the given stacks contain enough items, false otherwise
     */
    @SuppressWarnings("null")
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

    /**
     * Gets the currently active recipe.
     * 
     * If the currently selected recipe is a DOMUM recipe, it is returned directly.
     * If the currently selected recipe is null or not a DOMUM recipe, the method attempts to get the current crafting recipe from the crafting grid.
     * If the crafting recipe is null, the selected recipe is returned. Otherwise, the crafting recipe is converted to a WorkshopRecipe and returned.
     * 
     * @return the currently active recipe, or null if no recipe is active.
     */
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

    /**
     * Clears the current JEI selection and optionally clears the crafting grid.
     *
     * @param clearGrid whether or not to clear the crafting grid
     */
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

    /**
     * Updates the request details text and icon based on the current selected request and JEI search output.
     * If the selected request is null, it will display the JEI search output or the "request item" text.
     * If the selected request is not null, it will display the selected request's short display string and the
     * request preview output.
     * Additionally, it will update the status text and color based on the current grid's craftability and
     * whether it matches the selected request.
     */
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
        if (!playerSettingsLoaded)
        {
            recipeLabel.setText(selectedRecipe != null
                ? Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.index", selectedRecipeIndex + 1, matchingRecipes.size())
                : Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.manual"));
            outputIcon.setItem(activeRecipe == null ? ItemStack.EMPTY : getCurrentGridOutput());
            setStatusText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.settings_loading"));
            return;
        }

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
        final boolean craftable = playerSettingsLoaded && isGridCraftable();
        craftButton.setEnabled(craftable);
        craftAllButton.setEnabled(craftable && hasValidCraftAmount());
    }

    private void populateCraftAmountFromRequest()
    {
        final ItemStack previewOutput = getRequestPreviewOutput();
        final int requestedCount = previewOutput.isEmpty() ? 0 : previewOutput.getCount();
        craftAmountInput.setText(requestedCount > 0 ? Integer.toString(requestedCount) : DEFAULT_CRAFT_AMOUNT);
    }

    private boolean hasValidCraftAmount()
    {
        return getCraftAmount() > 0;
    }

    /**
     * Gets the craft amount entered by the user in the craft amount input field.
     * If the input is empty, or cannot be parsed to an integer, 0 is returned.
     * If the input is invalid, Integer.MAX_VALUE is returned.
     * 
     * @return the craft amount entered by the user
     */
    private int getCraftAmount()
    {
        final String text = sanitizeCraftAmountText(craftAmountInput.getText());
        if (text.isEmpty())
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(text);
        }
        catch (final NumberFormatException ignored)
        {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Sanitizes the given input string by removing all non-digit characters.
     * If the input string is null or empty, an empty string is returned.
     * 
     * @param input the input string to be sanitized
     * @return the sanitized string containing only digits
     */
    private static String sanitizeCraftAmountText(final @Nullable String input)
    {
        if (input == null || input.isEmpty())
        {
            return "";
        }

        final StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++)
        {
            final char c = input.charAt(i);
            if (Character.isDigit(c))
            {
                sanitized.append(c);
            }
        }

        return sanitized.toString();
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
    
    /**
     * Sets the status label's text and color.
     * @param status The Component to display in the status label.
     * @param color The color to set the status label's text to.
     */
    private void setStatusText(final Component status, final int color)
    {
        statusLabel.setTextColor(color);
        statusLabel.setText(status);
    }

    /**
     * Updates the grid icons, backgrounds, and buttons based on the current recipe and grid contents.
     * If the recipe is a Domum recipe, it enables the first two slots and disables the rest.
     */
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

    /**
     * Updates the background of a grid slot to indicate the presence or absence of the
     * ingredient in the warehouse.
     *
     * @param slot the index of the grid slot to update
     */
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
