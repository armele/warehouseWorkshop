package com.deathfrog.warehouseworkshop.core.research;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearchTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Calculates eligible research costs and exact, multi-warehouse delivery plans. */
public final class ResearchSupplyPlanner
{
    private ResearchSupplyPlanner()
    {
    }

    /** Describes one displayed research and its current material coverage. */
    public record ResearchStatus(ResourceLocation branch, ResourceLocation research, String name, int depth,
                                 List<CostStatus> costs, boolean deliverable, boolean pending, boolean autoStart,
                                 StartState startState, int activeResearch, int researchCapacity)
    {
    }

    /** Describes the authoritative reason an automatic research start is waiting. */
    public enum StartState
    {
        NOT_REQUESTED,
        DELIVERY_PENDING,
        MATERIALS_MISSING,
        QUEUE_FULL,
        NO_LONGER_ELIGIBLE,
        REQUIREMENTS_MISSING,
        READY
    }

    /** Describes availability for one research cost. */
    public record CostStatus(ItemStack display, int required, int university, int warehouse)
    {
    }

    /** Describes an exact delivery from one warehouse. */
    public record Allocation(IWareHouse warehouse, ItemStack stack)
    {
    }

    /** Returns all material-blocked research whose non-material prerequisites are fulfilled. */
    public static List<ResearchStatus> getStatuses(final IBuilding university, final Set<ResourceLocation> pending,
                                                    final Set<ResourceLocation> autoStart)
    {
        final IColony colony = university.getColony();
        final ILocalResearchTree localTree = colony.getResearchManager().getResearchTree();
        final List<ResearchStatus> result = new ArrayList<>();
        for (final IGlobalResearch research : allResearch())
        {
            final boolean isPending = pending.contains(research.getId());
            final boolean canResearch = research.canResearch(university, localTree);
            final boolean requirementsFulfilled = IGlobalResearchTree.getInstance()
                .isResearchRequirementsFulfilled(research.getResearchRequirements(), colony);
            if (research.getCostList().isEmpty() || research.isAutostart()
                || (!isPending && (!canResearch || !requirementsFulfilled)))
            {
                continue;
            }

            final List<CostStatus> costs = buildCostStatuses(university, research);
            final boolean universityReady = costs.stream().allMatch(cost -> cost.university() >= cost.required());
            if (universityReady && !isPending)
            {
                continue;
            }
            final boolean deliverable = costs.stream().allMatch(cost -> cost.university() + cost.warehouse() >= cost.required());
            final boolean shouldAutoStart = autoStart.contains(research.getId());
            final int activeResearch = localTree.getResearchInProgress().size();
            final int researchCapacity = university.getBuildingLevel();
            final StartState startState = getStartState(isPending, shouldAutoStart, universityReady, activeResearch,
                researchCapacity, canResearch, requirementsFulfilled);
            result.add(new ResearchStatus(research.getBranch(), research.getId(), research.getName().getKey(), research.getDepth(),
                costs, deliverable, isPending, shouldAutoStart, startState, activeResearch, researchCapacity));
        }
        result.sort((left, right) -> Boolean.compare(right.deliverable(), left.deliverable()));
        return result;
    }

    /** Resolves the first server-side condition preventing an automatic start. */
    private static StartState getStartState(final boolean pending, final boolean autoStart, final boolean universityReady,
                                            final int activeResearch, final int researchCapacity,
                                            final boolean canResearch, final boolean requirementsFulfilled)
    {
        if (!pending) return StartState.NOT_REQUESTED;
        if (!autoStart) return universityReady ? StartState.READY : StartState.DELIVERY_PENDING;
        if (!universityReady) return StartState.MATERIALS_MISSING;
        if (activeResearch >= researchCapacity) return StartState.QUEUE_FULL;
        if (!canResearch) return StartState.NO_LONGER_ELIGIBLE;
        if (!requirementsFulfilled) return StartState.REQUIREMENTS_MISSING;
        return StartState.READY;
    }

    /** Creates a concrete warehouse allocation for the university's outstanding cost. */
    public static List<Allocation> createAllocation(final IBuilding university, final IGlobalResearch research)
    {
        final List<Allocation> allocations = new ArrayList<>();
        final List<StockEntry> universityStock = stock(university.getItemHandlerCap(), null);
        final List<StockEntry> warehouseStock = new ArrayList<>();
        for (final IWareHouse warehouse : university.getColony().getServerBuildingManager().getWareHouses())
        {
            warehouseStock.addAll(stock(warehouse.getItemHandlerCap(), warehouse));
        }
        for (final SizedIngredient cost : orderedCosts(research))
        {
            int remaining = cost.count() - consumeMatches(universityStock, cost, cost.count(), null);
            if (remaining <= 0)
            {
                continue;
            }
            remaining -= consumeMatches(warehouseStock, cost, remaining, allocations);
            if (remaining > 0)
            {
                return List.of();
            }
        }
        return allocations;
    }

