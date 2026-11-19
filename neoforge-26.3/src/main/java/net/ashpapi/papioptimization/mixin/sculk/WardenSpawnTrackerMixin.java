package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.ashpapi.papioptimization.sculk.IWardenSpawnTrackerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = WardenSpawnTracker.class, priority = 1500)
public abstract class WardenSpawnTrackerMixin implements IWardenSpawnTrackerAccess {
    @Shadow
    protected abstract boolean onCooldown();

    @Shadow
    protected abstract void increaseWarningLevel();

    @Shadow
    protected abstract void copyData(WardenSpawnTracker copyFrom);

    @Override
    public boolean papi$onCooldown() {
        return this.onCooldown();
    }

    @Override
    public void papi$increaseWarningLevel() {
        this.increaseWarningLevel();
    }

    @Override
    public void papi$copyData(WardenSpawnTracker copyFrom) {
        this.copyData(copyFrom);
    }

    /**
     * @author AshPapi
     * @reason Stream-free, single-pass player warning evaluation with exact vanilla semantics.
     */
    @WrapMethod(method = "tryWarn")
    private static OptionalInt papi$fastTryWarn(
            ServerLevel level,
            BlockPos pos,
            ServerPlayer triggerPlayer,
            Operation<OptionalInt> original
    ) {
        // 1. Check nearby Warden (48-block exclusion cube)
        AABB wardenArea = AABB.ofSize(Vec3.atCenterOf(pos), 48.0, 48.0, 48.0);
        if (!level.getEntitiesOfClass(Warden.class, wardenArea).isEmpty()) {
            return OptionalInt.empty();
        }

        // 2. Collect nearby players within 16 blocks in a single allocation-free loop
        double originX = pos.getX() + 0.5;
        double originY = pos.getY() + 0.5;
        double originZ = pos.getZ() + 0.5;
        double maxDistSqr = 16.0 * 16.0;

        List<ServerPlayer> players = new ArrayList<>();
        boolean triggerIncluded = false;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }

            double dx = player.getX() - originX;
            double dy = player.getY() - originY;
            double dz = player.getZ() - originZ;
            if (dx * dx + dy * dy + dz * dz <= maxDistSqr) {
                IWardenSpawnTrackerAccess access = (IWardenSpawnTrackerAccess) player.getWardenSpawnTracker();
                if (access.papi$onCooldown()) {
                    return OptionalInt.empty();
                }
                players.add(player);
                if (player == triggerPlayer) {
                    triggerIncluded = true;
                }
            }
        }

        if (!triggerIncluded) {
            IWardenSpawnTrackerAccess triggerAccess = (IWardenSpawnTrackerAccess) triggerPlayer.getWardenSpawnTracker();
            if (triggerAccess.papi$onCooldown()) {
                return OptionalInt.empty();
            }
            players.add(triggerPlayer);
        }

        if (players.isEmpty()) {
            return OptionalInt.empty();
        }

        // 3. Find highest warning tracker
        WardenSpawnTracker highestTracker = null;
        int maxWarning = -1;
        for (int i = 0; i < players.size(); i++) {
            WardenSpawnTracker tracker = players.get(i).getWardenSpawnTracker();
            if (tracker.getWarningLevel() > maxWarning) {
                maxWarning = tracker.getWarningLevel();
                highestTracker = tracker;
            }
        }

        if (highestTracker != null) {
            ((IWardenSpawnTrackerAccess) highestTracker).papi$increaseWarningLevel();
            for (int i = 0; i < players.size(); i++) {
                ((IWardenSpawnTrackerAccess) players.get(i).getWardenSpawnTracker()).papi$copyData(highestTracker);
            }
            return OptionalInt.of(highestTracker.getWarningLevel());
        }

        return OptionalInt.empty();
    }
}
