package com.deathfrog.warehouseworkshop.core.compatibility.jei;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public class WorkshopGhostIngredientHandler implements IGhostIngredientHandler<BOScreen>
{
    private static final int[][] GRID_POSITIONS = {
        {18, 44}, {36, 44}, {54, 44},
        {18, 62}, {36, 62}, {54, 62},
        {18, 80}, {36, 80}, {54, 80}
    };

    private static final int SLOT_SIZE = 18;
    private static final int REQUEST_PREVIEW_X = 113;
    private static final int REQUEST_PREVIEW_Y = 56;

    @Override
    public <I> List<Target<I>> getTargetsTyped(@NotNull final BOScreen gui, @NotNull final ITypedIngredient<I> ingredient, final boolean doStart)
    {
        final WindowWorkshopModule workshopWindow = getWorkshopWindow(gui);
        if (workshopWindow == null || ingredient.getType().getIngredientClass() != ItemStack.class)
        {
            return List.of();
        }

        final List<Target<I>> targets = new ArrayList<>();
        for (int slot = 0; slot < GRID_POSITIONS.length; slot++)
        {
            final int targetSlot = slot;
            if (!workshopWindow.canAcceptIngredientInSlot(targetSlot))
            {
                continue;
            }

            targets.add(new Target<>()
            {
                @Override
                public @NotNull Rect2i getArea()
                {
                    return getScaledArea(gui, GRID_POSITIONS[targetSlot][0], GRID_POSITIONS[targetSlot][1], SLOT_SIZE, SLOT_SIZE);
                }

                @Override
                public void accept(final I dropped)
                {
                    workshopWindow.applyJeiIngredientToSlot(targetSlot, ((ItemStack) dropped).copy());
                }
            });
        }

        targets.add(new Target<>()
        {
            @Override
            public @NotNull Rect2i getArea()
            {
                return getScaledArea(gui, REQUEST_PREVIEW_X, REQUEST_PREVIEW_Y, SLOT_SIZE, SLOT_SIZE);
            }

            @Override
            public void accept(final I dropped)
            {
                workshopWindow.selectJeiOutput(((ItemStack) dropped).copy());
            }
        });

        return targets;
    }

    @Override
    public void onComplete()
    {
    }

    private static Rect2i getScaledArea(final BOScreen gui, final int logicalX, final int logicalY, final int logicalWidth, final int logicalHeight)
    {
        final ScreenBounds bounds = ScreenBounds.of(gui);
        final int x = bounds.guiLeft() + scale(bounds.scaleFactor(), logicalX);
        final int y = bounds.guiTop() + scale(bounds.scaleFactor(), logicalY);
        final int width = Math.max(1, scale(bounds.scaleFactor(), logicalWidth));
        final int height = Math.max(1, scale(bounds.scaleFactor(), logicalHeight));
        return new Rect2i(x, y, width, height);
    }

    private static int scale(final double factor, final int value)
    {
        return (int) Math.round(value * factor);
    }

    static WindowWorkshopModule getWorkshopWindow(final BOScreen gui)
    {
        final BOWindow window = gui.getWindow();
        return window instanceof WindowWorkshopModule workshopWindow ? workshopWindow : null;
    }

    record ScreenBounds(int guiLeft, int guiTop, double scaleFactor)
    {
        static ScreenBounds of(final BOScreen gui)
        {
            final BOWindow window = gui.getWindow();
            final double scaleFactor = gui.getRenderScale() / gui.getVanillaGuiScale();
            final int guiWidth = Math.max(gui.getFramebufferWidth(), 320);
            final int guiHeight = Math.max(gui.getFramebufferHeight(), 240);
            final int guiLeft = (int) Math.round((guiWidth - (window.getWidth() * gui.getRenderScale())) / (2.0D * gui.getVanillaGuiScale()));
            final int guiTop = (int) Math.round((guiHeight - (window.getHeight() * gui.getRenderScale())) / (2.0D * gui.getVanillaGuiScale()));
            return new ScreenBounds(guiLeft, guiTop, scaleFactor);
        }
    }
}
