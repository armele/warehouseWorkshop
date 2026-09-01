package com.deathfrog.warehouseworkshop.core.client.network;

import com.deathfrog.warehouseworkshop.core.client.gui.modules.ResearchSuppliesReceiver;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import com.ldtteam.blockui.BOScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Routes research-supply snapshots to the currently open compatible window. */
public final class ClientResearchSuppliesHandler
{
    private ClientResearchSuppliesHandler()
    {
    }

    /** Delivers a snapshot to the active research-supplies receiver. */
    public static void handle(final BlockPos position, final List<ResearchStatus> statuses)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen screen
            && screen.getWindow() instanceof ResearchSuppliesReceiver receiver)
        {
            receiver.receiveResearchSupplies(position, statuses);
        }
    }
}
