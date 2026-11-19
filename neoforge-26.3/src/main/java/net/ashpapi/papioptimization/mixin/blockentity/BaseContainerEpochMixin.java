package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseContainerBlockEntity.class, priority = 1500)
public abstract class BaseContainerEpochMixin implements PapiEpochContainer {

    @Unique
    private long papi$changeEpoch = 1L;

    @Override
    public long papi$getChangeEpoch() {
        return this.papi$changeEpoch;
    }

    @Override
    public void papi$incrementEpoch() {
        this.papi$changeEpoch++;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"))
    private void papi$onSetItem(int slot, ItemStack stack, boolean isSetChanged, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "removeItem", at = @At("RETURN"))
    private void papi$onRemoveItem(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void papi$onRemoveItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "clearContent", at = @At("RETURN"))
    private void papi$onClearContent(CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void papi$onLoadAdditional(ValueInput input, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "applyImplicitComponents", at = @At("RETURN"))
    private void papi$onApplyImplicitComponents(DataComponentGetter componentGetter, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }
}
