package com.deathfrog.warehouseworkshop.core.client.gui.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static com.minecolonies.api.util.constant.WindowConstants.DELIVERY_IMAGE;
import static com.minecolonies.api.util.constant.WindowConstants.LIFE_COUNT_DIVIDER;
import static com.minecolonies.api.util.constant.WindowConstants.LIST_ELEMENT_ID_REQUEST_STACK;
import static com.minecolonies.api.util.constant.WindowConstants.REQUEST_SHORT_DETAIL;
import static com.minecolonies.api.util.constant.WindowConstants.REQUESTER;
import static com.minecolonies.core.colony.requestsystem.requests.AbstractRequest.MISSING;

/**
 * Request picker for the workshop module.
 */
public class WindowWorkshopSelectRequest extends AbstractModuleWindow<WorkshopModuleView>
{
    private final Predicate<IRequest<?>> predicate;
    private final Consumer<IRequest<?>> reopenWithRequest;
    private final ScrollingList requestsList;
    private List<IRequest<?>> openRequests = List.of();
    private int lifeCount;

    public WindowWorkshopSelectRequest(
        final WorkshopModuleView moduleView,
        final Predicate<IRequest<?>> predicate,
        final Consumer<@Nullable IRequest<?>> reopenWithRequest)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "gui/layouthuts/layoutselectworkshoprequest.xml"));
        this.predicate = predicate;
        this.reopenWithRequest = reopenWithRequest;
        this.requestsList = findPaneOfTypeByID("requests", ScrollingList.class);

        registerButton("select", this::select);
        registerButton("cancel", this::cancel);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        openRequests = getOpenRequests();
        updateRequests();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (!Screen.hasShiftDown())
        {
            lifeCount++;
        }
    }

    private List<IRequest<?>> getOpenRequests()
    {
        final List<IRequest<?>> requests = new ArrayList<>();
        final IRequestManager requestManager = buildingView.getColony().getRequestManager();
        final IPlayerRequestResolver resolver = requestManager.getPlayerResolver();
        final IRetryingRequestResolver retryingResolver = requestManager.getRetryingRequestResolver();
        final Set<IToken<?>> requestTokens = new HashSet<>();
        requestTokens.addAll(resolver.getAllAssignedRequests());
        requestTokens.addAll(retryingResolver.getAllAssignedRequests());
        final Set<IToken<?>> visited = new HashSet<>();

        for (final IToken<?> token : requestTokens)
        {
            IRequest<?> request = requestManager.getRequestForToken(token);
            while (request != null && request.hasParent())
            {
                request = requestManager.getRequestForToken(request.getParent());
            }

            if (request != null)
            {
                collectMatchingRequests(requestManager, request, requests, visited);
            }
        }

        return requests;
    }

    private void collectMatchingRequests(
        final IRequestManager requestManager,
        final IRequest<?> rootRequest,
        final List<IRequest<?>> requests,
        final Set<IToken<?>> visited)
    {
        final ArrayDeque<IRequest<?>> pending = new ArrayDeque<>();
        pending.add(rootRequest);

        while (!pending.isEmpty())
        {
            final IRequest<?> request = pending.removeFirst();
            if (!visited.add(request.getId()))
            {
                continue;
            }

            if (predicate.test(request))
            {
                requests.add(request);
            }

            for (final IToken<?> childToken : request.getChildren())
            {
                final IRequest<?> childRequest = requestManager.getRequestForToken(childToken);
                if (childRequest != null)
                {
                    pending.addLast(childRequest);
                }
            }
        }
    }

    private void cancel()
    {
        reopenWithRequest.accept(null);
    }

    private void select(@NotNull final Button button)
    {
        final int row = requestsList.getListElementIndexByPane(button);
        if (row >= 0 && row < openRequests.size())
        {
            reopenWithRequest.accept(openRequests.get(row));
        }
    }

    private void updateRequests()
    {
        requestsList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return openRequests.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                if (index < 0 || index >= openRequests.size())
                {
                    return;
                }

                final IRequest<?> request = openRequests.get(index);
                final ItemIcon stackIcon = rowPane.findPaneOfTypeByID(LIST_ELEMENT_ID_REQUEST_STACK, ItemIcon.class);
                final Image logo = rowPane.findPaneOfTypeByID(DELIVERY_IMAGE, Image.class);
                final List<ItemStack> displayStacks = request.getDisplayStacks();

                if (!displayStacks.isEmpty())
                {
                    logo.setVisible(false);
                    stackIcon.setVisible(true);
                    stackIcon.setItem(displayStacks.get((lifeCount / LIFE_COUNT_DIVIDER) % displayStacks.size()));
                }
                else
                {
                    stackIcon.setVisible(false);
                    if (!request.getDisplayIcon().equals(MISSING))
                    {
                        logo.setVisible(true);
                        logo.setImage(request.getDisplayIcon(), false);
                        PaneBuilders.tooltipBuilder().hoverPane(logo).build().setText(request.getResolverToolTip(buildingView.getColony()));
                    }
                }

                rowPane.findPaneOfTypeByID(REQUESTER, Text.class)
                    .setText(request.getRequester().getRequesterDisplayName(buildingView.getColony().getRequestManager(), request));

                if (request instanceof StandardRequests.ItemTagRequest && !displayStacks.isEmpty())
                {
                    rowPane.findPaneOfTypeByID(REQUEST_SHORT_DETAIL, Text.class)
                        .setText(displayStacks.get((lifeCount / LIFE_COUNT_DIVIDER) % displayStacks.size()).getHoverName());
                }
                else
                {
                    rowPane.findPaneOfTypeByID(REQUEST_SHORT_DETAIL, Text.class)
                        .setText(request.getShortDisplayString().copy());
                }
            }
        });
    }
}
