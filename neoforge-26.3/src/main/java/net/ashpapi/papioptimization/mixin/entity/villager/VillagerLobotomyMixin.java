package net.ashpapi.papioptimization.mixin.entity.villager;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Villager.class, priority = 1500)
public abstract class VillagerLobotomyMixin extends AbstractVillager {

    @Shadow
    public abstract int getVillagerXp();

    @Shadow
    public abstract boolean shouldRestock(ServerLevel level);

    @Unique
    private static final int PAPI$RECHECK_INTERVAL = 100;

    @Unique
    private boolean papi$trapped;

    @Unique
    private long papi$nextTrappedCheckTick = Long.MIN_VALUE;

    @Unique
    private double papi$lastCheckX = Double.NaN;

    @Unique
    private double papi$lastCheckY = Double.NaN;

    @Unique
    private double papi$lastCheckZ = Double.NaN;

    protected VillagerLobotomyMixin(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    @Unique
    private static boolean papi$isVillagerPassable(BlockState state) {
        if (state.isPathfindable(PathComputationType.LAND)) {
            return true;
        }
        return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
    }

    @Unique
    private boolean papi$checkIsBoxedIn(ServerLevel level) {
        int bx = this.getBlockX();
        int by = this.getBlockY();
        int bz = this.getBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean headClearAbove = papi$isVillagerPassable(level.getBlockState(pos.set(bx, by + 2, bz)));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            int nx = bx + dirs[i][0];
            int nz = bz + dirs[i][1];
            BlockState head = level.getBlockState(pos.set(nx, by + 1, nz));
            if (!papi$isVillagerPassable(head)) {
                continue;
            }
            BlockState feet = level.getBlockState(pos.set(nx, by, nz));
            if (papi$isVillagerPassable(feet)) {
                return false;
            }
            if (headClearAbove && papi$isVillagerPassable(level.getBlockState(pos.set(nx, by + 2, nz)))) {
                return false;
            }
        }
        return true;
    }

    @WrapOperation(
        method = "customServerAiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
        )
    )
    private void papi$lobotomizeTrappedVillagerBrain(
            Brain<Villager> brain,
            ServerLevel level,
            LivingEntity entity,
            Operation<Void> original
    ) {
        if (this.isBaby()
                || this.isTrading()
                || this.isSleeping()
                || this.isPassenger()
                || this.getVillagerXp() <= 0
                || this.hurtTime > 0
                || this.getLastHurtByMob() != null
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || this.isInWater()
                || this.isInLava()
                || !this.onGround()
                || this.shouldRestock(level)) {
            this.papi$trapped = false;
            original.call(brain, level, entity);
            return;
        }

        long gameTime = level.getGameTime();
        double dx = this.getX() - this.papi$lastCheckX;
        double dy = this.getY() - this.papi$lastCheckY;
        double dz = this.getZ() - this.papi$lastCheckZ;
        if (gameTime >= this.papi$nextTrappedCheckTick
                || Double.isNaN(this.papi$lastCheckX)
                || dx * dx + dy * dy + dz * dz > 0.25) {
            this.papi$trapped = this.papi$checkIsBoxedIn(level);
            this.papi$lastCheckX = this.getX();
            this.papi$lastCheckY = this.getY();
            this.papi$lastCheckZ = this.getZ();
            this.papi$nextTrappedCheckTick = gameTime + PAPI$RECHECK_INTERVAL + (this.getId() & 31);
        }

        if (this.papi$trapped) {
            int interval = PapiMsptController.getVillagerLobotomyInterval();
            if (interval > 1 && Math.floorMod(this.tickCount + this.getId(), interval) != 0) {
                return;
            }
        }

        original.call(brain, level, entity);
    }
}
