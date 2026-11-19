package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ContainerOrHandler;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.List;

@Mixin(value = HopperBlockEntity.class, priority = 1500)
public abstract class FastHopperMixin extends RandomizableContainerBlockEntity implements Hopper {

    @Shadow
    private Direction facing;

    protected FastHopperMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private @Nullable AABB papi$cachedSuckAabb;

    @Unique
    private double papi$cachedX = Double.NaN;

    @Unique
    private double papi$cachedY = Double.NaN;

    @Unique
    private double papi$cachedZ = Double.NaN;

    @Unique
    private @Nullable WeakReference<Container> papi$lastPushDest;

    @Unique
    private long papi$lastPushPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPushSelfEpoch;

    @Unique
    private long papi$lastPushOtherEpoch;

    @Unique
    private long papi$lastPushBlockEpoch;

    @Unique
    private long papi$lastPushOtherBlockPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPushOtherBlockEpoch;

    @Unique
    private @Nullable WeakReference<Container> papi$lastPullSource;

    @Unique
    private long papi$lastPullPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPullSelfEpoch;

    @Unique
    private long papi$lastPullOtherEpoch;

    @Unique
    private long papi$lastPullBlockEpoch;

    @Unique
    private long papi$lastPullOtherBlockPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPullOtherBlockEpoch;

    @Unique
    private static final ThreadLocal<Container @Nullable []> PAPI$OBSERVED_CONTAINER =
            ThreadLocal.withInitial(() -> new Container[1]);

    @WrapMethod(method = "getItemsAtAndAbove")
    private static List<ItemEntity> papi$fastGetItemsAtAndAbove(
            Level level,
            Hopper hopper,
            Operation<List<ItemEntity>> original
    ) {
        if (hopper instanceof HopperBlockEntity hbe) {
            FastHopperMixin accessor = (FastHopperMixin) (Object) hbe;
            double hx = hopper.getLevelX();
            double hy = hopper.getLevelY();
            double hz = hopper.getLevelZ();
            AABB box = accessor.papi$cachedSuckAabb;
            if (box == null || hx != accessor.papi$cachedX || hy != accessor.papi$cachedY || hz != accessor.papi$cachedZ) {
                box = hopper.getSuckAabb().move(hx - 0.5, hy - 0.5, hz - 0.5);
                accessor.papi$cachedSuckAabb = box;
                accessor.papi$cachedX = hx;
                accessor.papi$cachedY = hy;
                accessor.papi$cachedZ = hz;
            }
            return level.getEntitiesOfClass(ItemEntity.class, box, EntitySelector.ENTITY_STILL_ALIVE);
        }
        return original.call(level, hopper);
    }

