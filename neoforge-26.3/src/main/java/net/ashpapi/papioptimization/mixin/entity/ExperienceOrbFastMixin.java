package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ExperienceOrb.class, priority = 1500)
public abstract class ExperienceOrbFastMixin extends Entity {

    @Shadow private int count;
    @Shadow private int age;

    @Shadow public abstract int getValue();
    @Shadow
    private int repairPlayerItems(ServerPlayer player, int amount) {
        throw new AssertionError();
    }

    @Unique
    private static final long PAPI$MAX_TOTAL_XP = 1_000_000_000L;

    @Unique
    private @Nullable Int2IntOpenHashMap papi$clumpedValues;

    public ExperienceOrbFastMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private long papi$computeTotalXp() {
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        if (map == null || map.isEmpty()) {
            return (long) Math.max(0, this.getValue()) * (long) Math.max(0, this.count);
        }
        long sum = 0L;
        for (Int2IntMap.Entry e : map.int2IntEntrySet()) {
            sum += (long) e.getIntKey() * (long) e.getIntValue();
            if (sum >= PAPI$MAX_TOTAL_XP) {
                return PAPI$MAX_TOTAL_XP;
            }
        }
        return sum;
    }

    @Unique
    private void papi$ensureClumpMap() {
        if (this.papi$clumpedValues == null) {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap(4);
            int curVal = this.getValue();
            if (curVal > 0 && this.count > 0) {
                map.put(curVal, this.count);
            }
            this.papi$clumpedValues = map;
        }
    }

    @Unique
    private void papi$addClumpedEntry(int val, int addCount) {
        if (val <= 0 || addCount <= 0) {
            return;
        }
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        int prev = map.get(val);
        long next = (long) prev + (long) addCount;
        map.put(val, (int) Math.min(Integer.MAX_VALUE, next));
    }

    @Unique
    private static void papi$awardTotalXp(Player player, long totalXp) {
        long remaining = totalXp;
        while (remaining > 0L) {
            int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
            player.giveExperiencePoints(chunk);
            remaining -= chunk;
        }
    }

    /**
     * Eliminates the artificial (getId() - id) % 40 == 0 check that prevented 97.5% of
     * eligible XP orbs from merging in vanilla.
     */
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void papi$canMergeFast(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (orb.isRemoved() || orb.getValue() != value) {
            cir.setReturnValue(false);
            return;
        }
        ExperienceOrbFastMixin m = (ExperienceOrbFastMixin) (Object) orb;
        cir.setReturnValue(m.papi$clumpedValues == null
                && m.count > 0
                && m.count < Integer.MAX_VALUE
                && m.papi$computeTotalXp() + (long) Math.max(0, value) <= PAPI$MAX_TOTAL_XP);
    }

    @WrapMethod(method = "tryMergeToExisting")
    private static boolean papi$tryMergeDifferentValues(
            ServerLevel level,
            Vec3 pos,
            int value,
            Operation<Boolean> original
    ) {
        if (value <= 0) {
            return original.call(level, pos, value);
        }
        AABB box = AABB.ofSize(pos, 1.0, 1.0, 1.0);
        List<ExperienceOrb> list = level.getEntities(PAPI$ORB_TEST, box, orb -> !orb.isRemoved());
        for (int i = 0; i < list.size(); i++) {
            ExperienceOrb orb = list.get(i);
            ExperienceOrbFastMixin m = (ExperienceOrbFastMixin) (Object) orb;
            if (m.count <= 0) {
                continue;
            }
            if (m.papi$computeTotalXp() + (long) value > PAPI$MAX_TOTAL_XP) {
                continue;
            }
            if (m.papi$clumpedValues == null && orb.getValue() == value && m.count < Integer.MAX_VALUE) {
                m.count++;
            } else {
                m.papi$ensureClumpMap();
                m.papi$addClumpedEntry(value, 1);
                m.count = Math.max(1, m.count);
            }
            m.age = 0;
            return true;
        }
        return false;
    }

    @Unique
    private static final EntityTypeTest<Entity, ExperienceOrb> PAPI$ORB_TEST =
            EntityTypeTest.forClass(ExperienceOrb.class);

