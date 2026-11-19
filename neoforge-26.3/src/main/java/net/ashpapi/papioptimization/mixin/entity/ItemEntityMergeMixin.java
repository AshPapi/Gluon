package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemEntity.class, priority = 1500)
public abstract class ItemEntityMergeMixin extends Entity {
    public ItemEntityMergeMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    protected abstract boolean isMergable();

    @Inject(method = "areMergable", at = @At("HEAD"), cancellable = true)
    private static void papi$fastAreMergable(ItemStack stack1, ItemStack stack2, CallbackInfoReturnable<Boolean> cir) {
        if (stack1.getItem() != stack2.getItem()) {
            cir.setReturnValue(false);
            return;
        }
        if (stack2.getCount() + stack1.getCount() > stack2.getMaxStackSize()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(ItemStack.isSameItemSameComponents(stack1, stack2));
    }

    @Inject(method = "mergeWithNeighbours", at = @At("HEAD"), cancellable = true)
    private void papi$throttleItemMerge(CallbackInfo ci) {
        if (!this.isMergable()) {
            ci.cancel();
            return;
        }

        boolean isStationary = this.onGround() && this.getDeltaMovement().lengthSqr() < 1.0E-4;
        int mask = isStationary ? 15 : 3;

        if (((this.tickCount + this.getId()) & mask) != 0) {
            ci.cancel();
        }
    }
}
