package com.deathfrog.warehouseworkshop.core.compatibility.jei;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.ldtteam.blockui.BOScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

@mezz.jei.api.JeiPlugin
public class WorkshopJeiPlugin implements IModPlugin
{
    private final @Nonnull WorkshopGhostIngredientHandler ghostIngredientHandler = new WorkshopGhostIngredientHandler();
    private IJeiRuntime jeiRuntime;
    private boolean registeredMouseHandler = false;

    @Override
    public @NotNull ResourceLocation getPluginUid()
    {
        return ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "jei_plugin");
    }

    /**
     * Registers a JEI GUI handler for the BOScreen class.
     * <p>
     * The handler provides a set of properties that are used by JEI to determine the position and size of the GUI.
     * <p>
     * The handler is only used when the BOScreen is open, and is used to determine the position of the workshop window within the GUI.
     * <p>
     * The handler is also used to register a ghost ingredient handler, which is used to display the currently selected ingredient in the workshop window.
     */
    @Override
    public void registerGuiHandlers(@Nonnull final IGuiHandlerRegistration registration)
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

    @Override
    public void onRuntimeAvailable(@Nonnull final IJeiRuntime jeiRuntime)
    {
        this.jeiRuntime = jeiRuntime;
        if (!this.registeredMouseHandler)
        {
            NeoForge.EVENT_BUS.addListener(this::onMouseButtonPressed);
            this.registeredMouseHandler = true;
        }
    }

    @Override
    public void onRuntimeUnavailable()
    {
        this.jeiRuntime = null;
    }

    /**
     * Called when a mouse button is pressed while the user is inside a JEI screen.
     * This method handles the logic for selecting a JEI output to send to the workshop
     * module.
     *
     * @param event the event that triggered this method
     */
    private void onMouseButtonPressed(final ScreenEvent.MouseButtonPressed.Pre event)
    {
        if (event.getButton() != 0 || this.jeiRuntime == null || !(event.getScreen() instanceof BOScreen gui))
        {
            return;
        }

        final WindowWorkshopModule workshopWindow = WorkshopGhostIngredientHandler.getWorkshopWindow(gui);
        if (workshopWindow == null)
        {
            return;
        }

        final ItemStack hoveredStack = this.jeiRuntime.getIngredientListOverlay()
            .getIngredientUnderMouse()
            .flatMap(ITypedIngredient::getItemStack)
            .map(ItemStack::copy)
            .orElse(ItemStack.EMPTY);
        if (hoveredStack.isEmpty())
        {
            return;
        }

        if (Screen.hasShiftDown())
        {
            workshopWindow.selectJeiOutput(hoveredStack);
            event.setCanceled(true);
            return;
        }
    }
}
