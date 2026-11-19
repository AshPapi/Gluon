package net.ashpapi.papioptimization.access;

import net.ashpapi.papioptimization.crafting.PapiRecipeIndex;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;

public interface FastRecipeMap {
    <I extends RecipeInput, T extends Recipe<I>> RecipeHolder<T>[] papi$getRecipesArray(RecipeType<T> type);

    <I extends RecipeInput, T extends Recipe<I>> PapiRecipeIndex<I, T> papi$getOrCreateIndex(RecipeType<T> type);
}
