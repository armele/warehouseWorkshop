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
import com.deathfrog.warehouseworkshop.core.network.SetWorkshopOutputTargetMessage;
import com.deathfrog.warehouseworkshop.core.network.WorkshopCraftMessage;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Workshop module window styled after the MineColonies recipe teaching UI.
 */
public class WindowWorkshopModule extends AbstractModuleWindow<WorkshopModuleView>
{
    private static final int GRID_SIZE = 9;

    private final List<ItemIcon> gridIcons = new ArrayList<>(GRID_SIZE);
    private final List<ItemStack> selectedGrid = new ArrayList<>(Collections.nCopies(GRID_SIZE, ItemStack.EMPTY));
    private final List<List<ItemStorage>> slotMatches = new ArrayList<>(GRID_SIZE);

    private final ItemIcon requestIcon;
    private final ItemIcon outputIcon;
    private final Text requestLabel;
    private final Text recipeLabel;
    private final Text outputLabel;
    private final Text statusLabel;
    private final Button outputTargetButton;

    private final Map<ItemStorage, Integer> warehouseStock = new HashMap<>();
    private final List<ItemStack> requestOutputs = new ArrayList<>();
    private final List<RecipeHolder<CraftingRecipe>> matchingRecipes = new ArrayList<>();

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
            gridIcons.add(window.findPaneOfTypeByID("gridIcon" + i, ItemIcon.class));
            registerButton("gridButton" + i, () -> setActiveSlot(slot));
        }

        this.requestIcon = window.findPaneOfTypeByID("requestPreview", ItemIcon.class);
        this.outputIcon = window.findPaneOfTypeByID("outputPreview", ItemIcon.class);
        this.requestLabel = window.findPaneOfTypeByID("requestLabel", Text.class);
        this.recipeLabel = window.findPaneOfTypeByID("recipeLabel", Text.class);
        this.outputLabel = window.findPaneOfTypeByID("outputLabel", Text.class);
        this.statusLabel = window.findPaneOfTypeByID("statusLabel", Text.class);
        this.outputTargetButton = window.findPaneOfTypeByID("outputTarget", Button.class);

        registerButton("request", this::showRequests);
        registerButton("clear", this::clearGrid);
        registerButton("craft", this::craft);
        registerButton("craftAll", this::craftAll);
        registerButton("prevRecipe", this::selectPreviousRecipe);
        registerButton("nextRecipe", this::selectNextRecipe);
        registerButton("outputTarget", this::toggleOutputTarget);

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

    @Override
    public void onOpened()
    {
        super.onOpened();
        refreshWarehouseStock();
        updateOutputTargetButton();
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

    private void updateOutputTargetButton()
    {
        outputTargetButton.setText(Component.translatable(moduleView.getOutputTarget() == OutputTarget.WAREHOUSE_INVENTORY
            ? "com.warehouseworkshop.core.gui.workshop.output_target.warehouse"
            : "com.warehouseworkshop.core.gui.workshop.output_target.inventory"));
    }

    private void showRequests()
    {
        refreshWarehouseStock();
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
        if (slot < 0 || slot >= GRID_SIZE || stack.isEmpty())
        {
            return;
        }

        clearRequestSelection();
        clearJeiSelection(false);
        outputLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.search_item"));

        final ItemStack selected = stack.copy();
        selected.setCount(1);
        selectedGrid.set(slot, selected);
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
        }

        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
        if (selectedRecipe == null)
        {
            updateRequestDetails();
            updateGridIcons();
            return;
        }

        final List<Ingredient> slottedIngredients = buildSlottedIngredients(selectedRecipe.value());
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);

        for (int slot = 0; slot < GRID_SIZE; slot++)
        {
            final Ingredient ingredient = slottedIngredients.get(slot);
            if (ingredient.isEmpty())
            {
                continue;
            }

            final List<ItemStorage> matches = findWarehouseMatches(ingredient);
            slotMatches.get(slot).addAll(matches);

            for (final ItemStorage match : matches)
            {
                final int available = remainingStock.getOrDefault(match, 0);
                if (available > 0)
                {
                    final ItemStack chosen = match.getItemStack().copy();
                    chosen.setCount(1);
                    selectedGrid.set(slot, chosen);
                    remainingStock.put(match, available - 1);
                    break;
                }
            }
        }

        updateRequestDetails();
        updateGridIcons();
    }

    private void setActiveSlot(final int slot)
    {
        updateRequestDetails();
    }

    private void clearGrid()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            selectedGrid.set(i, ItemStack.EMPTY);
        }

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
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"));
            return;
        }

        performCraft(craftCount);
    }

    private void performCraft(final int craftCount)
    {
        if (getActiveRecipe() == null)
        {
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.norecipe"));
            return;
        }

        if (!isGridCraftable())
        {
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"));
            return;
        }

        new WorkshopCraftMessage(buildingView.getPosition(), List.copyOf(selectedGrid), craftCount).sendToServer();
        statusLabel.setText(Component.translatable(
            craftCount == 1
                ? "com.warehouseworkshop.core.gui.workshop.status.sent"
                : "com.warehouseworkshop.core.gui.workshop.status.sent.all",
            craftCount));
        refreshWarehouseStock();
        applySelectedRecipe();
    }

    private int getCraftAllCount()
    {
        final RecipeHolder<CraftingRecipe> selectedRecipe = getActiveRecipe();
        if (selectedRecipe == null)
        {
            return 0;
        }

        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return 0;
        }

        final ItemStack recipeOutput = selectedRecipe.value().getResultItem(level.registryAccess());
        final int requestedCrafts = getRequestedCraftCount(recipeOutput);
        final int warehouseCrafts = getWarehouseSupportedCraftCount();
        if (requestedCrafts <= 0)
        {
            return warehouseCrafts;
        }

        return Math.min(requestedCrafts, warehouseCrafts);
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

    private int getWarehouseSupportedCraftCount()
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
            final int available = warehouseStock.entrySet().stream()
                .filter(candidate -> Objects.equals(candidate.getKey(), entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
            supportedCrafts = Math.min(supportedCrafts, available / entry.getValue());
        }

        return supportedCrafts == Integer.MAX_VALUE ? 0 : supportedCrafts;
    }

    private boolean isGridCraftable()
    {
        return getCurrentGridRecipe() != null && hasWarehouseContents(List.copyOf(selectedGrid));
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
        if (selectedRequest == null)
        {
            return true;
        }

        final ItemStack gridOutput = getCurrentGridOutput();
        return !gridOutput.isEmpty() && getRequestedOutputCount(gridOutput) > 0;
    }

    private ItemStack getRequestPreviewOutput()
    {
        final RecipeHolder<CraftingRecipe> selectedRecipe = getActiveRecipe();
        final Level level = Minecraft.getInstance().level;
        if (selectedRecipe != null && level != null)
        {
            final ItemStack recipeOutput = selectedRecipe.value().getResultItem(level.registryAccess()).copy();
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

    private List<RecipeHolder<CraftingRecipe>> findMatchingRecipes(final List<ItemStack> requestedOutputs, final boolean requireWarehouseContents)
    {
        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return List.of();
        }

        final RecipeManager recipeManager = level.getRecipeManager();
        final List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();

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
                matches.add(recipe);
            }
        }

        matches.sort(Comparator.comparing(recipe -> recipe.value().getResultItem(level.registryAccess()).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private @Nullable List<List<ItemStorage>> buildSlotMatches(final CraftingRecipe recipe)
    {
        final List<Ingredient> slottedIngredients = buildSlottedIngredients(recipe);
        final List<List<ItemStorage>> matches = new ArrayList<>(GRID_SIZE);
        final Map<ItemStorage, Integer> remainingStock = new HashMap<>(warehouseStock);

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
            if (candidates.isEmpty())
            {
                return null;
            }

            matches.get(slot).addAll(candidates);

            boolean reserved = false;
            for (final ItemStorage candidate : candidates)
            {
                final int available = remainingStock.getOrDefault(candidate, 0);
                if (available > 0)
                {
                    remainingStock.put(candidate, available - 1);
                    reserved = true;
                    break;
                }
            }

            if (!reserved)
            {
                return null;
            }
        }

        return matches;
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
        final List<ItemStorage> matches = new ArrayList<>();

        for (final Map.Entry<ItemStorage, Integer> entry : warehouseStock.entrySet())
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

    private boolean hasWarehouseContents(final List<ItemStack> stacks)
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
            final int available = warehouseStock.entrySet().stream()
                .filter(candidate -> Objects.equals(candidate.getKey(), entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();

            if (available < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    private @Nullable RecipeHolder<CraftingRecipe> getSelectedRecipe()
    {
        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= matchingRecipes.size())
        {
            return null;
        }

        return matchingRecipes.get(selectedRecipeIndex);
    }

    private @Nullable RecipeHolder<CraftingRecipe> getActiveRecipe()
    {
        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
        return selectedRecipe != null ? selectedRecipe : getCurrentGridRecipe();
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
            }
        }
    }

    private void updateRequestDetails()
    {
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

        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
        final RecipeHolder<CraftingRecipe> activeRecipe = getActiveRecipe();
        if (activeRecipe == null)
        {
            recipeLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.none"));
            outputIcon.setItem(ItemStack.EMPTY);
            statusLabel.setText(selectedGrid.stream().allMatch(ItemStack::isEmpty)
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
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"));
            return;
        }

        statusLabel.setText(doesCurrentGridMatchRequest()
            ? Component.translatable("com.warehouseworkshop.core.gui.workshop.status.ready")
            : Component.translatable("com.warehouseworkshop.core.gui.workshop.status.validmismatch").withColor(Color.getByName("red")));
    }

    private void updateGridIcons()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            gridIcons.get(i).setItem(selectedGrid.get(i));
        }

        updateRequestDetails();
    }
}
