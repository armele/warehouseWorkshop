package com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowWorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

public class WorkshopModuleView  extends AbstractBuildingModuleView
{
    private OutputTarget outputTarget = OutputTarget.PLAYER_INVENTORY;

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf arg0)
    {
        outputTarget = OutputTarget.byId(arg0.readInt());
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.warehouseworkshop.core.gui.modules.workshop");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowWorkshopModule(this);
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public String getIcon()
    {
        return "crafting";
    }

    public OutputTarget getOutputTarget()
    {
        return outputTarget;
    }

    public void setOutputTarget(final OutputTarget outputTarget)
    {
        this.outputTarget = outputTarget;
    }
}
