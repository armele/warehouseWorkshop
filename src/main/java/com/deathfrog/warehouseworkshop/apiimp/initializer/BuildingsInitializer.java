package com.deathfrog.warehouseworkshop.apiimp.initializer;

import java.util.List;

import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer;

import net.neoforged.neoforge.registries.DeferredHolder;


public class BuildingsInitializer
{
    /**
     * Extends the existing buildings with the Workshop specific module(s)
     * if they are not already present
     */
    public static void injectBuildingModules()
    {

        // Get the existing entry to extend
        final DeferredHolder<BuildingEntry,BuildingEntry> warehouse = com.minecolonies.api.colony.buildings.ModBuildings.wareHouse;

        injectModuleToBuilding(BuildingModules.WORKSHOP_MODULE, warehouse, 3);
    }

    /**
     * Injects a module into an existing building entry if it is not already present.
     * 
     * @param producer the module to inject
     * @param buildingEntry the building to inject into
     * If the module is not already present, it will be added to the end of the module list.
     * If the module is already present, it will not be injected again.
     * If the buildingEntry is not bound, this method does nothing.
     */
    public static void injectModuleToBuilding(ModuleProducer<?, ?> producer, DeferredHolder<BuildingEntry,BuildingEntry> buildingEntryHolder) 
    {
        injectModuleToBuilding(producer, buildingEntryHolder, -1);
    }

    /**
     * Injects a module into an existing building entry if it is not already present
     *
     * @param producer the module to inject
     * @param buildingEntry the building to inject into
     */
    public static void injectModuleToBuilding(ModuleProducer<?, ?> producer, DeferredHolder<BuildingEntry,BuildingEntry> buildingEntryHolder, int position) 
    {
        if (buildingEntryHolder == null || !buildingEntryHolder.isBound()) return;
        BuildingEntry buildingEntry = buildingEntryHolder.get();

        @SuppressWarnings("rawtypes")
        final List<ModuleProducer> modules = buildingEntry.getModuleProducers();

        if (modules.stream().noneMatch(mp -> mp.key.equals(producer.key))) 
        {
            if (position == -1 || position >= modules.size())
            {    
                modules.add(producer);
            }
            else
            {
                modules.add(position, producer);
            }
        }
    }
}
