package com.deathfrog.warehouseworkshop.core.compatibility.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests slot translation for crafting inputs whose empty borders are trimmed by Minecraft.
 */
class PositionedCraftingSlotsTest
{
    /**
     * Verifies a two-column recipe in the left side of the full grid.
     */
    @Test
    void mapsLeftAlignedRecipe()
    {
        assertEquals(0, PositionedCraftingSlots.toFullGridSlot(2, 6, 0, 0, 0, 3, 3));
        assertEquals(6, PositionedCraftingSlots.toFullGridSlot(2, 6, 0, 0, 4, 3, 3));
        assertEquals(7, PositionedCraftingSlots.toFullGridSlot(2, 6, 0, 0, 5, 3, 3));
    }

    /**
     * Verifies the same recipe shifted to the right side of the full grid.
     */
    @Test
    void mapsRightAlignedRecipe()
    {
        assertEquals(1, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 0, 3, 3));
        assertEquals(7, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 4, 3, 3));
        assertEquals(8, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 5, 3, 3));
    }

    /**
     * Verifies that mirrored contents do not affect positional translation.
     */
    @Test
    void mapsMirroredRightAlignedRecipe()
    {
        assertEquals(1, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 0, 3, 3));
        assertEquals(7, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 4, 3, 3));
        assertEquals(8, PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 5, 3, 3));
        assertEquals(1, PositionedCraftingSlots.toShapedRecipeSlot(0, 2, true));
        assertEquals(0, PositionedCraftingSlots.toShapedRecipeSlot(1, 2, true));
        assertEquals(5, PositionedCraftingSlots.toShapedRecipeSlot(4, 2, true));
        assertEquals(4, PositionedCraftingSlots.toShapedRecipeSlot(5, 2, true));
    }

    /**
     * Verifies that invalid remainder indices fail before they can address the full grid.
     */
    @Test
    void rejectsSlotOutsideTrimmedInput()
    {
        assertThrows(IndexOutOfBoundsException.class,
            () -> PositionedCraftingSlots.toFullGridSlot(2, 6, 1, 0, 7, 3, 3));
    }
}
