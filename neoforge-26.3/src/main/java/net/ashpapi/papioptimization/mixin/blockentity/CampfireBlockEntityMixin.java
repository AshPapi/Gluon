package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Campfire BlockEntity Optimizer:
 * Fast Sleep: Skips cookTick and cooldownTick when campfire holds no items or progress,
 * completely eliminating tick overhead for idle / extinguished campfires while preserving 100% data safety.
 */
@Mixin(value = CampfireBlockEntity.class, priority = 1500)
public abstract class CampfireBlockEntityMixin {

    @Shadow private NonNullList<ItemStack> items;
    @Shadow private int[] cookingProgress;

    /**
     * Fast-fails cookTick on server if campfire is completely empty.
     */
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCookTickSleep(
            ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity entity,
            RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache, CallbackInfo ci) {
        CampfireBlockEntityMixin access = (CampfireBlockEntityMixin) (Object) entity;
        NonNullList<ItemStack> slots = access.items;

        if (slots.get(0).isEmpty() && slots.get(1).isEmpty() && slots.get(2).isEmpty() && slots.get(3).isEmpty()) {
            ci.cancel();
        }
    }

    /**
     * Fast-fails cooldownTick if extinguished campfire is already cool (progress == 0).
     */
    @Inject(method = "cooldownTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCooldownTickSleep(
            Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        CampfireBlockEntityMixin access = (CampfireBlockEntityMixin) (Object) entity;
        int[] progress = access.cookingProgress;

        if (progress[0] <= 0 && progress[1] <= 0 && progress[2] <= 0 && progress[3] <= 0) {
            ci.cancel();
        }
    }
}
