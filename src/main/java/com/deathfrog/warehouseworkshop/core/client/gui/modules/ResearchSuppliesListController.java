package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import com.deathfrog.warehouseworkshop.core.network.CreateResearchDeliveryMessage;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.CostStatus;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.AbstractTextBuilder.AutomaticTooltipBuilder;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.Tooltip;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchBranch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;

/** Shared list controller used by university and portable research-supply windows. */
public final class ResearchSuppliesListController implements ScrollingList.DataProvider
{
    private static final int VISIBLE_COSTS = 4;
    private final ScrollingList list;
    private final BlockPos universityPos;
    private List<ResearchStatus> statuses = List.of();

    /** Creates and attaches a shared research list controller. */
    public ResearchSuppliesListController(final ScrollingList list, final BlockPos universityPos)
    {
        this.list = list;
        this.universityPos = universityPos;
        list.setDataProvider(this);
    }

    /** Replaces the displayed snapshot. */
    public void setStatuses(final List<ResearchStatus> statuses)
    {
        this.statuses = List.copyOf(statuses);
        list.refreshElementPanes();
    }

    /** Returns the number of research rows. */
    @Override
    public int getElementCount()
    {
        return statuses.size();
    }

    /** Populates one research row, including counts, status colors, and actions. */
    @SuppressWarnings("null")
    @Override
    public void updateElement(final int index, final Pane row)
    {
        final ResearchStatus status = statuses.get(index);
        row.setID(status.branch() + ResearchSuppliesUiConstants.ACTION_VALUE_SEPARATOR + status.research());
        final Text researchName = row.findPaneOfTypeByID(ResearchSuppliesUiConstants.RESEARCH_NAME_ID, Text.class);
        final Text researchState = row.findPaneOfTypeByID(ResearchSuppliesUiConstants.RESEARCH_STATE_ID, Text.class);
        researchName.setText(getQualifiedResearchName(status));
        researchState.setText(Component.translatable(status.pending()
            ? status.autoStart() ? "com.warehouseworkshop.research_supplies.pending_start" : "com.warehouseworkshop.research_supplies.pending"
            : status.deliverable() ? "com.warehouseworkshop.research_supplies.available" : "com.warehouseworkshop.research_supplies.incomplete"));
        setTooltip(researchName, getResearchTooltip(status));
        setTooltip(researchState, getStartStateTooltip(status));
        for (int i = 0; i < VISIBLE_COSTS; i++)
        {
            final ItemIcon icon = row.findPaneOfTypeByID(ResearchSuppliesUiConstants.COST_ICON_ID_PREFIX + i, ItemIcon.class);
            final Text marker = row.findPaneOfTypeByID(ResearchSuppliesUiConstants.COST_STATE_ID_PREFIX + i, Text.class);
            if (i >= status.costs().size())
            {
                icon.hide();
                marker.hide();
                continue;
            }
            final CostStatus cost = status.costs().get(i);
            icon.show();
            marker.show();
            icon.setItem(cost.display());
            icon.setHoverPane(null);
            new AutomaticTooltipBuilder().hoverPane(icon).build();
            final int deficit = Math.max(0, cost.required() - cost.university());
            final String color = deficit == 0 ? ResearchSuppliesUiConstants.COLOR_BLUE
                : cost.warehouse() >= deficit ? ResearchSuppliesUiConstants.COLOR_GREEN
                : cost.warehouse() > 0 ? ResearchSuppliesUiConstants.COLOR_ORANGE : ResearchSuppliesUiConstants.COLOR_RED;
            marker.setText(Component.literal(Integer.toString(Math.min(cost.required(), cost.university() + cost.warehouse()))));
            marker.setColors(Color.getByName(color, 0));
        }
        configureButton(row.findPaneOfTypeByID(ResearchSuppliesUiConstants.DELIVER_BUTTON_ID, Button.class), status);
        configureButton(row.findPaneOfTypeByID(ResearchSuppliesUiConstants.DELIVER_START_BUTTON_ID, Button.class), status);
    }

    /** Returns the localized branch-qualified research name. */
    @SuppressWarnings("null")
    private static Component getQualifiedResearchName(final ResearchStatus status)
    {
        final IGlobalResearchBranch branch = IGlobalResearchTree.getInstance().getBranchData(status.branch());
        final Component branchName = branch == null ? Component.literal(status.branch().getPath()) : MutableComponent.create(branch.getName());
        return Component.translatable("com.warehouseworkshop.research_supplies.qualified_name", branchName,
            Component.translatable(status.name()));
    }

