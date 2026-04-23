package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.jetbrains.annotations.NotNull;

public class WorkshopModule extends AbstractBuildingModule implements IPersistentModule
{
    /**
     * Create a new module.
     *
     * @param jobEntry the entry of the job.
     */
    public WorkshopModule()
    {
        super();
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final net.minecraft.nbt.CompoundTag compound)
    {
        // Per-player workshop settings are stored on the player, not the building module.
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final net.minecraft.nbt.CompoundTag compound)
    {
        // Legacy module-scoped workshop settings are intentionally no longer persisted.
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        // The client receives authoritative per-player settings via a dedicated packet.
    }

    public enum OutputTarget
    {
        PLAYER_INVENTORY(0),
        WAREHOUSE_INVENTORY(1);

        private final int id;

        OutputTarget(final int id)
        {
            this.id = id;
        }

        public boolean isWarehouse()
        {
            return this == WAREHOUSE_INVENTORY;
        }

        public int getId()
        {
            return id;
        }

        public static OutputTarget byId(final int id)
        {
            for (final OutputTarget target : values())
            {
                if (target.id == id)
                {
                    return target;
                }
            }

            return PLAYER_INVENTORY;
        }
    }
}