    @WrapMethod(method = "ejectItems")
    private static boolean papi$epochEjectItems(
            Level level,
            BlockPos pos,
            HopperBlockEntity hopper,
            Operation<Boolean> original
    ) {
        FastHopperMixin self = (FastHopperMixin) (Object) hopper;
        Direction dir = self.facing;
        long targetLong = BlockPos.asLong(
                pos.getX() + dir.getStepX(),
                pos.getY() + dir.getStepY(),
                pos.getZ() + dir.getStepZ()
        );
        PapiLevelBlockEpoch blockEpochBus = (PapiLevelBlockEpoch) level;
        long curBlockEpoch = blockEpochBus.papi$getBlockEpoch(targetLong);

        Container cachedDest = self.papi$lastPushDest != null ? self.papi$lastPushDest.get() : null;
        if (cachedDest != null
                && self.papi$lastPushPosLong == targetLong
                && curBlockEpoch == self.papi$lastPushBlockEpoch
                && (self.papi$lastPushOtherBlockPosLong == Long.MIN_VALUE
                    || blockEpochBus.papi$getBlockEpoch(self.papi$lastPushOtherBlockPosLong) == self.papi$lastPushOtherBlockEpoch)
                && papi$isValidBlockContainer(cachedDest, level, targetLong)) {
            long selfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
            long otherEpoch = ((PapiEpochContainer) cachedDest).papi$getChangeEpoch();
            if (selfEpoch == self.papi$lastPushSelfEpoch && otherEpoch == self.papi$lastPushOtherEpoch) {
                return false;
            }
        }

        Container[] box = PAPI$OBSERVED_CONTAINER.get();
        Container prev = box[0];
        box[0] = null;
        boolean moved;
        try {
            moved = original.call(level, pos, hopper);
            Container observed = box[0];
            if (!moved && observed != null && papi$isValidBlockContainer(observed, level, targetLong)) {
                self.papi$lastPushDest = (observed == cachedDest) ? self.papi$lastPushDest : new WeakReference<>(observed);
                self.papi$lastPushPosLong = targetLong;
                self.papi$lastPushSelfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
                self.papi$lastPushOtherEpoch = ((PapiEpochContainer) observed).papi$getChangeEpoch();
                self.papi$lastPushBlockEpoch = blockEpochBus.papi$getBlockEpoch(targetLong);
                long secondaryPos = papi$getSecondaryPosLong(observed, targetLong);
                self.papi$lastPushOtherBlockPosLong = secondaryPos;
                self.papi$lastPushOtherBlockEpoch = secondaryPos != Long.MIN_VALUE
                        ? blockEpochBus.papi$getBlockEpoch(secondaryPos)
                        : 0L;
            } else {
                self.papi$lastPushDest = null;
            }
        } finally {
            box[0] = prev;
        }
        return moved;
    }

