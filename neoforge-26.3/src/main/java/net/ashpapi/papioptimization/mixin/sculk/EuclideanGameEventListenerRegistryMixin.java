package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EuclideanGameEventListenerRegistry.class, priority = 1500)
public abstract class EuclideanGameEventListenerRegistryMixin {
    @Shadow @Final
    private List<GameEventListener> listeners;

    /**
     * Fast-path exit if listeners list is empty, avoiding iterator allocation and processing state transitions.
     */
    @Inject(method = "visitInRangeListeners", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyCheck(Holder<GameEvent> event, Vec3 sourcePosition, GameEvent.Context context,
                                GameEventListenerRegistry.ListenerVisitor action, CallbackInfoReturnable<Boolean> cir) {
        if (this.listeners.isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * @author AshPapi
     * @reason Avoid allocating BlockPos instances to compute Euclidean distance between listener and source.
     */
    @WrapMethod(method = "getPostableListenerPosition")
    private static Optional<Vec3> papi$fastGetPostableListenerPosition(
            ServerLevel level,
            Vec3 sourcePosition,
            GameEventListener listener,
            Operation<Optional<Vec3>> original
    ) {
        Optional<Vec3> position = listener.getListenerSource().getPosition((Level) level);
        if (position.isEmpty()) {
            return Optional.empty();
        }

        Vec3 pos = position.get();
        int lx = Mth.floor(pos.x);
        int ly = Mth.floor(pos.y);
        int lz = Mth.floor(pos.z);

        int sx = Mth.floor(sourcePosition.x);
        int sy = Mth.floor(sourcePosition.y);
        int sz = Mth.floor(sourcePosition.z);

        long dx = (long) lx - (long) sx;
        long dy = (long) ly - (long) sy;
        long dz = (long) lz - (long) sz;
        long distSqr = dx * dx + dy * dy + dz * dz;

        long radius = listener.getListenerRadius();
        long radiusSqr = radius * radius;

        return distSqr > radiusSqr ? Optional.empty() : position;
    }
}
