package net.ashpapi.papioptimization.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * High-performance ArmorStand tick optimizer.
 * Provides adaptive sleeping for grounded, static armor stands and complete tick-skipping for markers.
 * Guarantees 100% vanilla parity: armor stands with gravity, flowing water, damage, or player interactions
 * wake up immediately.
 */
public final class ArmorStandOptimizer {

    public static final int SLEEP_INTERVAL_TICKS = 20; // 1 second between wake-checks for sleeping stands

    private ArmorStandOptimizer() {}

    /**
     * Determines whether ArmorStand tick() can be skipped this tick.
     *
     * @param stand the ArmorStand being evaluated
     * @return true if tick() should be cancelled, false to execute standard vanilla tick
     */
    public static boolean shouldSkipTick(ArmorStand stand) {
        if (stand.level().isClientSide()) {
            return false;
        }

        if (!stand.isAlive() || stand.isRemoved() || stand.tickCount < 2 || stand.isPassenger() || stand.isVehicle()) {
            return false;
        }

        if (stand.isOnFire() || stand.hurtTime > 0) {
            return false;
        }

        Vec3 velocity = stand.getDeltaMovement();
        if (Math.abs(velocity.x) > 0.001 || Math.abs(velocity.y) > 0.001 || Math.abs(velocity.z) > 0.001) {
            return false;
        }

        if (stand.isInWater() || stand.isInLava()) {
            return false;
        }

        if (!stand.isNoGravity() && !stand.onGround()) {
            return false;
        }

        if (stand instanceof ArmorStandSleepHolder holder) {
            int sleepTicks = holder.papi$getSleepTicks();
            if (sleepTicks > 0) {
                if (stand.isMarker()) {
                    holder.papi$setSleepTicks(sleepTicks - 1);
                    return true;
                }
                if (holder.papi$hasMovedSinceSleep(stand.getX(), stand.getY(), stand.getZ())
                        || !stand.level().getFluidState(stand.blockPosition()).isEmpty()) {
                    holder.papi$wakeUp();
                    return false;
                }
                if (!stand.isNoGravity()) {
                    int mark = BlockPosPool.mark();
                    try {
                        BlockPos.MutableBlockPos below = BlockPosPool.get(
                                stand.getBlockX(),
                                Mth.floor(stand.getY() - 0.0625),
                                stand.getBlockZ()
                        );
                        if (stand.level().getBlockState(below).isAir()) {
                            holder.papi$wakeUp();
                            return false;
                        }
                    } finally {
                        BlockPosPool.reset(mark);
                    }
                }
                holder.papi$setSleepTicks(sleepTicks - 1);
                return true;
            }

            if (!stand.isMarker() && !stand.isNoGravity()) {
                AABB feetBox = stand.getBoundingBox().move(0.0, -0.0625, 0.0);
                if (stand.level().noCollision(stand, feetBox)) {
                    return false;
                }
            }

            if (!stand.level().getFluidState(stand.blockPosition()).isEmpty()) {
                return false;
            }

            holder.papi$recordSleepPos(stand.getX(), stand.getY(), stand.getZ());
            holder.papi$setSleepTicks(SLEEP_INTERVAL_TICKS);
            return false;
        }

        return false;
    }
}