    @WrapOperation(
            method = "ejectItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getContainerOrHandlerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;"
            )
    )
    private static ContainerOrHandler papi$capturePushContainer(
            Level level,
            BlockPos pos,
            Direction side,
            Operation<ContainerOrHandler> original
    ) {
        ContainerOrHandler result = original.call(level, pos, side);
        PAPI$OBSERVED_CONTAINER.get()[0] = result != null ? result.container() : null;
        return result;
    }

    @WrapMethod(method = "suckInItems")
    private static boolean papi$epochSuckInItems(
            Level level,
            Hopper hopper,
            Operation<Boolean> original
    ) {
        if (!(hopper instanceof HopperBlockEntity hbe)) {
            return original.call(level, hopper);
        }
        FastHopperMixin self = (FastHopperMixin) (Object) hbe;
        long aboveLong = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ()).asLong();
        PapiLevelBlockEpoch blockEpochBus = (PapiLevelBlockEpoch) level;
        long curBlockEpoch = blockEpochBus.papi$getBlockEpoch(aboveLong);

        Container cachedSrc = self.papi$lastPullSource != null ? self.papi$lastPullSource.get() : null;
        if (cachedSrc != null
                && self.papi$lastPullPosLong == aboveLong
                && curBlockEpoch == self.papi$lastPullBlockEpoch
                && (self.papi$lastPullOtherBlockPosLong == Long.MIN_VALUE
                    || blockEpochBus.papi$getBlockEpoch(self.papi$lastPullOtherBlockPosLong) == self.papi$lastPullOtherBlockEpoch)
                && papi$isValidBlockContainer(cachedSrc, level, aboveLong)) {
            long selfEpoch = ((PapiEpochContainer) hbe).papi$getChangeEpoch();
            long otherEpoch = ((PapiEpochContainer) cachedSrc).papi$getChangeEpoch();
            if (selfEpoch == self.papi$lastPullSelfEpoch && otherEpoch == self.papi$lastPullOtherEpoch) {
                return false;
            }
        }

        Container[] box = PAPI$OBSERVED_CONTAINER.get();
        Container prev = box[0];
        box[0] = null;
        boolean pulled;
        try {
            pulled = original.call(level, hopper);
            Container observed = box[0];
            if (!pulled && observed != null && papi$isValidBlockContainer(observed, level, aboveLong)) {
                self.papi$lastPullSource = (observed == cachedSrc) ? self.papi$lastPullSource : new WeakReference<>(observed);
                self.papi$lastPullPosLong = aboveLong;
                self.papi$lastPullSelfEpoch = ((PapiEpochContainer) hbe).papi$getChangeEpoch();
                self.papi$lastPullOtherEpoch = ((PapiEpochContainer) observed).papi$getChangeEpoch();
                self.papi$lastPullBlockEpoch = blockEpochBus.papi$getBlockEpoch(aboveLong);
                long secondaryPos = papi$getSecondaryPosLong(observed, aboveLong);
                self.papi$lastPullOtherBlockPosLong = secondaryPos;
                self.papi$lastPullOtherBlockEpoch = secondaryPos != Long.MIN_VALUE
                        ? blockEpochBus.papi$getBlockEpoch(secondaryPos)
                        : 0L;
            } else {
                self.papi$lastPullSource = null;
            }
        } finally {
            box[0] = prev;
        }
        return pulled;
    }

    @WrapOperation(
            method = "suckInItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getSourceContainerOrHandler(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;"
            )
    )
    private static ContainerOrHandler papi$capturePullContainer(
            Level level,
            Hopper hopper,
            BlockPos pos,
            BlockState state,
            Operation<ContainerOrHandler> original
    ) {
        ContainerOrHandler result = original.call(level, hopper, pos, state);
        PAPI$OBSERVED_CONTAINER.get()[0] = result != null ? result.container() : null;
        return result;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void papi$onHopperSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void papi$onHopperRemoveItem(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Unique
    private static long papi$getSecondaryPosLong(Container container, long primaryPosLong) {
        if (container instanceof CompoundContainer cc) {
            CompoundContainerAccessor acc = (CompoundContainerAccessor) cc;
            if (acc.papi$getContainer1() instanceof BaseContainerBlockEntity be1
                    && acc.papi$getContainer2() instanceof BaseContainerBlockEntity be2) {
                long p1 = be1.getBlockPos().asLong();
                long p2 = be2.getBlockPos().asLong();
                return p1 == primaryPosLong ? p2 : p1;
            }
        }
        return Long.MIN_VALUE;
    }

    @Unique
    private static boolean papi$isValidBlockContainer(Container container, Level level, long posLong) {
        if (!(container instanceof PapiEpochContainer)) {
            return false;
        }
        if (container instanceof BaseContainerBlockEntity be) {
            if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) {
                return false;
            }
            if (!"net.minecraft.world.level.block.entity".equals(be.getClass().getPackageName())) {
                return false;
            }
            if (be.isRemoved() || be.getLevel() != level || be.getBlockPos().asLong() != posLong) {
                return false;
            }
            if (be instanceof ChestBlockEntity) {
                BlockState state = be.getBlockState();
                return !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
            }
            return true;
        }
        if (container instanceof CompoundContainer cc) {
            CompoundContainerAccessor acc = (CompoundContainerAccessor) cc;
            Container c1 = acc.papi$getContainer1();
            Container c2 = acc.papi$getContainer2();
            if (!(c1 instanceof BaseContainerBlockEntity be1) || !(c2 instanceof BaseContainerBlockEntity be2)) {
                return false;
            }
            if (!"net.minecraft.world.level.block.entity".equals(be1.getClass().getPackageName())
                    || !"net.minecraft.world.level.block.entity".equals(be2.getClass().getPackageName())) {
                return false;
            }
            if (be1.isRemoved() || be2.isRemoved() || be1.getLevel() != level || be2.getLevel() != level) {
                return false;
            }
            if (be1.getBlockPos().asLong() != posLong && be2.getBlockPos().asLong() != posLong) {
                return false;
            }
            BlockState s1 = be1.getBlockState();
            BlockState s2 = be2.getBlockState();
            boolean valid1 = !s1.hasProperty(ChestBlock.TYPE) || s1.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
            boolean valid2 = !s2.hasProperty(ChestBlock.TYPE) || s2.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
            return valid1 && valid2;
        }
        return false;
    }
}
