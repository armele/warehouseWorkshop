package com.deathfrog.warehouseworkshop.core.colony.buildings.modules;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Stores workshop settings per player and per building, while lazily migrating
 * legacy module-level defaults for upgraded worlds.
 */
public record WorkshopPlayerSettings(OutputTarget outputTarget, boolean includePlayerInventory)
{
    private static final String TAG_MOD_DATA = WarehouseWorkshopMod.MODID;
    private static final String TAG_WORKSHOP_SETTINGS = "workshopSettings";
    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_OUTPUT_TARGET = "outputTarget";
    private static final String TAG_INCLUDE_PLAYER_INVENTORY = "includePlayerInventory";

    public static final WorkshopPlayerSettings DEFAULT = new WorkshopPlayerSettings(OutputTarget.PLAYER_INVENTORY, false);

    /**
     * Retrieves the workshop settings for the given player and building position.
     *
     * If the settings have been previously stored, they are returned directly.
     * Otherwise, the method attempts to migrate the legacy module-level defaults
     * for the given building, and saves the migrated settings before returning them.
     *
     * @param player the player to retrieve the settings for
     * @param buildingPos the position of the building to retrieve the settings for
     * @param legacyModule the legacy module to migrate the settings from, or null if no migration is needed
     * @return the retrieved or migrated workshop settings
     */
    public static WorkshopPlayerSettings get(final Player player, final BlockPos buildingPos, @Nullable final WorkshopModule legacyModule)
    {
        final CompoundTag entry = getSettingsEntry(player, buildingPos, false);
        if (entry != null)
        {
            return fromTag(entry);
        }

        final WorkshopPlayerSettings migrated = legacyModule == null
            ? DEFAULT
            : new WorkshopPlayerSettings(legacyModule.getOutputTarget(), legacyModule.shouldIncludePlayerInventory());
        save(player, buildingPos, migrated);
        return migrated;
    }

    /**
     * Persists the given workshop settings for the given player and building position.
     *
     * @param player the player to persist the settings for
     * @param buildingPos the building position
     * @param settings the settings to persist
     */
    public static void save(final Player player, final BlockPos buildingPos, final WorkshopPlayerSettings settings)
    {
        final CompoundTag entry = getSettingsEntry(player, buildingPos, true);

        if (entry == null)
        {
            return;
        }

        entry.putInt(TAG_OUTPUT_TARGET, settings.outputTarget().getId());
        entry.putBoolean(TAG_INCLUDE_PLAYER_INVENTORY, settings.includePlayerInventory());
    }

    private static WorkshopPlayerSettings fromTag(final CompoundTag tag)
    {
        return new WorkshopPlayerSettings(
            OutputTarget.byId(tag.getInt(TAG_OUTPUT_TARGET)),
            tag.getBoolean(TAG_INCLUDE_PLAYER_INVENTORY));
    }

    /**
     * Gets the compound tag for the given player and building position.
     *
     * The hierarchy for this tag is as follows:
     * - Player.PERSISTED_NBT_TAG
     *   - TAG_MOD_DATA
     *     - TAG_WORKSHOP_SETTINGS
     *       - TAG_DIMENSIONS
     *         - Dimension location as a string
     *           - The building key (see {@link #getBuildingKey(BlockPos)})
     *
     * @param player the player to get the tag for
     * @param buildingPos the building position
     * @param create whether to create the tag if it doesn't exist
     * @return the compound tag or null if it couldn't be found or created
     */
    @Nullable
    private static CompoundTag getSettingsEntry(final Player player, final BlockPos buildingPos, final boolean create)
    {
        final CompoundTag playerData = player.getPersistentData();
        final CompoundTag persisted = getOrCreateChild(playerData, Player.PERSISTED_NBT_TAG, create);
        if (persisted == null)
        {
            return null;
        }

        final CompoundTag modData = getOrCreateChild(persisted, TAG_MOD_DATA, create);
        if (modData == null)
        {
            return null;
        }

        final CompoundTag workshopSettings = getOrCreateChild(modData, TAG_WORKSHOP_SETTINGS, create);
        if (workshopSettings == null)
        {
            return null;
        }

        final CompoundTag dimensions = getOrCreateChild(workshopSettings, TAG_DIMENSIONS, create);
        if (dimensions == null)
        {
            return null;
        }

        final CompoundTag dimensionSettings = getOrCreateChild(dimensions, player.level().dimension().location().toString() + "", create);
        if (dimensionSettings == null)
        {
            return null;
        }

        return getOrCreateChild(dimensionSettings, getBuildingKey(buildingPos), create);
    }

    /**
     * Retrieves a child compound tag from the given parent, or creates a new one if it does not exist.
     * 
     * @param parent the parent compound tag
     * @param key the key of the child to retrieve or create
     * @param create whether to create a new child compound tag if it does not exist
     * @return the child compound tag, or null if create is false and the child does not exist
     */
    @Nullable
    private static CompoundTag getOrCreateChild(final CompoundTag parent, final @Nonnull String key, final boolean create)
    {
        if (parent.contains(key, Tag.TAG_COMPOUND))
        {
            return parent.getCompound(key);
        }

        if (!create)
        {
            return null;
        }

        final CompoundTag child = new CompoundTag();
        parent.put(key, child);
        return child;
    }

    /**
     * Generates a unique key for a building based on its position.
     * 
     * The key is of the form "x,y,z" where x, y and z are the coordinates of the building.
     * 
     * @param buildingPos the position of the building to generate a key for
     * @return a unique key for the building based on its position  
     */
    private static @Nonnull String getBuildingKey(final BlockPos buildingPos)
    {
        return buildingPos.getX() + "," + buildingPos.getY() + "," + buildingPos.getZ();
    }
}
