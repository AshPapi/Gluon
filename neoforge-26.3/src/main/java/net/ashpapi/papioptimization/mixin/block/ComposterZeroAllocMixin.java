package net.ashpapi.papioptimization.mixin.block;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "net.minecraft.world.level.block.ComposterBlock$InputContainer",
        "net.minecraft.world.level.block.ComposterBlock$OutputContainer",
        "net.minecraft.world.level.block.ComposterBlock$EmptyContainer"
}, priority = 1500)
public abstract class ComposterZeroAllocMixin extends SimpleContainer {

    @Unique
    private static final int[] PAPI$ZERO_SLOTS = new int[0];

    @Unique
    private static final int[] PAPI$SINGLE_SLOT = new int[]{0};

    @Unique
    private static final String PAPI$INPUT_CLASS_NAME = "net.minecraft.world.level.block.ComposterBlock$InputContainer";

    protected ComposterZeroAllocMixin(int size) {
        super(size);
    }

    @Inject(method = "getSlotsForFace", at = @At("HEAD"), cancellable = true)
    private void papi$zeroAllocSlotsForFace(Direction side, CallbackInfoReturnable<int[]> cir) {
        if (this.getContainerSize() == 0) {
            cir.setReturnValue(PAPI$ZERO_SLOTS);
            return;
        }
        if (this.getClass().getName().equals(PAPI$INPUT_CLASS_NAME)) {
            cir.setReturnValue(side == Direction.UP ? PAPI$SINGLE_SLOT : PAPI$ZERO_SLOTS);
        } else {
            cir.setReturnValue(side == Direction.DOWN ? PAPI$SINGLE_SLOT : PAPI$ZERO_SLOTS);
        }
    }
}
