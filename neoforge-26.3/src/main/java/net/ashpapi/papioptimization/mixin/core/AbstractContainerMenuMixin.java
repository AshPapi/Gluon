package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(value = AbstractContainerMenu.class, priority = 1500)
public abstract class AbstractContainerMenuMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    @Final
    private List<DataSlot> dataSlots;

    @Shadow
    private void triggerSlotListeners(int slotIndex, ItemStack stack, Supplier<ItemStack> copySupplier) {
    }

    @Shadow
    private void synchronizeSlotToRemote(int slotIndex, ItemStack stack, Supplier<ItemStack> copySupplier) {
    }

    @Shadow
    private void synchronizeCarriedToRemote() {
    }

    @Shadow
    private void updateDataSlotListeners(int slotIndex, int value) {
    }

    @Shadow
    private void synchronizeDataSlotToRemote(int slotIndex, int value) {
    }

    @Unique
    private ItemStack papi$currentStack;

    @Unique
    private ItemStack papi$cachedCopy;

    @Unique
    private final Supplier<ItemStack> papi$reusableCopySupplier = () -> {
        ItemStack copy = this.papi$cachedCopy;
        if (copy == null) {
            ItemStack cur = this.papi$currentStack;
            copy = cur != null ? cur.copy() : ItemStack.EMPTY;
            this.papi$cachedCopy = copy;
        }
        return copy;
    };

    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void papi$zeroAllocBroadcastChanges(CallbackInfo ci) {
        ItemStack prevStack = this.papi$currentStack;
        ItemStack prevCopy = this.papi$cachedCopy;
        NonNullList<Slot> slotsList = this.slots;
        Supplier<ItemStack> copySupplier = this.papi$reusableCopySupplier;
        try {
            for (int i = 0; i < slotsList.size(); i++) {
                ItemStack stack = slotsList.get(i).getItem();
                this.papi$currentStack = stack;
                this.papi$cachedCopy = null;
                this.triggerSlotListeners(i, stack, copySupplier);
                if (this.papi$currentStack != stack) {
                    this.papi$currentStack = stack;
                    this.papi$cachedCopy = null;
                }
                this.synchronizeSlotToRemote(i, stack, copySupplier);
            }
        } finally {
            this.papi$currentStack = prevStack;
            this.papi$cachedCopy = prevCopy;
        }
        this.synchronizeCarriedToRemote();
        List<DataSlot> dataList = this.dataSlots;
        for (int i = 0; i < dataList.size(); i++) {
            DataSlot dataSlot = dataList.get(i);
            int value = dataSlot.get();
            if (dataSlot.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(i, value);
            }
            this.synchronizeDataSlotToRemote(i, value);
        }
        ci.cancel();
    }
}
