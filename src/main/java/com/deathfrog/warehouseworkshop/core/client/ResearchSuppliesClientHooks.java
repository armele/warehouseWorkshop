package com.deathfrog.warehouseworkshop.core.client;

import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.ResearchSuppliesModuleView;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import com.ldtteam.blockui.views.BOWindow;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Common-side indirection for optional client behavior.
 *
 * <p>The default handlers do nothing and reference no Minecraft client classes. The client-only
 * mod entry point installs the real GUI handlers during client construction.</p>
 */
public final class ResearchSuppliesClientHooks
{
    private static Consumer<BlockPos> ledgerOpener = ignored -> { };
    private static Function<ResearchSuppliesModuleView, BOWindow> universityWindowFactory = ignored -> null;
    private static SnapshotHandler snapshotHandler = (position, statuses) -> { };

    /** Prevents utility-class construction. */
    private ResearchSuppliesClientHooks()
    {
    }

    /** Installs all research-supplies client handlers from the client-only entry point. */
    public static void register(
        final Consumer<BlockPos> ledgerWindowOpener,
        final Function<ResearchSuppliesModuleView, BOWindow> moduleWindowFactory,
        final SnapshotHandler clientSnapshotHandler)
    {
        ledgerOpener = ledgerWindowOpener;
        universityWindowFactory = moduleWindowFactory;
        snapshotHandler = clientSnapshotHandler;
    }

    /** Opens the portable ledger window when client handlers are installed. */
    public static void openLedger(final BlockPos universityPos)
    {
        ledgerOpener.accept(universityPos);
    }

    /** Creates the university module window when client handlers are installed. */
    public static BOWindow createUniversityWindow(final ResearchSuppliesModuleView moduleView)
    {
        return universityWindowFactory.apply(moduleView);
    }

    /** Routes a decoded snapshot without linking the common payload class to client classes. */
    public static void handleSnapshot(final BlockPos universityPos, final List<ResearchStatus> statuses)
    {
        snapshotHandler.handle(universityPos, statuses);
    }

    /** Functional client handler for decoded research-supply snapshots. */
    @FunctionalInterface
    public interface SnapshotHandler
    {
        /** Applies one decoded snapshot on the client thread. */
        void handle(BlockPos universityPos, List<ResearchStatus> statuses);
    }
}
