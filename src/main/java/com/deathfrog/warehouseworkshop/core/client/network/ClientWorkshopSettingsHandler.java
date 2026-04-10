package com.deathfrog.warehouseworkshop.core.client.network;

import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-side handling for persisted workshop settings updates.
 */
public final class ClientWorkshopSettingsHandler
{
    private ClientWorkshopSettingsHandler()
    {
    }

    public static void handle(final BlockPos buildingPos, final OutputTarget outputTarget, final boolean includePlayerInventory)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof final BOScreen gui))
        {
            return;
        }

        final BOWindow window = gui.getWindow();
        if (window instanceof final WindowWorkshopModule workshopWindow)
        {
            workshopWindow.refreshPlayerSettings(buildingPos, outputTarget, includePlayerInventory);
        }
    }
}
