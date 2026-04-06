package com.deathfrog.warehouseworkshop.core.client.network;

import java.util.List;

import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side handling for workshop craft completion updates.
 */
public final class ClientWorkshopCraftedHandler
{
    private ClientWorkshopCraftedHandler()
    {
    }

    public static void handle(final BlockPos buildingPos, final List<ItemStack> warehouseStock, final List<ItemStack> playerStock)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof final BOScreen gui))
        {
            return;
        }

        final BOWindow window = gui.getWindow();
        if (window instanceof final WindowWorkshopModule workshopWindow)
        {
            workshopWindow.refreshCraftingContents(buildingPos, warehouseStock, playerStock);
        }
    }
}
