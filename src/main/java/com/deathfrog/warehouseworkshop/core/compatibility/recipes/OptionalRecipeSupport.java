package com.deathfrog.warehouseworkshop.core.compatibility.recipes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Soft integrations for optional recipe implementations from other mods.
 */
public final class OptionalRecipeSupport
{
    private static final ResourceLocation UNIQUE_TAG_SHAPELESS_SERIALIZER_ID =
        ResourceLocation.fromNamespaceAndPath("mctradepost", "unique_tag_shapeless");

    private OptionalRecipeSupport()
    {
        // Utility class.
    }

    public static List<CraftingSlotRequirement> buildCraftingSlotRequirements(final CraftingRecipe recipe, final int gridSize)
    {
        final Optional<UniqueTagRecipeDefinition> uniqueTagRecipe = tryGetUniqueTagRecipe(recipe);
        if (uniqueTagRecipe.isPresent())
        {
            return buildUniqueTagRequirements(uniqueTagRecipe.get(), gridSize);
        }

        return buildDefaultRequirements(recipe, gridSize);
    }

    private static List<CraftingSlotRequirement> buildDefaultRequirements(final CraftingRecipe recipe, final int gridSize)
    {
        final List<CraftingSlotRequirement> slots = new ArrayList<>(Collections.nCopies(gridSize, CraftingSlotRequirement.empty()));
        final List<Ingredient> ingredients = recipe.getIngredients();

        if (recipe instanceof final ShapedRecipe shapedRecipe)
        {
            final int width = shapedRecipe.getWidth();
            final int height = shapedRecipe.getHeight();
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    slots.set((y * 3) + x, new CraftingSlotRequirement(ingredients.get((y * width) + x), null));
                }
            }
        }
        else if (recipe instanceof final ShapelessRecipe shapelessRecipe)
        {
            for (int i = 0; i < Math.min(gridSize, shapelessRecipe.getIngredients().size()); i++)
            {
                slots.set(i, new CraftingSlotRequirement(shapelessRecipe.getIngredients().get(i), null));
            }
        }
        else
        {
            for (int i = 0; i < Math.min(gridSize, ingredients.size()); i++)
            {
                slots.set(i, new CraftingSlotRequirement(ingredients.get(i), null));
            }
        }

        return slots;
    }

    private static List<CraftingSlotRequirement> buildUniqueTagRequirements(final UniqueTagRecipeDefinition recipe, final int gridSize)
    {
        final List<CraftingSlotRequirement> slots = new ArrayList<>(Collections.nCopies(gridSize, CraftingSlotRequirement.empty()));
        if (gridSize <= 0)
        {
            return slots;
        }

        slots.set(0, new CraftingSlotRequirement(recipe.base(), null));
        for (int i = 1; i < Math.min(gridSize, recipe.count() + 1); i++)
        {
            slots.set(i, new CraftingSlotRequirement(recipe.tagIngredient(), recipe.uniqueGroup()));
        }

        return slots;
    }

    /**
     * Tries to extract a unique tag recipe definition from the given recipe.
     * <p>
     * This method is used to support soft integrations with other mods that use a custom shapeless recipe serializer.
     * <p>
     * The method first checks if the recipe's serializer is the custom shapeless recipe serializer.
     * If it is, the method then tries to reflectively call the recipe's "getBase", "getTag", and "getCount" methods.
     * If all of these calls succeed, the method then wraps the returned values in a {@link UniqueTagRecipeDefinition} and returns it.
     * If any of the calls fail, the method returns an empty optional.
     * <p>
     * The custom shapeless recipe serializer is identified by the resource location "mctradepost:unique_tag_shapeless".
     * The "getBase", "getTag", and "getCount" methods are expected to return an {@link Ingredient}, a {@link TagKey}, and an {@link Integer}, respectively.
     * The base ingredient is the ingredient that the tag is applied to.
     * The tag ingredient is the ingredient that the tag is applied to.
     * The count is the number of times to apply the tag to the base ingredient.
     * <p>
     * The returned {@link UniqueTagRecipeDefinition} contains the base ingredient, the tag ingredient, the count, and the resource location of the tag.
     * @param recipe the recipe to extract the unique tag recipe definition from
     * @return an optional containing the extracted unique tag recipe definition, or an empty optional if the extraction fails
     */
    private static Optional<UniqueTagRecipeDefinition> tryGetUniqueTagRecipe(final CraftingRecipe recipe)
    {
        RecipeSerializer<?> serializer = recipe.getSerializer();
        
        if (serializer == null)
        {
            return Optional.empty();
        }

        final ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
        if (!UNIQUE_TAG_SHAPELESS_SERIALIZER_ID.equals(serializerId))
        {
            return Optional.empty();
        }

        try
        {
            final Method getBase = recipe.getClass().getMethod("getBase");
            final Method getTag = recipe.getClass().getMethod("getTag");
            final Method getCount = recipe.getClass().getMethod("getCount");

            final Object baseValue = getBase.invoke(recipe);
            final Object tagValue = getTag.invoke(recipe);
            final Object countValue = getCount.invoke(recipe);

            if (!(baseValue instanceof Ingredient base)
                || !(tagValue instanceof TagKey<?> rawTag)
                || !(countValue instanceof Integer count)
                || count.intValue() <= 0)
            {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            final TagKey<Item> tag = (TagKey<Item>) rawTag;
            final Ingredient tagIngredient = Ingredient.of(tag);
            if (tagIngredient.isEmpty())
            {
                return Optional.empty();
            }

            return Optional.of(new UniqueTagRecipeDefinition(base, tagIngredient, count.intValue(), tag.location()));
        }
        catch (final ReflectiveOperationException | RuntimeException ignored)
        {
            return Optional.empty();
        }
    }

    public record CraftingSlotRequirement(Ingredient ingredient, @Nullable ResourceLocation uniqueGroup)
    {
        public static CraftingSlotRequirement empty()
        {
            return new CraftingSlotRequirement(Ingredient.EMPTY, null);
        }
    }

    private record UniqueTagRecipeDefinition(
        Ingredient base,
        Ingredient tagIngredient,
        int count,
        ResourceLocation uniqueGroup)
    {
    }
}
