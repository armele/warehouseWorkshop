package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Receives authoritative research-supply snapshots from the network layer. */
public interface ResearchSuppliesReceiver
{
    /** Replaces the displayed rows when the snapshot targets this window. */
    void receiveResearchSupplies(BlockPos universityPos, List<ResearchStatus> statuses);
}