    /**
     * Enhances scanForMerges with an expanded 1.25m bounding box to consolidate
     * orbs rapidly in mob drop chutes.
     */
    @Inject(method = "scanForMerges", at = @At("HEAD"), cancellable = true)
    private void papi$scanForMergesFast(CallbackInfo ci) {
        if (this.isRemoved() || this.count <= 0) {
            ci.cancel();
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        AABB searchBox = this.getBoundingBox().inflate(1.25D);
        List<ExperienceOrb> nearbyOrbs = serverLevel.getEntities(
                PAPI$ORB_TEST,
                searchBox,
                other -> other != (Object) this && !other.isRemoved()
        );

        int orbCount = nearbyOrbs.size();
        if (orbCount == 0) {
            ci.cancel();
            return;
        }

        long selfTotal = this.papi$computeTotalXp();
        int selfValue = this.getValue();

        for (int i = 0; i < orbCount; i++) {
            ExperienceOrb other = nearbyOrbs.get(i);
            ExperienceOrbFastMixin otherMixin = (ExperienceOrbFastMixin) (Object) other;
            if (otherMixin.isRemoved() || otherMixin.count <= 0) continue;

            long otherTotal = otherMixin.papi$computeTotalXp();
            if (otherTotal <= 0L || selfTotal + otherTotal > PAPI$MAX_TOTAL_XP) {
                continue;
            }

            int otherValue = other.getValue();
            if (this.papi$clumpedValues == null
                    && otherMixin.papi$clumpedValues == null
                    && otherValue == selfValue
                    && (long) this.count + (long) otherMixin.count <= Integer.MAX_VALUE) {
                this.count += otherMixin.count;
            } else {
                this.papi$ensureClumpMap();
                if (otherMixin.papi$clumpedValues != null && !otherMixin.papi$clumpedValues.isEmpty()) {
                    for (Int2IntMap.Entry e : otherMixin.papi$clumpedValues.int2IntEntrySet()) {
                        this.papi$addClumpedEntry(e.getIntKey(), e.getIntValue());
                    }
                    otherMixin.papi$clumpedValues.clear();
                } else {
                    this.papi$addClumpedEntry(otherValue, otherMixin.count);
                }
                this.count = Math.max(1, this.count);
            }

            selfTotal += otherTotal;
            this.age = Math.min(this.age, otherMixin.age);
            otherMixin.count = 0; // Prevent phantom touches if other is still in tick list
            other.discard();
        }

        ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void papi$saveClumpedValues(ValueOutput output, CallbackInfo ci) {
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        if (map != null && !map.isEmpty()) {
            int[] packed = new int[map.size() * 2];
            int idx = 0;
            for (Int2IntMap.Entry e : map.int2IntEntrySet()) {
                packed[idx++] = e.getIntKey();
                packed[idx++] = e.getIntValue();
            }
            output.putIntArray("GluonOrbValues", packed);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void papi$loadClumpedValues(ValueInput input, CallbackInfo ci) {
        this.papi$clumpedValues = null;
        int[] packed = input.getIntArray("GluonOrbValues")
                .orElseGet(() -> input.getIntArray("PapiClumps").orElse(null));
        if (packed != null && packed.length >= 2 && (packed.length & 1) == 0) {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap(packed.length >> 1);
            for (int i = 0; i < packed.length; i += 2) {
                int val = packed[i];
                int cnt = packed[i + 1];
                if (val > 0 && cnt > 0) {
                    long sum = (long) map.get(val) + (long) cnt;
                    map.put(val, (int) Math.min(Integer.MAX_VALUE, sum));
                }
            }
            if (!map.isEmpty()) {
                this.papi$clumpedValues = map;
                this.count = Math.max(1, this.count);
            }
        }
    }

    /**
     * Instantly and safely absorbs all clumped XP on player touch.
     * Preserves exact vanilla Mending calculations and Player experience points.
     */
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void papi$fastPlayerTouch(Player player, CallbackInfo ci) {
        if (this.isRemoved() || this.count <= 0 || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (player.takeXpDelay == 0) {
            int preEventVal = this.getValue();
            IEventBus eventBus = NeoForge.EVENT_BUS;
            PlayerXpEvent.PickupXp event = new PlayerXpEvent.PickupXp(player, (ExperienceOrb) (Object) this);
            if (eventBus.post(event).isCanceled()) {
                ci.cancel();
                return;
            }

            player.take((ExperienceOrb) (Object) this, 1);
            player.takeXpDelay = 2;
            int orbValue = this.getValue();
            int eventDelta = orbValue - preEventVal;
            int totalCount = this.count;
            Int2IntOpenHashMap clumped = this.papi$clumpedValues;

            // Zero out and discard immediately to prevent re-entrant or multi-player duplication
            this.count = 0;
            this.papi$clumpedValues = null;
            this.discard();

            if (clumped == null || clumped.isEmpty()) {
                if (orbValue > 0) {
                    for (int i = 0; i < totalCount; i++) {
                        int remainingXp = this.repairPlayerItems(serverPlayer, orbValue);
                        if (remainingXp == orbValue) {
                            // No damaged items remaining! All mending gear is at 100% durability.
                            // Award all remaining XP in a single call.
                            int restCount = totalCount - i;
                            long totalRestXp = (long) restCount * (long) orbValue;
                            papi$awardTotalXp(player, totalRestXp);
                            break;
                        } else if (remainingXp > 0) {
                            player.giveExperiencePoints(remainingXp);
                        }
                    }
                }
            } else {
                int maxNoRepairVal = 0;
                long pendingPlayerXp = eventDelta;
                for (Int2IntMap.Entry entry : clumped.int2IntEntrySet()) {
                    int val = entry.getIntKey();
                    int cnt = entry.getIntValue();
                    if (val <= 0 || cnt <= 0) {
                        continue;
                    }
                    if (val <= maxNoRepairVal) {
                        pendingPlayerXp += (long) val * (long) cnt;
                        continue;
                    }
                    for (int i = 0; i < cnt; i++) {
                        int remainingXp = this.repairPlayerItems(serverPlayer, val);
                        if (remainingXp == val) {
                            maxNoRepairVal = val;
                            pendingPlayerXp += (long) (cnt - i) * (long) val;
                            break;
                        } else if (remainingXp > 0) {
                            pendingPlayerXp += remainingXp;
                        }
                    }
                }
                if (pendingPlayerXp > 0L) {
                    papi$awardTotalXp(player, pendingPlayerXp);
                }
            }

            ci.cancel();
        }
    }
}
