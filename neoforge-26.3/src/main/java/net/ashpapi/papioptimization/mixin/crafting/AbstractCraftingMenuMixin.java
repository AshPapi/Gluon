package net.ashpapi.papioptimization.mixin.crafting;

import net.ashpapi.papioptimization.access.PapiCraftingMenuCache;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AbstractCraftingMenu.class, priority = 1500)
public abstract class AbstractCraftingMenuMixin implements PapiCraftingMenuCache {

    @Unique
    private @Nullable RecipeHolder<CraftingRecipe> papi$lastRecipe;

    @Unique
    private int papi$lastRecipeEpoch;

    @Override
    public @Nullable RecipeHolder<CraftingRecipe> papi$getLastRecipe() {
        return this.papi$lastRecipe;
    }

    @Override
    public void papi$setLastRecipe(@Nullable RecipeHolder<CraftingRecipe> recipe) {
        this.papi$lastRecipe = recipe;
    }

    @Override
    public int papi$getLastRecipeEpoch() {
        return this.papi$lastRecipeEpoch;
    }

    @Override
    public void papi$setLastRecipeEpoch(int epoch) {
        this.papi$lastRecipeEpoch = epoch;
    }
}
