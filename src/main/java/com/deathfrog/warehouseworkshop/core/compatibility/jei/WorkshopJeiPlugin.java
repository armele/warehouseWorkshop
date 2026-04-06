package com.deathfrog.warehouseworkshop.core.compatibility.jei;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.ldtteam.blockui.BOScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

@mezz.jei.api.JeiPlugin
public class WorkshopJeiPlugin implements IModPlugin
{
    private final WorkshopGhostIngredientHandler ghostIngredientHandler = new WorkshopGhostIngredientHandler();

    @Override
    public @NotNull ResourceLocation getPluginUid()
    {
        return ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(@NotNull final IGuiHandlerRegistration registration)
    {
        registration.addGuiScreenHandler(BOScreen.class, gui ->
        {
            final WindowWorkshopModule workshopWindow = WorkshopGhostIngredientHandler.getWorkshopWindow(gui);
            if (workshopWindow == null)
            {
                return null;
            }

            final WorkshopGhostIngredientHandler.ScreenBounds bounds = WorkshopGhostIngredientHandler.ScreenBounds.of(gui);
            final int guiWidth = Math.max(1, (int) Math.round(workshopWindow.getWidth() * bounds.scaleFactor()));
            final int guiHeight = Math.max(1, (int) Math.round(workshopWindow.getHeight() * bounds.scaleFactor()));

            return new mezz.jei.api.gui.handlers.IGuiProperties()
            {
                @Override
                public Class<? extends Screen> screenClass()
                {
                    return BOScreen.class;
                }

                @Override
                public int guiLeft()
                {
                    return bounds.guiLeft();
                }

                @Override
                public int guiTop()
                {
                    return bounds.guiTop();
                }

                @Override
                public int guiXSize()
                {
                    return guiWidth;
                }

                @Override
                public int guiYSize()
                {
                    return guiHeight;
                }

                @Override
                public int screenWidth()
                {
                    return Minecraft.getInstance().getWindow().getGuiScaledWidth();
                }

                @Override
                public int screenHeight()
                {
                    return Minecraft.getInstance().getWindow().getGuiScaledHeight();
                }
            };
        });
        registration.addGhostIngredientHandler(BOScreen.class, ghostIngredientHandler);
    }
}
