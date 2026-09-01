package com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews;

import com.deathfrog.warehouseworkshop.core.client.ResearchSuppliesClientHooks;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

/** Client-side university module view for research supply staging. */
public class ResearchSuppliesModuleView extends AbstractBuildingModuleView
{
    /** Accepts module synchronization; live content is supplied by dedicated payloads. */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buffer)
    {
    }

    /** Returns the translated tab description. */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.warehouseworkshop.core.gui.modules.research_supplies");
    }

    /** Opens the reusable research supplies window. */
    @Override
    public BOWindow getWindow()
    {
        return ResearchSuppliesClientHooks.createUniversityWindow(this);
    }

    /** Returns the supplied module icon name. */
    @Override
    public String getIcon()
    {
        return ResearchSuppliesConstants.MODULE_ICON;
    }
}
