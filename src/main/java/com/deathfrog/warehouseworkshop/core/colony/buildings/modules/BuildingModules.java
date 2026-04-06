package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import com.deathfrog.warehouseworkshop.api.colony.buildings.moduleviews.WorkshopModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BuildingModules
{
    public static final BuildingEntry.ModuleProducer<WorkshopModule, WorkshopModuleView> WORKSHOP_MODULE     =
      new BuildingEntry.ModuleProducer<WorkshopModule, WorkshopModuleView>("workshop", () -> new WorkshopModule(), () -> WorkshopModuleView::new);
}