    /** Tests whether the university alone contains every research cost. */
    public static boolean universityHasCosts(final IBuilding university, final IGlobalResearch research)
    {
        final List<StockEntry> available = stock(university.getItemHandlerCap(), null);
        return orderedCosts(research).stream().allMatch(cost -> consumeMatches(available, cost, cost.count(), null) >= cost.count());
    }

    /** Removes every research cost from the university inventory. */
    public static boolean consumeUniversityCosts(final IBuilding university, final IGlobalResearch research)
    {
        if (!universityHasCosts(university, research))
        {
            return false;
        }
        final IItemHandler inventory = university.getItemHandlerCap();
        for (final SizedIngredient cost : orderedCosts(research))
        {
            int remaining = cost.count();
            for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++)
            {
                if (IGlobalResearch.isUniversityResearchMatch(inventory.getStackInSlot(slot), cost))
                {
                    remaining -= inventory.extractItem(slot, remaining, false).getCount();
                }
            }
        }
        return true;
    }

    /** Builds item-level availability information for one research. */
    private static List<CostStatus> buildCostStatuses(final IBuilding university, final IGlobalResearch research)
    {
        final List<CostStatus> result = new ArrayList<>();
        final List<StockEntry> universityStock = stock(university.getItemHandlerCap(), null);
        final List<StockEntry> warehouseStock = new ArrayList<>();
        for (final IWareHouse warehouse : university.getColony().getServerBuildingManager().getWareHouses())
        {
            warehouseStock.addAll(stock(warehouse.getItemHandlerCap(), warehouse));
        }
        for (final SizedIngredient cost : orderedCosts(research))
        {
            final ItemStack[] candidates = cost.getItems();
            if (candidates.length == 0)
            {
                continue;
            }
            final int atUniversity = consumeMatches(universityStock, cost, cost.count(), null);
            final int atWarehouses = consumeMatches(warehouseStock, cost, Math.max(0, cost.count() - atUniversity), null);
            result.add(new CostStatus(candidates[0].copyWithCount(1), cost.count(), atUniversity, atWarehouses));
        }
        return result;
    }

    /** Enumerates every research by walking each branch from its roots. */
    private static List<IGlobalResearch> allResearch()
    {
        final IGlobalResearchTree tree = IGlobalResearchTree.getInstance();
        final List<IGlobalResearch> result = new ArrayList<>();
        final Set<ResearchKey> visited = new HashSet<>();
        for (final ResourceLocation branch : tree.getBranches())
        {
            final ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(tree.getPrimaryResearch(branch));
            while (!pending.isEmpty())
            {
                final ResourceLocation id = pending.removeFirst();
                if (!visited.add(new ResearchKey(branch, id)))
                {
                    continue;
                }
                final IGlobalResearch research = tree.getResearch(branch, id);
                if (research != null)
                {
                    result.add(research);
                    pending.addAll(research.getChildren());
                }
            }
        }
        return result;
    }

    /** Orders restrictive ingredients before broad tag ingredients to avoid consuming scarce variants prematurely. */
    private static List<SizedIngredient> orderedCosts(final IGlobalResearch research)
    {
        final List<SizedIngredient> costs = new ArrayList<>(research.getCostList());
        costs.sort(java.util.Comparator.comparingInt(cost -> cost.getItems().length));
        return costs;
    }

    /** Copies inventory slots into consumable planning entries. */
    private static List<StockEntry> stock(final IItemHandler inventory, final IWareHouse warehouse)
    {
        final List<StockEntry> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) result.add(new StockEntry(warehouse, stack.copy(), stack.getCount()));
        }
        return result;
    }

    /** Consumes matching planning stock and optionally records concrete warehouse allocations. */
    private static int consumeMatches(final List<StockEntry> stock, final SizedIngredient cost, final int requested,
                                      final List<Allocation> allocations)
    {
        int consumed = 0;
        for (final StockEntry entry : stock)
        {
            if (consumed >= requested) break;
            if (entry.remaining > 0 && IGlobalResearch.isUniversityResearchMatch(entry.stack, cost))
            {
                final int amount = Math.min(requested - consumed, entry.remaining);
                entry.remaining -= amount;
                consumed += amount;
                if (allocations != null && entry.warehouse != null)
                {
                    allocations.add(new Allocation(entry.warehouse, entry.stack.copyWithCount(amount)));
                }
            }
        }
        return consumed;
    }

    /** Mutable stock entry used only while constructing a plan. */
    private static final class StockEntry
    {
        private final IWareHouse warehouse;
        private final ItemStack stack;
        private int remaining;

        /** Creates a consumable inventory snapshot entry. */
        private StockEntry(final IWareHouse warehouse, final ItemStack stack, final int remaining)
        {
            this.warehouse = warehouse;
            this.stack = stack;
            this.remaining = remaining;
        }
    }

    /** Branch-qualified key used while traversing the global research tree. */
    private record ResearchKey(ResourceLocation branch, ResourceLocation research)
    {
    }
}
