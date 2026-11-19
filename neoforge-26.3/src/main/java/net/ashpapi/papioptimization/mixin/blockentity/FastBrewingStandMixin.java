package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Fast Brewing Stand Check:
 * Eliminates redundant recipe checking and 3x BrewingInput allocations per tick
 * when a brewing stand is idle and missing fuel, ingredients, or potion bottles.
 * 100% vanilla parity.
 */
@Mixin(value = BrewingStandBlockEntity.class, priority = 1500)
public abstract class FastBrewingStandMixin {

    @Shadow private NonNullList<ItemStack> items;
    @Shadow private int brewTime;
    @Shadow private int fuel;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void papi$fastBrewingCheck(
            ServerLevel level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        FastBrewingStandMixin access = (FastBrewingStandMixin) (Object) stand;

        // Active brewing process: let vanilla tick
        if (access.brewTime > 0) {
            return;
        }

        if (access.fuel <= 0 && !access.items.get(4).isEmpty()) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (state.getValue(BrewingStandBlock.HAS_BOTTLE[i]) != !access.items.get(i).isEmpty()) {
                return;
            }
        }

        // Slot 3: Ingredient, Slot 4: Fuel, Slots 0..2: Potions
        ItemStack ingredient = access.items.get(3);
        if (ingredient.isEmpty()) {
            ci.cancel();
            return;
        }

        ItemStack fuelItem = access.items.get(4);
        if (access.fuel <= 0 && fuelItem.isEmpty()) {
            ci.cancel();
            return;
        }

        // If all 3 potion slots are empty, brewing cannot occur
        if (access.items.get(0).isEmpty() && access.items.get(1).isEmpty() && access.items.get(2).isEmpty()) {
            ci.cancel();
        }
    }
}
