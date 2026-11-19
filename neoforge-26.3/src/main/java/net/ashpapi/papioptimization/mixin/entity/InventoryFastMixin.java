package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Inventory.class, priority = 1500)
public abstract class InventoryFastMixin implements Container {

    @Shadow @Final
    private NonNullList<ItemStack> items;

    @Inject(method = "hasRemainingSpaceForItem", at = @At("HEAD"), cancellable = true)
    private void papi$fastHasRemainingSpaceForItem(ItemStack existing, ItemStack toInsert, CallbackInfoReturnable<Boolean> cir) {
        if (existing.isEmpty()
            || existing.getItem() != toInsert.getItem()
            || existing.getCount() >= this.getMaxStackSize(existing)
            || !existing.isStackable()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(ItemStack.isSameItemSameComponents(existing, toInsert));
    }

    @Inject(method = "findSlotMatchingItem", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindSlotMatchingItem(ItemStack target, CallbackInfoReturnable<Integer> cir) {
        if (target.isEmpty()) {
            cir.setReturnValue(-1);
            return;
        }
        Item targetItem = target.getItem();
        NonNullList<ItemStack> list = this.items;
        for (int i = 0, size = list.size(); i < size; i++) {
            ItemStack slot = list.get(i);
            if (!slot.isEmpty() && slot.getItem() == targetItem && ItemStack.isSameItemSameComponents(target, slot)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }
}
