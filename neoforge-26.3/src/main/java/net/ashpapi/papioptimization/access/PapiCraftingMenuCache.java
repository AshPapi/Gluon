package net.ashpapi.papioptimization.access;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

public interface PapiCraftingMenuCache {
    @Nullable RecipeHolder<CraftingRecipe> papi$getLastRecipe();
    void papi$setLastRecipe(@Nullable RecipeHolder<CraftingRecipe> recipe);
    int papi$getLastRecipeEpoch();
    void papi$setLastRecipeEpoch(int epoch);
}
