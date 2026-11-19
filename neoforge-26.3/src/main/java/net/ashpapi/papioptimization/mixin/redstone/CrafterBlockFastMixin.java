package net.ashpapi.papioptimization.mixin.redstone;

import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Fast Crafter Cache:
 * Vanilla CrafterBlock shares a single static RecipeCache with a capacity of only 10 entries.
 * When multiple crafters run with different recipes (iron, gold, copper, redstone, dispensers),
 * the cache constantly thrashes, forcing repeated full recipe database scans.
 *
 * This mixin expands the vanilla RecipeCache capacity to 64 entries.
 * Because it uses vanilla's RecipeCache directly, it guarantees 100% parity:
 * - Proper stack count normalization (count == 1 matching).
 * - Automatic cache invalidation on datapack reloads (/reload).
 * - Zero memory leaks or duplicate maps.
 */
@Mixin(value = CrafterBlock.class, priority = 1500)
public abstract class CrafterBlockFastMixin {

    @Shadow @Final @Mutable
    private static RecipeCache RECIPE_CACHE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void papi$expandRecipeCache(CallbackInfo ci) {
        RECIPE_CACHE = new RecipeCache(64);
    }
}
