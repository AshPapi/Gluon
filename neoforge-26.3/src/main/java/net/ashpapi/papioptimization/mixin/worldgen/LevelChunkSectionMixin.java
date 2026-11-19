package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiNoisiumPalette;
import net.ashpapi.papioptimization.access.PapiNoisiumSection;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunkSection.class, priority = 1500)
public abstract class LevelChunkSectionMixin implements PapiNoisiumSection {

    @Shadow private short nonEmptyBlockCount;
    @Shadow private short fluidCount;
    @Shadow private short tickingBlockCount;
    @Shadow private short tickingFluidCount;
    @Shadow @Final private PalettedContainer<BlockState> states;

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetEmptyFluidState(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        if (this.fluidCount == 0) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Override
    public void papi$setBlockStateNoisium(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            NoisiumPaletteCursor cursor
    ) {
        if (!(this.states instanceof PapiNoisiumPalette noisiumPalette)) {
            ((LevelChunkSection) (Object) this).setBlockState(sectionX, sectionY, sectionZ, state, false);
            return;
        }
        int index = (sectionY << 8) | (sectionZ << 4) | sectionX;
        BlockState previous = noisiumPalette.papi$setDirectNoisium(index, state, cursor);
        if (previous == state) {
            return;
        }
        if (previous != Blocks.AIR.defaultBlockState() && !previous.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount - 1);
            if (previous.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount - 1);
            }
            FluidState previousFluid = previous.getFluidState();
            if (!previousFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount - 1);
                if (previousFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount - 1);
                }
            }
        }
        if (!state.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount + 1);
            if (state.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount + 1);
            }
            FluidState currentFluid = state.getFluidState();
            if (!currentFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount + 1);
                if (currentFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount + 1);
                }
            }
        }
    }

    @WrapMethod(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState papi$fastSetBlockState(
            int sectionX,
            int sectionY,
            int sectionZ,
            BlockState state,
            boolean useLocks,
            Operation<BlockState> original
    ) {
        BlockState previous = useLocks
                ? this.states.getAndSet(sectionX, sectionY, sectionZ, state)
                : this.states.getAndSetUnchecked(sectionX, sectionY, sectionZ, state);
        if (previous == state) {
            return previous;
        }
        if (!previous.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount - 1);
            if (previous.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount - 1);
            }
            FluidState previousFluid = previous.getFluidState();
            if (!previousFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount - 1);
                if (previousFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount - 1);
                }
            }
        }
        if (!state.isEmpty()) {
            this.nonEmptyBlockCount = (short) (this.nonEmptyBlockCount + 1);
            if (state.isRandomlyTicking()) {
                this.tickingBlockCount = (short) (this.tickingBlockCount + 1);
            }
            FluidState currentFluid = state.getFluidState();
            if (!currentFluid.isEmpty()) {
                this.fluidCount = (short) (this.fluidCount + 1);
                if (currentFluid.isRandomlyTicking()) {
                    this.tickingFluidCount = (short) (this.tickingFluidCount + 1);
                }
            }
        }
        return previous;
    }
}
