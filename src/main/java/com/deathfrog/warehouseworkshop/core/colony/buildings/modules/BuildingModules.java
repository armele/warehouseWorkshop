package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

public class BuildingModules
{
    public static final BuildingEntry.ModuleProducer<WorkshopModule, WorkshopModuleView> WORKSHOP_MODULE     =
      new BuildingEntry.ModuleProducer<WorkshopModule, WorkshopModuleView>("workshop", () -> new WorkshopModule(), () -> WorkshopModuleView::new);

    /** Module that stages and optionally starts university research. */
    public static final BuildingEntry.ModuleProducer<ResearchSuppliesModule, com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.ResearchSuppliesModuleView> RESEARCH_SUPPLIES_MODULE =
      new BuildingEntry.ModuleProducer<>(ResearchSuppliesConstants.MODULE_ID, ResearchSuppliesModule::new,
        () -> com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.ResearchSuppliesModuleView::new);
}
