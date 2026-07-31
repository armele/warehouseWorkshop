package com.deathfrog.warehouseworkshop.core.compatibility.recipes;

import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Translates slots in Minecraft's trimmed crafting input back to the original crafting grid.
 */
public final class PositionedCraftingSlots
{
    private PositionedCraftingSlots()
    {
        // Utility class.
    }

    /**
     * Maps a slot in a positioned, trimmed crafting input to its slot in the full grid.
     *
     * @param positioned the positioned crafting input
     * @param localSlot the slot in the trimmed input
     * @param gridWidth the width of the original crafting grid
     * @param gridHeight the height of the original crafting grid
     * @return the corresponding full-grid slot
     * @throws IndexOutOfBoundsException if either the local or mapped slot is outside its grid
     */
    public static int toFullGridSlot(
        final CraftingInput.Positioned positioned,
        final int localSlot,
        final int gridWidth,
        final int gridHeight)
    {
        final CraftingInput input = positioned.input();
        return toFullGridSlot(
            input.width(),
            input.size(),
            positioned.left(),
            positioned.top(),
            localSlot,
            gridWidth,
            gridHeight);
    }

    /**
     * Performs full-grid slot translation using explicit trimmed-input dimensions and offsets.
     * This overload isolates the coordinate arithmetic from Minecraft runtime initialization.
     *
     * @param inputWidth the width of the trimmed crafting input
     * @param inputSize the number of slots in the trimmed crafting input
     * @param left the trimmed input's horizontal offset in the full grid
     * @param top the trimmed input's vertical offset in the full grid
     * @param localSlot the slot in the trimmed input
     * @param gridWidth the width of the original crafting grid
     * @param gridHeight the height of the original crafting grid
     * @return the corresponding full-grid slot
     * @throws IndexOutOfBoundsException if either the local or mapped slot is outside its grid
     */
    static int toFullGridSlot(
        final int inputWidth,
        final int inputSize,
        final int left,
        final int top,
        final int localSlot,
        final int gridWidth,
        final int gridHeight)
    {
        if (inputWidth <= 0 || localSlot < 0 || localSlot >= inputSize)
        {
            throw new IndexOutOfBoundsException("Local crafting slot " + localSlot + " is outside input size " + inputSize);
        }

        final int localX = localSlot % inputWidth;
        final int localY = localSlot / inputWidth;
        final int fullX = left + localX;
        final int fullY = top + localY;
        if (fullX < 0 || fullX >= gridWidth || fullY < 0 || fullY >= gridHeight)
        {
            throw new IndexOutOfBoundsException(
                "Mapped crafting position " + fullX + "," + fullY + " is outside " + gridWidth + "x" + gridHeight);
        }

        return (fullY * gridWidth) + fullX;
    }

    /**
     * Maps a local input slot to its canonical shaped-recipe slot.
     *
     * @param localSlot the slot in the trimmed input
     * @param inputWidth the width shared by the input and shaped recipe
     * @param mirrored whether the recipe matched horizontally mirrored
     * @return the corresponding slot in the recipe pattern
     * @throws IndexOutOfBoundsException if the slot or width is invalid
     */
    public static int toShapedRecipeSlot(final int localSlot, final int inputWidth, final boolean mirrored)
    {
        if (inputWidth <= 0 || localSlot < 0)
        {
            throw new IndexOutOfBoundsException("Invalid shaped recipe slot " + localSlot + " for width " + inputWidth);
        }

        final int localX = localSlot % inputWidth;
        final int localY = localSlot / inputWidth;
        final int recipeX = mirrored ? inputWidth - 1 - localX : localX;
        return (localY * inputWidth) + recipeX;
    }
}
