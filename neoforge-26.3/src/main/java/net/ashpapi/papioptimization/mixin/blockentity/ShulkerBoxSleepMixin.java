package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ShulkerBoxBlockEntity.class, priority = 1500)
public abstract class ShulkerBoxSleepMixin {

    @Shadow
    private float progress;

    @Shadow
    private float progressOld;

    @WrapMethod(method = "tick")
    private static void papi$skipIdleShulkerTick(
            Level level,
            BlockPos pos,
            BlockState state,
            ShulkerBoxBlockEntity shulker,
            Operation<Void> original
    ) {
        ShulkerBoxSleepMixin acc = (ShulkerBoxSleepMixin) (Object) shulker;
        if (shulker.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.CLOSED
                && acc.progress == 0.0F
                && acc.progressOld == 0.0F) {
            return;
        }
        original.call(level, pos, state, shulker);
    }
}