    /** Builds the research details shown by MineColonies' Research tab. */
    @SuppressWarnings("null")
    private static Component getResearchTooltip(final ResearchStatus status)
    {
        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(status.branch(), status.research());
        if (research == null) return Component.translatable(status.name());
        final MutableComponent tooltip = MutableComponent.create(research.getName()).copy()
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (!research.getSubtitle().getKey().isEmpty())
        {
            appendLine(tooltip, MutableComponent.create(research.getSubtitle()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        for (final IResearchEffect effect : research.getEffects())
        {
            appendLine(tooltip, MutableComponent.create(effect.getName()));
            if (!effect.getSubtitle().getKey().isEmpty())
            {
                appendLine(tooltip, Component.literal("- ").append(MutableComponent.create(effect.getSubtitle()))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        research.getResearchRequirements().forEach(requirement -> appendLine(tooltip,
            Component.literal(" - ").append(requirement.getDesc()).withStyle(ChatFormatting.AQUA)));
        for (final SizedIngredient cost : research.getCostList())
        {
            final Component costName = cost.getItems().length == 0
                ? Component.literal("?") : cost.getItems()[0].getHoverName();
            appendLine(tooltip, Component.literal(" - ").append(Component.translatable(
                "com.minecolonies.coremod.research.limit.requirement", cost.count(), costName))
                .withStyle(ChatFormatting.AQUA));
        }
        return tooltip;
    }

    /** Returns the server-derived automatic-start diagnostic for a row. */
    @SuppressWarnings("null")
    private static Component getStartStateTooltip(final ResearchStatus status)
    {
        final Component state;
        if (!status.pending())
        {
            state = Component.translatable(status.deliverable()
                ? "com.warehouseworkshop.research_supplies.start_state.not_requested"
                : "com.warehouseworkshop.research_supplies.start_state.supply_incomplete");
        }
        else
        {
            state = switch (status.startState())
            {
                case DELIVERY_PENDING -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.delivery_pending");
                case MATERIALS_MISSING -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.materials_missing");
                case QUEUE_FULL -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.queue_full",
                    status.activeResearch(), status.researchCapacity());
                case NO_LONGER_ELIGIBLE -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.no_longer_eligible");
                case REQUIREMENTS_MISSING -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.requirements_missing");
                case READY -> Component.translatable(status.autoStart()
                    ? "com.warehouseworkshop.research_supplies.start_state.ready"
                    : "com.warehouseworkshop.research_supplies.start_state.delivered");
                case NOT_REQUESTED -> Component.translatable("com.warehouseworkshop.research_supplies.start_state.not_requested");
            };
        }
        final MutableComponent tooltip = state.copy();
        for (final CostStatus cost : status.costs())
        {
            final int stillMissing = Math.max(0, cost.required() - cost.university() - cost.warehouse());
            tooltip.append(Component.literal("\n\n")).append(Component.translatable(
                "com.warehouseworkshop.research_supplies.status_cost_tooltip", cost.display().getHoverName(),
                cost.required(), cost.university(), cost.warehouse(), stillMissing));
        }
        return tooltip;
    }

    /** Attaches or updates a tooltip on a reusable scrolling-list pane. */
    private static void setTooltip(final Pane pane, final Component text)
    {
        if (pane.getHoverPane() == null) PaneBuilders.tooltipBuilder().hoverPane(pane).build();
        ((Tooltip) pane.getHoverPane()).setText(text);
    }

    /** Appends a tooltip line while retaining the new line's formatting. */
    @SuppressWarnings("null")
    private static void appendLine(final MutableComponent tooltip, final Component line)
    {
        tooltip.append(Component.literal("\n")).append(line);
    }

    /** Enables a row action button without changing its stable XML component identifier. */
    private void configureButton(final Button button, final ResearchStatus status)
    {
        if (!status.deliverable() || status.pending()) button.disable(); else button.enable();
    }

    /** Handles a delivery action emitted by either host window. */
    @SuppressWarnings("null")
    public void handleButton(final Button button)
    {
        final String id = button.getID();
        final boolean autoStart = ResearchSuppliesUiConstants.DELIVER_START_BUTTON_ID.equals(id);
        if (!autoStart && !ResearchSuppliesUiConstants.DELIVER_BUTTON_ID.equals(id)) return;
        final Pane row = button.getParent();
        if (row == null) return;
        final String[] values = row.getID().split(ResearchSuppliesUiConstants.ACTION_VALUE_SEPARATOR_REGEX, 2);
        if (values.length == 2)
        {
            final net.minecraft.resources.ResourceLocation branch = net.minecraft.resources.ResourceLocation.tryParse(values[0]);
            final net.minecraft.resources.ResourceLocation research = net.minecraft.resources.ResourceLocation.tryParse(values[1]);
            if (branch != null && research != null)
            {
                new CreateResearchDeliveryMessage(universityPos, branch, research, autoStart).sendToServer();
                button.disable();
            }
        }
    }
}
