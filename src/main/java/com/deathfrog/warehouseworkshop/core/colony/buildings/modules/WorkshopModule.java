package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.jetbrains.annotations.NotNull;

public class WorkshopModule extends AbstractBuildingModule implements IPersistentModule
{
    private static final String TAG_OUTPUT_TARGET = "outputTarget";

    private OutputTarget outputTarget = OutputTarget.PLAYER_INVENTORY;

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
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        if (compound.contains(TAG_OUTPUT_TARGET))
        {
            outputTarget = OutputTarget.byId(compound.getInt(TAG_OUTPUT_TARGET));
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        compound.putInt(TAG_OUTPUT_TARGET, outputTarget.id);
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(outputTarget.id);
    }

    public OutputTarget getOutputTarget()
    {
        return outputTarget;
    }

    public void setOutputTarget(final OutputTarget outputTarget)
    {
        if (this.outputTarget != outputTarget)
        {
            this.outputTarget = outputTarget;
            markDirty();
        }
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
