package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.ResearchSuppliesModuleView;
import com.deathfrog.warehouseworkshop.core.network.RequestResearchSuppliesMessage;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

/** University-hosted research supply staging window. */
public class WindowResearchSupplies extends AbstractModuleWindow<ResearchSuppliesModuleView> implements ResearchSuppliesReceiver
{
    private final BlockPos universityPos;
    private final ResearchSuppliesListController controller;
    private int refreshCooldown;

    /** Creates the university module window and its reusable list controller. */
    public WindowResearchSupplies(final ResearchSuppliesModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, ResearchSuppliesConstants.WINDOW_LAYOUT));
        universityPos = moduleView.getBuildingView().getPosition();
        controller = new ResearchSuppliesListController(findPaneOfTypeByID(ResearchSuppliesUiConstants.RESEARCH_LIST_ID, ScrollingList.class), universityPos);
    }

    /** Requests fresh authoritative content whenever the window opens. */
    @Override
    public void onOpened()
    {
        super.onOpened();
        attachHelpTooltip();
        requestRefresh();
        refreshCooldown = ResearchSuppliesUiConstants.AUTO_REFRESH_TICKS;
    }

    /** Periodically requests authoritative supply data while the window remains open. */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (--refreshCooldown <= 0)
        {
            requestRefresh();
            refreshCooldown = ResearchSuppliesUiConstants.AUTO_REFRESH_TICKS;
        }
    }

    /** Routes dynamically identified row actions to the shared controller. */
    @Override
    public void onButtonClicked(@NotNull final Button button)
    {
        super.onButtonClicked(button);
        controller.handleButton(button);
    }

    /** Applies a matching server snapshot. */
    @Override
    public void receiveResearchSupplies(final BlockPos position, final List<ResearchStatus> statuses)
    {
        if (universityPos.equals(position)) controller.setStatuses(statuses);
    }

    /** Requests a refreshed server snapshot. */
    private void requestRefresh()
    {
        new RequestResearchSuppliesMessage(universityPos).sendToServer();
    }

    /** Attaches the translated feature guide to the header help icon. */
    private void attachHelpTooltip()
    {
        final Image help = findPaneOfTypeByID(ResearchSuppliesUiConstants.HELP_ICON_ID, Image.class);
        PaneBuilders.tooltipBuilder().hoverPane(help).build()
            .setText(Component.translatable("com.warehouseworkshop.research_supplies.help"));
    }
}
