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
import com.deathfrog.warehouseworkshop.core.network.WorkshopCraftMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
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
    private static final int MATCH_COLUMNS = 9;

    private final List<ItemIcon> gridIcons = new ArrayList<>(GRID_SIZE);
    private final List<ItemStack> selectedGrid = new ArrayList<>(Collections.nCopies(GRID_SIZE, ItemStack.EMPTY));
    private final List<List<ItemStorage>> slotMatches = new ArrayList<>(GRID_SIZE);

    private final ScrollingList matchesList;
    private final ItemIcon requestIcon;
    private final ItemIcon outputIcon;
    private final Text requestLabel;
    private final Text recipeLabel;
    private final Text slotLabel;
    private final Text statusLabel;

    private final Map<ItemStorage, Integer> warehouseStock = new HashMap<>();
    private final List<ItemStack> requestOutputs = new ArrayList<>();
    private final List<RecipeHolder<CraftingRecipe>> matchingRecipes = new ArrayList<>();

    private @Nullable IRequest<?> selectedRequest;
    private int selectedRecipeIndex = -1;
    private int activeSlot;

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

        this.matchesList = window.findPaneOfTypeByID("matches", ScrollingList.class);
        this.requestIcon = window.findPaneOfTypeByID("requestPreview", ItemIcon.class);
        this.outputIcon = window.findPaneOfTypeByID("outputPreview", ItemIcon.class);
        this.requestLabel = window.findPaneOfTypeByID("requestLabel", Text.class);
        this.recipeLabel = window.findPaneOfTypeByID("recipeLabel", Text.class);
        this.slotLabel = window.findPaneOfTypeByID("slotLabel", Text.class);
        this.statusLabel = window.findPaneOfTypeByID("statusLabel", Text.class);

        registerButton("request", this::showRequests);
        registerButton("clear", this::clearGrid);
        registerButton("craft", this::craft);
        registerButton("craftAll", this::craftAll);
        registerButton("prevRecipe", this::selectPreviousRecipe);
        registerButton("nextRecipe", this::selectNextRecipe);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        refreshWarehouseStock();
        updateRequestDetails();
        updateMatchesList();
        updateGridIcons();
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
            this.selectedRequest = request;
            this.requestOutputs.clear();
            this.requestOutputs.addAll(extractRequestedOutputs(request));
            this.matchingRecipes.clear();
            this.matchingRecipes.addAll(findMatchingRecipes(this.requestOutputs, false));
            this.selectedRecipeIndex = this.matchingRecipes.isEmpty() ? -1 : 0;
            applySelectedRecipe();
        }

        open();
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
        this.activeSlot = 0;

        for (int i = 0; i < GRID_SIZE; i++)
        {
            slotMatches.get(i).clear();
            selectedGrid.set(i, ItemStack.EMPTY);
        }

        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
        if (selectedRecipe == null)
        {
            updateRequestDetails();
            updateMatchesList();
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
        updateMatchesList();
        updateGridIcons();
    }

    private void setActiveSlot(final int slot)
    {
        this.activeSlot = slot;
        updateRequestDetails();
        updateMatchesList();
    }

    private void assignMatchToActiveSlot(@NotNull final ItemStorage match)
    {
        final ItemStack chosen = match.getItemStack().copy();
        chosen.setCount(1);
        selectedGrid.set(activeSlot, chosen);
        updateGridIcons();
        updateMatchesList();
    }

    private void clearGrid()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            selectedGrid.set(i, ItemStack.EMPTY);
        }

        updateGridIcons();
        updateMatchesList();
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
        if (getSelectedRecipe() == null)
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
        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
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
        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
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

    private void updateRequestDetails()
    {
        if (selectedRequest == null)
        {
            requestLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.request.none"));
            requestIcon.setItem(ItemStack.EMPTY);
        }
        else
        {
            requestLabel.setText(selectedRequest.getShortDisplayString().copy());
            requestIcon.setItem(getRequestPreviewOutput());
        }

        slotLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.slot", activeSlot + 1));

        final RecipeHolder<CraftingRecipe> selectedRecipe = getSelectedRecipe();
        if (selectedRecipe == null)
        {
            recipeLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.none"));
            outputIcon.setItem(ItemStack.EMPTY);
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.selectrequest"));
            return;
        }

        final ItemStack currentGridOutput = getCurrentGridOutput();
        recipeLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.recipe.index", selectedRecipeIndex + 1, matchingRecipes.size()));
        outputIcon.setItem(currentGridOutput);
        if (!isGridCraftable())
        {
            statusLabel.setText(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"));
            return;
        }

        statusLabel.setText(doesCurrentGridMatchRequest()
            ? Component.translatable("com.warehouseworkshop.core.gui.workshop.status.ready")
            : Component.translatable("com.warehouseworkshop.core.gui.workshop.status.validmismatch"));
    }

    private void updateGridIcons()
    {
        for (int i = 0; i < GRID_SIZE; i++)
        {
            gridIcons.get(i).setItem(selectedGrid.get(i));
        }

        updateRequestDetails();
    }

    private void updateMatchesList()
    {
        matchesList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return (slotMatches.get(activeSlot).size() + MATCH_COLUMNS - 1) / MATCH_COLUMNS;
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                for (int column = 0; column < MATCH_COLUMNS; column++)
                {
                    final int matchIndex = (index * MATCH_COLUMNS) + column;
                    final ItemIcon icon = rowPane.findPaneOfTypeByID("matchIcon" + column, ItemIcon.class);
                    final Text qty = rowPane.findPaneOfTypeByID("matchQty" + column, Text.class);
                    final Button useButton = rowPane.findPaneOfTypeByID("matchUse" + column, Button.class);

                    if (matchIndex < slotMatches.get(activeSlot).size())
                    {
                        final ItemStorage storage = slotMatches.get(activeSlot).get(matchIndex);
                        icon.setItem(storage.getItemStack());
                        qty.setText(Component.literal(Integer.toString(storage.getAmount())));
                        useButton.setHandler(button -> assignMatchToActiveSlot(storage));
                    }
                    else
                    {
                        icon.setItem(ItemStack.EMPTY);
                        qty.setText(Component.empty());
                        useButton.setHandler(button -> {});
                    }
                }
            }
        });
    }
}






