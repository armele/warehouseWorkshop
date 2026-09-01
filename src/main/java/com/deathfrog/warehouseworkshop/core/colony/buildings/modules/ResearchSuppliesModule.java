package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.util.TraceUtils;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.util.MessageUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persists research supply batches and starts opted-in research without requiring an online player. */
public class ResearchSuppliesModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    private static final String TAG_PENDING = "ResearchSupplyPending";
    private static final String TAG_RESEARCH = "Research";
    private static final String TAG_BRANCH = "Branch";
    private static final String TAG_AUTO_START = "AutoStart";
    private final Map<ResourceLocation, PendingResearch> pending = new LinkedHashMap<>();

    /** Adds or replaces a pending supply batch. */
    public void addPending(final ResourceLocation branch, final ResourceLocation research, final boolean autoStart)
    {
        pending.put(research, new PendingResearch(branch, research, autoStart));
        markDirty();
        TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
            "Research supplies: registered pending research branch={}, research={}, autoStart={}, university={}, colony={}",
            branch, research, autoStart, building.getPosition(), building.getColony().getID()));
    }

    /** Returns whether a research already has a supply batch. */
    public boolean isPending(final ResourceLocation research)
    {
        return pending.containsKey(research);
    }

    /** Returns identifiers for all supply batches. */
    public Set<ResourceLocation> getPendingResearch()
    {
        return Set.copyOf(pending.keySet());
    }

    /** Returns identifiers for batches that should automatically start. */
    @SuppressWarnings("null")
    public Set<ResourceLocation> getAutoStartResearch()
    {
        return pending.values().stream().filter(PendingResearch::autoStart).map(PendingResearch::research)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Periodically completes delivered batches and processes the FIFO automatic-start queue. */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (pending.isEmpty())
        {
            return;
        }

        TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
            "Research supplies tick: university={}, colony={}, pending={}, buildingLevel={}, activeResearch={}",
            building.getPosition(), colony.getID(), pending.size(), building.getBuildingLevel(),
            colony.getResearchManager().getResearchTree().getResearchInProgress().size()));

        final List<ResourceLocation> completed = new ArrayList<>();
        for (final PendingResearch entry : pending.values())
        {
            final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(entry.branch(), entry.research());
            if (research == null)
            {
                WarehouseWorkshopMod.LOGGER.warn(
                    "Research supplies: removing unresolved research branch={}, research={}, university={}, colony={}",
                    entry.branch(), entry.research(), building.getPosition(), colony.getID());
                completed.add(entry.research());
                continue;
            }
            if (colony.getResearchManager().getResearchTree().getResearch(entry.branch(), entry.research()) != null)
            {
                TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                    "Research supplies: removing research already present in local tree branch={}, research={}, university={}, colony={}",
                    entry.branch(), entry.research(), building.getPosition(), colony.getID()));
                completed.add(entry.research());
                continue;
            }
            final boolean hasCosts = ResearchSupplyPlanner.universityHasCosts(building, research);

            TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                "Research supplies entry: branch={}, research={}, autoStart={}, universityHasCosts={}",
                entry.branch(), entry.research(), entry.autoStart(), hasCosts));

            if (!hasCosts) continue;

            if (!entry.autoStart())
            {
                TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                    "Research supplies: delivery-only request completed for branch={}, research={}, university={}, colony={}",
                    entry.branch(), entry.research(), building.getPosition(), colony.getID()));
                completed.add(entry.research());
                continue;
            }
            if (tryStart(colony, research))
            {
                completed.add(entry.research());
                break;
            }
        }
        if (!completed.isEmpty())
        {
            completed.forEach(pending::remove);
            markDirty();
            TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                "Research supplies: removed completed entries {} from university={}, colony={}; remaining={}",
                completed, building.getPosition(), colony.getID(), pending.size()));
        }
    }

    /** Revalidates, logs each start condition, consumes university-only costs, and starts one research. */
    @SuppressWarnings("null")
    private boolean tryStart(final IColony colony, final IGlobalResearch research)
    {
        final ILocalResearchTree localTree = colony.getResearchManager().getResearchTree();
        final int activeResearch = localTree.getResearchInProgress().size();
        final int researchCapacity = building.getBuildingLevel();
        final boolean queueAvailable = activeResearch < researchCapacity;
        final boolean canResearch = research.canResearch(building, localTree);
        final boolean requirementsFulfilled = IGlobalResearchTree.getInstance()
            .isResearchRequirementsFulfilled(research.getResearchRequirements(), colony);
        final boolean universityHasCosts = ResearchSupplyPlanner.universityHasCosts(building, research);

        TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
            "Research supplies start check: branch={}, research={}, queueAvailable={} ({}/{}), canResearch={}, requirementsFulfilled={}, universityHasCosts={}",
            research.getBranch(), research.getId(), queueAvailable, activeResearch, researchCapacity, canResearch,
            requirementsFulfilled, universityHasCosts));

        if (!queueAvailable || !canResearch || !requirementsFulfilled || !universityHasCosts) return false;

        final boolean costsConsumed = ResearchSupplyPlanner.consumeUniversityCosts(building, research);
        if (!costsConsumed)
        {
            WarehouseWorkshopMod.LOGGER.warn(
                "Research supplies: costs passed validation but could not be consumed for branch={}, research={}, university={}, colony={}",
                research.getBranch(), research.getId(), building.getPosition(), colony.getID());
            return false;
        }
        TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
            "Research supplies: invoking startResearch for branch={}, research={}, university={}, colony={}",
            research.getBranch(), research.getId(), building.getPosition(), colony.getID()));
        try
        {
            research.startResearch(localTree);
        }
        catch (final RuntimeException exception)
        {
            WarehouseWorkshopMod.LOGGER.error(
                "Research supplies: startResearch threw for branch={}, research={}, university={}, colony={}",
                research.getBranch(), research.getId(), building.getPosition(), colony.getID(), exception);
            return false;
        }
        final boolean registered = localTree.getResearch(research.getBranch(), research.getId()) != null;
        final int activeAfterStart = localTree.getResearchInProgress().size();
        if (!registered)
        {
            WarehouseWorkshopMod.LOGGER.warn(
                "Research supplies: startResearch returned but research is absent from the local tree; branch={}, research={}, activeBefore={}, activeAfter={}, university={}, colony={}",
                research.getBranch(), research.getId(), activeResearch, activeAfterStart, building.getPosition(), colony.getID());
        }
        else
        {
            TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                "Research supplies: startResearch registered successfully; branch={}, research={}, activeBefore={}, activeAfter={}, university={}, colony={}",
                research.getBranch(), research.getId(), activeResearch, activeAfterStart, building.getPosition(), colony.getID()));
        }
        colony.getResearchManager().markDirty();
        colony.markDirty();
        MessageUtils.format("com.warehouseworkshop.research_supplies.started",
            MutableComponent.create(research.getName())).sendTo(colony).forManagers();
        return true;
    }

    /** Loads persistent supply batches. */
    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        pending.clear();
        for (final Tag raw : compound.getList(TAG_PENDING, Tag.TAG_COMPOUND))
        {
            final CompoundTag tag = (CompoundTag) raw;
            final ResourceLocation branch = ResourceLocation.tryParse(tag.getString(TAG_BRANCH));
            final ResourceLocation research = ResourceLocation.tryParse(tag.getString(TAG_RESEARCH));
            if (branch != null && research != null)
            {
                pending.put(research, new PendingResearch(branch, research, tag.getBoolean(TAG_AUTO_START)));
            }
        }
        if (!pending.isEmpty())
        {
            TraceUtils.dynamicTrace(TraceUtils.TRACE_RESEARCH_DELIVERY, () -> WarehouseWorkshopMod.LOGGER.info(
                "Research supplies: restored {} pending entries from NBT for university={}, colony={}",
                pending.size(), building.getPosition(), building.getColony().getID()));
        }
    }

    /** Saves persistent supply batches. */
    @SuppressWarnings("null")
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        final ListTag list = new ListTag();
        for (final PendingResearch entry : pending.values())
        {
            final CompoundTag tag = new CompoundTag();
            tag.put(TAG_BRANCH, StringTag.valueOf(entry.branch().toString()));
            tag.put(TAG_RESEARCH, StringTag.valueOf(entry.research().toString()));
            tag.putBoolean(TAG_AUTO_START, entry.autoStart());
            list.add(tag);
        }
        compound.put(TAG_PENDING, list);
    }

    /** Persistent descriptor for one requested research. */
    private record PendingResearch(ResourceLocation branch, ResourceLocation research, boolean autoStart)
    {
    }
}
