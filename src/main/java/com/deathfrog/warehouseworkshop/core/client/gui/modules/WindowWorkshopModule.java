package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.compatibility.recipes.OptionalRecipeSupport;
import com.deathfrog.warehouseworkshop.core.compatibility.recipes.OptionalRecipeSupport.CraftingSlotRequirement;
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
import com.ldtteam.blockui.controls.Tooltip;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final int LIMITED_SLOT_RED = 220;
    private static final int LIMITED_SLOT_GREEN = 180;
    private static final int LIMITED_SLOT_BLUE = 32;
    private static final int PRESENT_SLOT_ICON_COLOR = 0x30A040;
    private static final int LIMITED_SLOT_ICON_COLOR = 0xDCA000;
    private static final int MISSING_SLOT_ICON_COLOR = 0xB43030;
    private static final String PRESENT_SLOT_ICON = "\u2713";
    private static final String LIMITED_SLOT_ICON = "!";
    private static final String MISSING_SLOT_ICON = "X";
    private static final String DEFAULT_CRAFT_AMOUNT = "1";

    private final List<Gradient> gridBackgrounds = new ArrayList<>(GRID_SIZE);
    private final List<ItemIcon> gridIcons = new ArrayList<>(GRID_SIZE);
    private final List<Text> gridStatusIcons = new ArrayList<>(GRID_SIZE);
    private final List<Tooltip> gridStatusTooltips = new ArrayList<>(GRID_SIZE);
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

    /**
     * Creates the workshop module window and wires its controls to their handlers.
     *
     * @param moduleView the module view backing this window
     */
    public WindowWorkshopModule(final WorkshopModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "gui/layouthuts/layoutworkshopmodule.xml"));

        for (int i = 0; i < GRID_SIZE; i++)
        {
            slotMatches.add(new ArrayList<>());
            final int slot = i;
            gridBackgrounds.add(window.findPaneOfTypeByID("gridBackground" + i, Gradient.class));
            gridIcons.add(window.findPaneOfTypeByID("gridIcon" + i, ItemIcon.class));
            final Text gridStatusIcon = window.findPaneOfTypeByID("gridStatusIcon" + i, Text.class);
            gridStatusIcons.add(gridStatusIcon);
            final Button gridButton = window.findPaneOfTypeByID("gridButton" + i, Button.class);
            gridButtons.add(gridButton);
            gridStatusTooltips.add(PaneBuilders.tooltipBuilder().hoverPane(gridStatusIcon).build());
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
        LIMITED,
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

    private record RecipeSelectionSnapshot(
        @Nullable IRequest<?> selectedRequest,
        List<ItemStack> requestOutputs,
        ItemStack jeiSearchOutput,
        List<WorkshopRecipe> matchingRecipes,
        int selectedRecipeIndex)
    {
        static RecipeSelectionSnapshot capture(final WindowWorkshopModule window)
        {
            return new RecipeSelectionSnapshot(
                window.selectedRequest,
                copyStacks(window.requestOutputs),
                window.jeiSearchOutput.copy(),
                List.copyOf(window.matchingRecipes),
                window.selectedRecipeIndex);
        }

        private static List<ItemStack> copyStacks(final List<ItemStack> stacks)
        {
            final List<ItemStack> copies = new ArrayList<>(stacks.size());
            for (final ItemStack stack : stacks)
            {
                copies.add(stack.copy());
            }
            return copies;
        }
    }

    /**
     * Describes how many requested crafts can currently be completed and which ingredients limit partial crafting.
     */
    private record CraftCapacity(int requestedCrafts, int supportedCrafts, Set<ItemStorage> limitingIngredients)
    {
        static CraftCapacity none()
        {
            return new CraftCapacity(0, 0, Set.of());
        }

        boolean isPartial()
        {
            return supportedCrafts > 0 && supportedCrafts < requestedCrafts && !limitingIngredients.isEmpty();
        }
    }

    /**
     * Screen-space area exposed to JEI as a ghost ingredient drop target.
     */
    public record WorkshopTargetArea(int x, int y, int width, int height)
    {
    }

    /**
     * Initializes workshop state when the window opens and requests the player's saved workshop settings.
     */
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

    /**
     * Applies player-specific settings received from the server and refreshes recipe availability if needed.
     *
     * @param buildingPos the building position these settings belong to
     * @param outputTarget the selected destination for crafted output
     * @param includePlayerInventory whether player inventory should be used as an ingredient source
     */
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

    /**
     * Toggles crafted output between warehouse and player inventory, then persists the setting server-side.
     */
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

    /**
     * Toggles whether player inventory can supply ingredients, then persists the setting server-side.
     */
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

    /**
     * Updates the output target button label to reflect the current settings state.
     */
    private void updateOutputTargetButton()
    {
        outputTargetButton.setEnabled(playerSettingsLoaded);
        outputTargetButton.setText(Component.translatable(!playerSettingsLoaded
            ? "com.warehouseworkshop.core.gui.workshop.settings.loading"
            : moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? "com.warehouseworkshop.core.gui.workshop.output_target.warehouse"
            : "com.warehouseworkshop.core.gui.workshop.output_target.inventory"));
    }

    /**
     * Updates the player-inventory toggle button label to reflect the current settings state.
     */
    private void updateIncludePlayerInventoryButton()
    {
        includePlayerInventoryButton.setEnabled(playerSettingsLoaded);
        includePlayerInventoryButton.setText(Component.translatable(!playerSettingsLoaded
            ? "com.warehouseworkshop.core.gui.workshop.settings.loading"
            : moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.include_player_inventory.yes"
            : "com.warehouseworkshop.core.gui.workshop.include_player_inventory.no"));
    }

    /**
     * Reopens the window with no selected request, returning to the request list.
     */
    private void showRequests()
    {
        refreshWarehouseStock();
        refreshPlayerInventoryStock();
        new WindowWorkshopSelectRequest(moduleView, this::matchingRequest, this::reopenWithRequest).open();
    }

    /**
     * Checks whether a request has at least one craftable output recipe.
     *
     * @param request the request to inspect
     * @return true if the request can be matched to a workshop recipe
     */
    private boolean matchingRequest(@NotNull final IRequest<?> request)
    {
        final List<ItemStack> outputs = extractRequestedOutputs(request);
        return !outputs.isEmpty() && !findMatchingRecipes(outputs, false).isEmpty();
    }

    /**
     * Reopens the workshop window and selects the provided request, if one was supplied.
     *
     * @param request the request to select after reopening
     */
    private void reopenWithRequest(@Nullable final IRequest<?> request)
    {
        if (request != null)
        {
            selectRequest(request);
        }

        open();
    }

    /**
     * Selects a request for the workshop and updates the GUI to reflect the selected request.
     * This method clears the JEI selection, sets the output label text, sets the selected request,
     * extracts the requested outputs from the request, refreshes the player inventory stock,
     * finds matching recipes for the requested outputs, sets the selected recipe index,
     * populates the craft amount from the request, and applies the selected recipe.
     *
     * @param request the request to select
     */
    private void selectRequest(@NotNull final IRequest<?> request)
    {
        clearJeiSelection(true);
        outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.request_item"));
        this.selectedRequest = request;
        this.requestOutputs.clear();
        this.requestOutputs.addAll(extractRequestedOutputs(request));
        refreshPlayerInventoryStock();
        this.matchingRecipes.addAll(findMatchingRecipes(this.requestOutputs, false));
        populateCraftAmountFromRequest();
        selectFirstCraftableRecipe();
    }

    /**
     * Selects a JEI output for the workshop and updates the GUI to reflect the selected output.
     * This method clears the request selection, sets the output label text, sets the selected JEI output,
     * refreshes the player inventory stock, finds matching recipes for the selected JEI output,
     * sets the selected recipe index, and applies the selected recipe.
     *
     * @param output the JEI output to select
     */
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
        selectFirstCraftableRecipe();
    }

    /**
     * Applies a JEI ingredient to a workshop slot, preserving the current output selection when the edited grid
     * still produces that output.
     *
     * @param slot the slot to apply the ingredient to
     * @param stack the ingredient to apply
     */
    public void applyJeiIngredientToSlot(final int slot, @NotNull final ItemStack stack)
    {
        if (slot < 0 || slot >= GRID_SIZE || stack.isEmpty() || !canAcceptIngredientInSlot(slot))
        {
            return;
        }

        final RecipeSelectionSnapshot previousSelection = RecipeSelectionSnapshot.capture(this);

        final ItemStack selected = stack.copy();
        selected.setCount(1);
        slotMatches.get(slot).clear();
        selectedGrid.set(slot, selected);
        displayGrid.set(slot, selected.copy());
        slotStates.set(slot, SlotState.PRESENT);
        refreshGridSlotAvailability();

        if (!doesCurrentSelectionStillMatchGrid() && !restoreMatchingSelectionForCurrentGrid(previousSelection))
        {
            clearRequestSelection();
            clearJeiSelection(false);
            outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.search_item"));
        }

        updateGridIcons();
    }

    /**
     * Selects the previous recipe in the current matching recipe list.
     */
    private void selectPreviousRecipe()
    {
        if (matchingRecipes.isEmpty())
        {
            return;
        }

        selectedRecipeIndex = (selectedRecipeIndex - 1 + matchingRecipes.size()) % matchingRecipes.size();
        applySelectedRecipe();
    }

    /**
     * Selects the next recipe in the current matching recipe list.
     */
    private void selectNextRecipe()
    {
        if (matchingRecipes.isEmpty())
        {
            return;
        }

        selectedRecipeIndex = (selectedRecipeIndex + 1) % matchingRecipes.size();
        applySelectedRecipe();
    }

    /**
     * Selects the first matching recipe that can currently be crafted, falling back to the first match.
     */
    private void selectFirstCraftableRecipe()
    {
        if (matchingRecipes.isEmpty())
        {
            selectedRecipeIndex = -1;
            applySelectedRecipe();
            return;
        }

        for (int i = 0; i < matchingRecipes.size(); i++)
        {
            selectedRecipeIndex = i;
            applySelectedRecipe();
            if (isGridCraftable())
            {
                return;
            }
        }

        selectedRecipeIndex = 0;
        applySelectedRecipe();
    }

    /**
     * Applies the currently selected recipe to the workshop grid. This method will clear the current request selection,
     * clear the current JEI selection (but does not clear the crafting grid), and then attempts to apply
     * the selected recipe to the workshop grid. The method will first clear all slots in the grid, and then
     * attempt to apply each ingredient in the recipe to a slot in the grid. If the ingredient can be
     * found in the warehouse stock, it will be reserved from the stock and the slot will be set to present. If
     * the ingredient cannot be found in the warehouse stock but can be found in the player's inventory, it
     * will be reserved from the player's inventory and the slot will be set to present. If the ingredient cannot
     * be found in either the warehouse stock or the player's inventory, the slot will be set to missing.
     *
     * If the selected recipe is a DOMUM recipe, it will be applied using the applySelectedDomumRecipe method.
     */
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

        final List<CraftingSlotRequirement> slotRequirements =
            OptionalRecipeSupport.buildCraftingSlotRequirements(selectedRecipe.craftingRecipe().value(), GRID_SIZE);
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems = new HashMap<>();

        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final CraftingSlotRequirement requirement = slotRequirements.get(slot);
            if (requirement.ingredient().isEmpty())
            {
                continue;
            }

            displayGrid.set(slot, getIngredientDisplayStack(requirement.ingredient()));
            final List<ItemStorage> matches = findWarehouseMatches(requirement.ingredient());
            slotMatches.get(slot).addAll(matches);

            if (tryReserveSlotFromMatches(slot, requirement, matches, remainingStock, reservedUniqueItems))
            {
                setSlotPresent(slot);
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerMatches = findPlayerInventoryMatches(requirement.ingredient());
                slotMatches.get(slot).addAll(playerMatches);
                if (tryReserveSlotFromMatches(slot, requirement, playerMatches, remainingPlayerStock, reservedUniqueItems))
                {
                    setSlotPresent(slot);
                    continue;
                }
            }

            slotStates.set(slot, SlotState.MISSING);
        }

        updateGridIcons();
    }

    /**
     * Applies the currently selected DOMUM recipe to the workshop grid. This method will clear the current request selection,
     * clear the current JEI selection (but does not clear the crafting grid), and then attempts to apply
     * each component of the DOMUM recipe to a slot in the grid. If the component can be found in the warehouse stock,
     * it will be reserved from the stock and the slot will be set to present. If the component cannot be found in the
     * warehouse stock but can be found in the player's inventory, it will be reserved from the player's inventory and the
     * slot will be set to present. If the component cannot be found in either the warehouse stock or the player's inventory,
     * the slot will be set to missing.
     */
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

            if (tryReserveSlotFromMatches(slot, CraftingSlotRequirement.empty(), matches, remainingStock, new HashMap<>()))
            {
                setSlotPresent(slot);
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerMatches = findDomumMatches(requirements.get(component).material(), playerStock);
                slotMatches.get(slot).addAll(playerMatches);
                if (tryReserveSlotFromMatches(slot, CraftingSlotRequirement.empty(), playerMatches, remainingPlayerStock, new HashMap<>()))
                {
                    setSlotPresent(slot);
                    continue;
                }
            }

            slotStates.set(slot, SlotState.MISSING);
        }
    }

    /**
     * Marks a grid slot as present and displays the selected concrete stack for that slot.
     *
     * @param slot the slot index to mark present
     */
    private void setSlotPresent(final int slot)
    {
        displayGrid.set(slot, selectedGrid.get(slot));
        slotStates.set(slot, SlotState.PRESENT);
    }

    private boolean tryReserveSlotFromMatches(
        final int slot,
        final CraftingSlotRequirement requirement,
        final List<ItemStorage> matches,
        final Map<ItemStorage, Integer> remainingStock,
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems)
    {
        for (final ItemStorage match : matches)
        {
            final int available = remainingStock.getOrDefault(match, 0);
            if (available > 0 && canUseUniqueCandidate(requirement, match.getItemStack(), reservedUniqueItems))
            {
                final ItemStack chosen = match.getItemStack().copy();
                chosen.setCount(1);
                selectedGrid.set(slot, chosen);
                remainingStock.put(match, available - 1);
                reserveUniqueCandidate(requirement, chosen, reservedUniqueItems);
                return true;
            }
        }

        return false;
    }

    private boolean canUseUniqueCandidate(
        final CraftingSlotRequirement requirement,
        final ItemStack stack,
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems)
    {
        if (requirement.uniqueGroup() == null)
        {
            return true;
        }

        @SuppressWarnings("null")
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && !reservedUniqueItems.getOrDefault(requirement.uniqueGroup(), Set.of()).contains(itemId);
    }

    /**
     * Reserves the given ItemStack as used for the given unique group, if the ItemStack is not null and the requirement has a unique group.
     * This method does not check if the ItemStack can be used for the requirement (i.e. it does not check if the ItemStack matches the requirement's ingredient).
     * @param requirement the requirement to reserve the ItemStack for
     * @param stack the ItemStack to reserve
     * @param reservedUniqueItems the map of reserved unique items
     */
    private void reserveUniqueCandidate(
        final CraftingSlotRequirement requirement,
        final ItemStack stack,
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems)
    {
        if (requirement.uniqueGroup() == null)
        {
            return;
        }

        @SuppressWarnings("null")
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null)
        {
            reservedUniqueItems.computeIfAbsent(requirement.uniqueGroup(), ignored -> new HashSet<>()).add(itemId);
        }
    }

    /**
     * Handles a click on a grid slot, including click-through selection for populated ingredients.
     *
     * @param slot the clicked grid slot
     */
    private void setActiveSlot(final int slot)
    {
        if (!canAcceptIngredientInSlot(slot))
        {
            return;
        }

        if (slotStates.get(slot) != SlotState.EMPTY && !displayGrid.get(slot).isEmpty())
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

    /**
     * Checks whether JEI may drop an ingredient into the given slot.
     *
     * @param slot the grid slot to check
     * @return true if the slot can accept a dropped ingredient
     */
    public boolean canAcceptIngredientInSlot(final int slot)
    {
        if (slot < 0 || slot >= GRID_SIZE)
        {
            return false;
        }

        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        return selectedRecipe == null || selectedRecipe.kind() != RecipeKind.DOMUM || slot == DOMUM_FIRST_SLOT || slot == DOMUM_SECOND_SLOT;
    }

    /**
     * Gets the JEI drop target area for a grid ingredient slot.
     *
     * @param slot the grid slot to expose
     * @return the target area, or null for an invalid slot
     */
    public @Nullable WorkshopTargetArea getJeiIngredientTargetArea(final int slot)
    {
        if (slot < 0 || slot >= GRID_SIZE)
        {
            return null;
        }

        final Button gridButton = gridButtons.get(slot);
        return new WorkshopTargetArea(gridButton.getX(), gridButton.getY(), gridButton.getWidth(), gridButton.getHeight());
    }

    /**
     * Gets the JEI drop target area used to select the desired output item.
     *
     * @return the output target area
     */
    public WorkshopTargetArea getJeiOutputTargetArea()
    {
        return new WorkshopTargetArea(requestIcon.getX(), requestIcon.getY(), requestIcon.getWidth(), requestIcon.getHeight());
    }

    /**
     * Clears the current request, JEI selection, and crafting grid.
     */
    private void clearGrid()
    {
        clearRequestSelection();
        clearJeiSelection(true);
        updateGridIcons();
    }

    /**
     * Sends a request to craft the selected recipe once.
     */
    private void craft()
    {
        performCraft(1, 0);
    }

    /**
     * Sends a request to craft as many items as supported by the requested craft amount and stock.
     */
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

        performCraft(craftCount, getCraftAmount());
    }

    /**
     * Sends a craft request to the server to perform a workshop craft.
     * @param craftCount The number of times to craft the active recipe.
     * @param requestedOutputCount The total requested output count for craft-all status reporting.
     */
    private void performCraft(final int craftCount, final int requestedOutputCount)
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

        new WorkshopCraftMessage(buildingView.getPosition(), List.copyOf(selectedGrid), craftCount, requestedOutputCount, activeRecipe.kind().networkId, activeRecipe.id()).sendToServer();
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
        final CraftCapacity capacity = getCraftCapacity();
        return capacity.requestedCrafts() <= 0 ? 0 : Math.min(capacity.requestedCrafts(), capacity.supportedCrafts());
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
    private CraftCapacity getCraftCapacity()
    {
        final WorkshopRecipe selectedRecipe = getActiveRecipe();
        if (selectedRecipe == null)
        {
            return CraftCapacity.none();
        }

        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return CraftCapacity.none();
        }

        final ItemStack recipeOutput = getCurrentGridOutput();
        final int requestedOutputCount = getCraftAmount();
        if (requestedOutputCount <= 0)
        {
            return CraftCapacity.none();
        }

        final int requestedCrafts = getRequestedCraftCount(recipeOutput, requestedOutputCount);
        if (requestedCrafts <= 0)
        {
            return CraftCapacity.none();
        }

        return getCraftCapacity(requestedCrafts);
    }

    @SuppressWarnings("null")
    /**
     * Calculates craft capacity for a fixed number of requested crafts.
     *
     * @param requestedCrafts the number of crafts to evaluate
     * @return the supported craft count and limiting ingredients
     */
    private CraftCapacity getCraftCapacity(final int requestedCrafts)
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
            return CraftCapacity.none();
        }

        final WorkshopRecipe activeRecipe = getActiveRecipe();
        final Level level = Minecraft.getInstance().level;
        if (activeRecipe == null || activeRecipe.kind() != RecipeKind.CRAFTING || level == null)
        {
            return getBasicCraftCapacity(requestedCrafts, requiredPerCraft);
        }

        final RecipeHolder<CraftingRecipe> currentRecipe = getCurrentGridRecipe();
        if (currentRecipe == null)
        {
            return getBasicCraftCapacity(requestedCrafts, requiredPerCraft);
        }

        final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));
        if (input == null || input.isEmpty())
        {
            return CraftCapacity.none();
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

        final Set<ItemStorage> limitingIngredients = supportedCrafts > 0 && supportedCrafts < requestedCrafts
            ? getLimitingIngredients(requiredPerCraft, remainingWarehouseStock, remainingPlayerStock, includePlayerInventory)
            : Set.of();
        return new CraftCapacity(requestedCrafts, supportedCrafts, limitingIngredients);
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
     * Calculates craft capacity for recipes that do not need remainder-aware simulation.
     *
     * @param requestedCrafts the number of crafts requested
     * @param requiredPerCraft the ingredients required for one craft
     * @return the supported craft count and limiting ingredients
     */
    private CraftCapacity getBasicCraftCapacity(final int requestedCrafts, final Map<ItemStorage, Integer> requiredPerCraft)
    {
        final int supportedCrafts = getBasicSupportedCraftCount(requiredPerCraft);
        if (supportedCrafts <= 0 || supportedCrafts >= requestedCrafts)
        {
            return new CraftCapacity(requestedCrafts, supportedCrafts, Set.of());
        }

        final Set<ItemStorage> limitingIngredients = new HashSet<>();
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            if (getAvailableIngredientCount(entry.getKey()) - (entry.getValue() * supportedCrafts) < entry.getValue())
            {
                limitingIngredients.add(entry.getKey());
            }
        }

        return new CraftCapacity(requestedCrafts, supportedCrafts, limitingIngredients);
    }

    private Set<ItemStorage> getLimitingIngredients(
        final Map<ItemStorage, Integer> requiredPerCraft,
        final Map<ItemStorage, Integer> remainingWarehouseStock,
        final Map<ItemStorage, Integer> remainingPlayerStock,
        final boolean includePlayerInventory)
    {
        final Set<ItemStorage> limitingIngredients = new HashSet<>();
        for (final Map.Entry<ItemStorage, Integer> entry : requiredPerCraft.entrySet())
        {
            final int available = getStockCount(remainingWarehouseStock, entry.getKey())
                + (includePlayerInventory ? getStockCount(remainingPlayerStock, entry.getKey()) : 0);
            if (available < entry.getValue())
            {
                limitingIngredients.add(entry.getKey());
            }
        }

        return limitingIngredients;
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

    /**
     * Decrements the given amount of the given ingredient from the given stock map.
     * If the amount to decrement is greater than the current stock of the ingredient, the remaining amount is returned.
     * If the amount to decrement is less than or equal to the current stock of the ingredient, the stock count is updated
     * to reflect the new amount and 0 is returned.
     *
     * @param stock the map of items to their counts in the stock
     * @param ingredient the ingredient to decrement
     * @param amount the amount of the ingredient to decrement
     * @return the remaining amount of the ingredient that was not decremented
     */
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

    /**
     * Checks whether the current grid has a valid active recipe, output, and available ingredients.
     *
     * @return true if the current grid can be crafted
     */
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
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        if (selectedRecipe != null && selectedRecipe.kind() == RecipeKind.CRAFTING)
        {
            return getSelectedGridRecipe(selectedRecipe);
        }

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
     * Gets the selected crafting recipe if the current grid still matches that specific recipe.
     *
     * @param selectedRecipe the selected workshop recipe to validate
     * @return the crafting recipe holder, or null if the grid no longer matches
     */
    @SuppressWarnings("null")
    private @Nullable RecipeHolder<CraftingRecipe> getSelectedGridRecipe(final WorkshopRecipe selectedRecipe)
    {
        final RecipeHolder<CraftingRecipe> recipe = selectedRecipe.craftingRecipe();
        final Level level = Minecraft.getInstance().level;
        if (recipe == null || level == null)
        {
            return null;
        }

        final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));
        if (input == null || input.isEmpty() || !recipe.value().matches(input, level))
        {
            return null;
        }

        return recipe;
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

        if (selectedRecipe != null && selectedRecipe.kind() == RecipeKind.CRAFTING)
        {
            final RecipeHolder<CraftingRecipe> recipe = getSelectedGridRecipe(selectedRecipe);
            final Level level = Minecraft.getInstance().level;
            if (recipe == null || level == null)
            {
                return ItemStack.EMPTY;
            }

            final CraftingInput input = CraftingInput.of(3, 3, List.copyOf(selectedGrid));
            return recipe.value().assemble(input, level.registryAccess()).copy();
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
     * Recalculates per-slot present/missing state from the current grid and known ingredient stock.
     */
    private void refreshGridSlotAvailability()
    {
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final ItemStack selected = selectedGrid.get(slot);
            if (selected.isEmpty())
            {
                slotStates.set(slot, displayGrid.get(slot).isEmpty() ? SlotState.EMPTY : SlotState.MISSING);
                continue;
            }

            final ItemStorage ingredient = new ItemStorage(selected.copy());
            if (decrementStock(remainingStock, ingredient, selected.getCount()) <= 0)
            {
                slotStates.set(slot, SlotState.PRESENT);
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory()
                && decrementStock(remainingPlayerStock, ingredient, selected.getCount()) <= 0)
            {
                slotStates.set(slot, SlotState.PRESENT);
                continue;
            }

            slotStates.set(slot, SlotState.MISSING);
        }
    }

    /**
     * Checks whether the current selected recipe and output context still match the edited grid.
     *
     * @return true if the selected recipe remains valid for the current grid
     */
    private boolean doesCurrentSelectionStillMatchGrid()
    {
        final WorkshopRecipe selectedRecipe = getSelectedRecipe();
        return selectedRecipe != null && recipeMatchesCurrentGrid(selectedRecipe) && doesCurrentGridMatchRequest();
    }

    /**
     * Restores a recipe/output selection that matches the current grid after a manual ingredient edit.
     *
     * @param previousSelection the selection state before the grid edit
     * @return true if a matching selection was restored
     */
    private boolean restoreMatchingSelectionForCurrentGrid(final RecipeSelectionSnapshot previousSelection)
    {
        final List<ItemStack> desiredOutputs = getDesiredOutputs(previousSelection);
        if (desiredOutputs.isEmpty())
        {
            return false;
        }

        selectedRequest = previousSelection.selectedRequest();
        requestOutputs.clear();
        requestOutputs.addAll(RecipeSelectionSnapshot.copyStacks(previousSelection.requestOutputs()));
        jeiSearchOutput = previousSelection.jeiSearchOutput().copy();

        matchingRecipes.clear();
        matchingRecipes.addAll(findMatchingRecipes(desiredOutputs, false));
        for (int i = 0; i < matchingRecipes.size(); i++)
        {
            selectedRecipeIndex = i;
            if (recipeMatchesCurrentGrid(matchingRecipes.get(i)) && doesCurrentGridMatchRequest())
            {
                return true;
            }
        }

        selectedRecipeIndex = previousSelection.selectedRecipeIndex();
        matchingRecipes.clear();
        matchingRecipes.addAll(previousSelection.matchingRecipes());
        return false;
    }

    /**
     * Gets the desired outputs represented by a saved request or JEI output selection.
     *
     * @param selection the saved selection state
     * @return copied desired output stacks, or an empty list if no output was selected
     */
    private List<ItemStack> getDesiredOutputs(final RecipeSelectionSnapshot selection)
    {
        if (!selection.requestOutputs().isEmpty())
        {
            return RecipeSelectionSnapshot.copyStacks(selection.requestOutputs());
        }

        if (!selection.jeiSearchOutput().isEmpty())
        {
            return List.of(selection.jeiSearchOutput().copy());
        }

        return List.of();
    }

    /**
     * Does the recipe match the current grid configuration?
     * @param recipe
     * @return
     */
    private boolean recipeMatchesCurrentGrid(final WorkshopRecipe recipe)
    {
        if (recipe.kind() == RecipeKind.DOMUM)
        {
            return !assembleDomumOutput(recipe).isEmpty();
        }

        final RecipeHolder<CraftingRecipe> craftingRecipe = recipe.craftingRecipe();
        final Level level = Minecraft.getInstance().level;
        if (craftingRecipe == null || level == null)
        {
            return false;
        }

        List<ItemStack> grid = List.copyOf(selectedGrid);

        if (grid == null) return false;

        final CraftingInput input = CraftingInput.of(3, 3, grid);
        return input != null && !input.isEmpty() && craftingRecipe.value().matches(input, level);
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

    /**
     * Builds a list of matches for the given recipe, with the index of the outer list corresponding to the slot index of the recipe.
     * Each inner list will contain the ItemStorage objects that match the ingredient at the corresponding slot.
     * If the ingredient at the slot cannot be found in the warehouse stock or the player's inventory, the inner list will be empty.
     * If the player's inventory should be included in the search (moduleView.shouldIncludePlayerInventory() returns true),
     * the inner lists will also contain matches from the player's inventory.
     * @param recipe the crafting recipe to build matches for
     * @return a list of lists of ItemStorage objects, or null if any ingredient cannot be found in the warehouse stock or the player's inventory
     */
    private @Nullable List<List<ItemStorage>> buildSlotMatches(final CraftingRecipe recipe)
    {
        final List<CraftingSlotRequirement> slotRequirements = OptionalRecipeSupport.buildCraftingSlotRequirements(recipe, GRID_SIZE);
        final List<List<ItemStorage>> matches = new ArrayList<>(GRID_SIZE);
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);
        final Map<ItemStorage, Integer> remainingPlayerStock = new HashMap<>(playerStock);
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems = new HashMap<>();

        for (int i = 0; i < GRID_SIZE; i++)
        {
            matches.add(new ArrayList<>());
        }

        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final CraftingSlotRequirement requirement = slotRequirements.get(slot);
            if (requirement.ingredient().isEmpty())
            {
                continue;
            }

            final List<ItemStorage> candidates = findWarehouseMatches(requirement.ingredient());
            matches.get(slot).addAll(candidates);

            if (reserveFirstAvailable(requirement, candidates, remainingStock, reservedUniqueItems))
            {
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerCandidates = findPlayerInventoryMatches(requirement.ingredient());
                matches.get(slot).addAll(playerCandidates);
                if (reserveFirstAvailable(requirement, playerCandidates, remainingPlayerStock, reservedUniqueItems))
                {
                    continue;
                }
            }

            return null;
        }

        return matches;
    }

    /**
     * Builds a list of lists of item storages that match the given DOMUM recipe requirements.
     * Each inner list contains item storages that match the requirement at the given index.
     * The outer list is in the same order as the DOMUM recipe requirements.
     *
     * @param recipe The workshop recipe to build the slot matches for.
     * @return A list of lists of item storages that match the given DOMUM recipe requirements, or null if the recipe has no requirements.
     */
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

            if (reserveFirstAvailable(CraftingSlotRequirement.empty(), candidates, remainingStock, new HashMap<>()))
            {
                continue;
            }

            if (moduleView.shouldIncludePlayerInventory())
            {
                final List<ItemStorage> playerCandidates = findDomumMatches(requirements.get(component).material(), playerStock);
                matches.get(slot).addAll(playerCandidates);
                if (reserveFirstAvailable(CraftingSlotRequirement.empty(), playerCandidates, remainingPlayerStock, new HashMap<>()))
                {
                    continue;
                }
            }

            return null;
        }

        return matches;
    }

    /**
     * Gets the number of ingredients in the given workshop recipe that match the given slot index.
     * If the recipe is a DOMUM recipe, this method will return the number of components in the recipe that match the given slot index.
     * If the recipe is a shaping recipe, this method will return the number of ingredients in the recipe that match the given slot index.
     * If the recipe is a shapeless recipe, this method will return the number of ingredients in the recipe that match the given slot index.
     *
     * @param recipe The workshop recipe to get the ingredient count for.
     * @param slot The slot index to get the ingredient count for.
     * @return The number of ingredients in the given workshop recipe that match the given slot index.
     */
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

        final List<CraftingSlotRequirement> slotRequirements =
            OptionalRecipeSupport.buildCraftingSlotRequirements(recipe.craftingRecipe().value(), GRID_SIZE);
        if (slot < 0 || slot >= slotRequirements.size())
        {
            return 0;
        }

        final ItemStack targetDisplayStack = getIngredientDisplayStack(slotRequirements.get(slot).ingredient());
        if (targetDisplayStack.isEmpty())
        {
            return 0;
        }

        int matches = 0;
        for (final CraftingSlotRequirement requirement : slotRequirements)
        {
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(targetDisplayStack, getIngredientDisplayStack(requirement.ingredient()), false, true))
            {
                matches++;
            }
        }

        return matches;
    }

    /**
     * Material requirement for one Domum Ornamentum recipe component slot.
     */
    private record DomumSlotRequirement(Block material)
    {
    }

    private boolean reserveFirstAvailable(
        final CraftingSlotRequirement requirement,
        final List<ItemStorage> candidates,
        final Map<ItemStorage, Integer> remainingStock,
        final Map<ResourceLocation, Set<ResourceLocation>> reservedUniqueItems)
    {
        for (final ItemStorage candidate : candidates)
        {
            final int available = remainingStock.getOrDefault(candidate, 0);
            if (available > 0 && canUseUniqueCandidate(requirement, candidate.getItemStack(), reservedUniqueItems))
            {
                remainingStock.put(candidate, available - 1);
                reserveUniqueCandidate(requirement, candidate.getItemStack(), reservedUniqueItems);
                return true;
            }
        }

        return false;
    }

    /**
     * Finds warehouse inventory stacks that satisfy the given ingredient.
     *
     * @param ingredient the ingredient to match
     * @return matching warehouse stacks sorted for display and selection
     */
    private List<ItemStorage> findWarehouseMatches(final Ingredient ingredient)
    {
        return findMatches(ingredient, warehouseStock);
    }

    /**
     * Finds player inventory stacks that satisfy the given ingredient.
     *
     * @param ingredient the ingredient to match
     * @return matching player inventory stacks sorted for display and selection
     */
    private List<ItemStorage> findPlayerInventoryMatches(final Ingredient ingredient)
    {
        return findMatches(ingredient, playerStock);
    }

    /**
     * Finds all matches in the given stock for the given ingredient.
     * @param ingredient The ingredient to find matches for.
     * @param stock The stock to search for matches.
     * @return A list of matches, sorted by amount in descending order, and then by name in case insensitive order.
     */
    @SuppressWarnings("null")
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

    /**
     * Finds all matches in the given stock for the given domum material.
     * @param material The material to find matches for.
     * @param stock The stock to search for matches.
     * @return A list of matches, sorted by amount in descending order, and then by name in case insensitive order.
     */
    @SuppressWarnings("null")
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

    /**
     * Counts available stock for an ingredient from warehouse and, when enabled, player inventory.
     *
     * @param ingredient the ingredient storage key to count
     * @return the available item count
     */
    private int getAvailableIngredientCount(final ItemStorage ingredient)
    {
        int available = getStockCount(warehouseStock, ingredient);
        if (moduleView.shouldIncludePlayerInventory())
        {
            available += getStockCount(playerStock, ingredient);
        }

        return available;
    }

    /**
     * Counts matching entries for an ingredient inside a stock map.
     *
     * @param stock the stock map to inspect
     * @param ingredient the ingredient storage key to count
     * @return the matching item count
     */
    @SuppressWarnings("null")
    private int getStockCount(final Map<ItemStorage, Integer> stock, final ItemStorage ingredient)
    {
        return stock.entrySet().stream()
            .filter(candidate -> Objects.equals(candidate.getKey(), ingredient))
            .mapToInt(Map.Entry::getValue)
            .sum();
    }

    /**
     * Gets the currently selected recipe from the matching recipe list.
     *
     * @return the selected recipe, or null when no valid recipe index is selected
     */
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

        if (selectedRecipe != null && selectedRecipe.kind() == RecipeKind.CRAFTING)
        {
            return getSelectedGridRecipe(selectedRecipe) == null ? null : selectedRecipe;
        }

        final RecipeHolder<CraftingRecipe> currentRecipe = getCurrentGridRecipe();
        return currentRecipe == null ? selectedRecipe : WorkshopRecipe.crafting(currentRecipe, level);
    }

    /**
     * Clears the selected colony request and its requested output cache.
     */
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
    @SuppressWarnings("null")
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

    /**
     * Updates craft button enabled states and dependent grid highlighting.
     */
    private void updateCraftButtons()
    {
        updatePartialCraftHighlights();
        updateGridBackgrounds();
        final boolean craftable = playerSettingsLoaded && isGridCraftable();
        craftButton.setEnabled(craftable);
        craftAllButton.setEnabled(craftable && hasValidCraftAmount());
    }

    /**
     * Populates the craft amount input from the selected request preview output.
     */
    private void populateCraftAmountFromRequest()
    {
        final ItemStack previewOutput = getRequestPreviewOutput();
        final int requestedCount = previewOutput.isEmpty() ? 0 : previewOutput.getCount();
        craftAmountInput.setText(requestedCount > 0 ? Integer.toString(requestedCount) : DEFAULT_CRAFT_AMOUNT);
    }

    /**
     * Checks whether the craft amount input contains a positive value.
     *
     * @return true if the craft amount is valid
     */
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

    /**
     * Gets the localized ready status for the current ingredient source setting.
     *
     * @return the ready status text
     */
    private Component getReadyStatusText()
    {
        return Component.translatable(moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.status.ready.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.ready");
    }

    /**
     * Gets the localized missing-ingredients status for the current ingredient source setting.
     *
     * @return the missing status text
     */
    private Component getMissingStatusText()
    {
        return Component.translatable(moduleView.shouldIncludePlayerInventory()
            ? "com.warehouseworkshop.core.gui.workshop.status.missing.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.missing");
    }

    /**
     * Sets the status label text using the default status color.
     *
     * @param status the status text to display
     */
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
        updatePartialCraftHighlights();
        for (int i = 0; i < GRID_SIZE; i++)
        {
            gridIcons.get(i).setItem(displayGrid.get(i));
            updateGridBackground(i);
            gridButtons.get(i).setEnabled(!domumRecipe || i == DOMUM_FIRST_SLOT || i == DOMUM_SECOND_SLOT);
        }

        updateRequestDetails();
    }

    /**
     * Marks present slots as limited when the current craft amount can only be partially fulfilled.
     */
    private void updatePartialCraftHighlights()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            if (slotStates.get(i) == SlotState.LIMITED)
            {
                slotStates.set(i, SlotState.PRESENT);
            }
        }

        if (!playerSettingsLoaded || !hasValidCraftAmount() || !isGridCraftable())
        {
            return;
        }

        final CraftCapacity capacity = getCraftCapacity();
        if (!capacity.isPartial())
        {
            return;
        }

        for (int i = 0; i < GRID_SIZE; i++)
        {
            if (slotStates.get(i) == SlotState.PRESENT && isLimitingIngredient(selectedGrid.get(i), capacity.limitingIngredients()))
            {
                slotStates.set(i, SlotState.LIMITED);
            }
        }
    }

    /**
     * Checks whether a stack belongs to the set of ingredients limiting partial craft capacity.
     *
     * @param stack the stack to check
     * @param limitingIngredients the limiting ingredient keys
     * @return true if the stack is a limiting ingredient
     */
    private boolean isLimitingIngredient(final ItemStack stack, final Set<ItemStorage> limitingIngredients)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        final ItemStorage ingredient = new ItemStorage(stack.copy());
        for (final ItemStorage limitingIngredient : limitingIngredients)
        {
            if (Objects.equals(limitingIngredient, ingredient))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Refreshes all grid slot background colors and status indicators.
     */
    private void updateGridBackgrounds()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            updateGridBackground(i);
        }
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
        updateGridStatusIndicator(slot);
        switch (slotStates.get(slot))
        {
            case PRESENT:
                background.setGradientStart(PRESENT_SLOT_RED, PRESENT_SLOT_GREEN, PRESENT_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.setGradientEnd(PRESENT_SLOT_RED, PRESENT_SLOT_GREEN, PRESENT_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.show();
                break;
            case LIMITED:
                background.setGradientStart(LIMITED_SLOT_RED, LIMITED_SLOT_GREEN, LIMITED_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
                background.setGradientEnd(LIMITED_SLOT_RED, LIMITED_SLOT_GREEN, LIMITED_SLOT_BLUE, SLOT_BACKGROUND_ALPHA);
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

    /**
     * Updates the status icon and tooltip for a single grid slot.
     *
     * @param slot the grid slot to update
     */
    private void updateGridStatusIndicator(final int slot)
    {
        final Text statusIcon = gridStatusIcons.get(slot);
        final Tooltip tooltip = gridStatusTooltips.get(slot);
        switch (slotStates.get(slot))
        {
            case PRESENT:
                statusIcon.setText(Component.literal(PRESENT_SLOT_ICON).withStyle(ChatFormatting.BOLD));
                setStatusIconColor(statusIcon, PRESENT_SLOT_ICON_COLOR);
                statusIcon.show();
                tooltip.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.slot_status.present"));
                tooltip.hide();
                break;
            case LIMITED:
                statusIcon.setText(Component.literal(LIMITED_SLOT_ICON).withStyle(ChatFormatting.BOLD));
                setStatusIconColor(statusIcon, LIMITED_SLOT_ICON_COLOR);
                statusIcon.show();
                tooltip.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.slot_status.limited"));
                tooltip.hide();
                break;
            case MISSING:
                statusIcon.setText(Component.literal(MISSING_SLOT_ICON).withStyle(ChatFormatting.BOLD));
                setStatusIconColor(statusIcon, MISSING_SLOT_ICON_COLOR);
                statusIcon.show();
                tooltip.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.slot_status.missing"));
                tooltip.hide();
                break;
            default:
                statusIcon.hide();
                tooltip.clearText();
                tooltip.hide();
                break;
        }
    }

    /**
     * Applies a color consistently to all visual states of a grid status icon.
     *
     * @param statusIcon the icon text control to recolor
     * @param color the ARGB/RGB color value to apply
     */
    private void setStatusIconColor(final Text statusIcon, final int color)
    {
        statusIcon.setTextColor(color);
        statusIcon.setTextHoverColor(color);
        statusIcon.setTextDisabledColor(color);
    }
}
