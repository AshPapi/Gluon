package net.ashpapi.papioptimization.mixin.projectile;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ProjectileUtil.class, priority = 1500)
public abstract class ProjectileUtilFastMixin {

    @WrapMethod(
        method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"
    )
    private static @Nullable EntityHitResult papi$getEntityHitResult(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            float entityMargin,
            Operation<@Nullable EntityHitResult> original
    ) {
        double fromX = from.x;
        double fromY = from.y;
        double fromZ = from.z;
        double toX = to.x;
        double toY = to.y;
        double toZ = to.z;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;

        double margin = (double) entityMargin;
        double nearestScale = 1.0;
        Entity nearestEntity = null;

        List<Entity> entities = level.getEntities(source, targetSearchArea, matching);
        for (int i = 0, len = entities.size(); i < len; i++) {
            Entity entity = entities.get(i);
            AABB bb = entity.getBoundingBox();
            double minX = bb.minX - margin;
            double minY = bb.minY - margin;
            double minZ = bb.minZ - margin;
            double maxX = bb.maxX + margin;
            double maxY = bb.maxY + margin;
            double maxZ = bb.maxZ + margin;
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                continue;
            }

            double scale = papi$clipRayScale(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    fromX, fromY, fromZ, dx, dy, dz,
                    nearestScale
            );
            if (scale > 0.0 && scale < nearestScale) {
                nearestEntity = entity;
                nearestScale = scale;
            }
        }

        if (nearestEntity == null) {
            return null;
        }

        Vec3 hitPos = new Vec3(fromX + nearestScale * dx, fromY + nearestScale * dy, fromZ + nearestScale * dz);
        return new EntityHitResult(nearestEntity, hitPos);
    }

    @Unique
    private static double papi$clipRayScale(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double fromX, double fromY, double fromZ,
            double dx, double dy, double dz,
            double maxScale
    ) {
        double scale = maxScale;
        boolean hit = false;

        if (dx > 1.0E-7) {
            double s = (minX - fromX) / dx;
            if (s > 0.0 && s < scale) {
                double py = fromY + s * dy;
                double pz = fromZ + s * dz;
                if (minY - 1.0E-7 < py && py < maxY + 1.0E-7 && minZ - 1.0E-7 < pz && pz < maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dx < -1.0E-7) {
            double s = (maxX - fromX) / dx;
            if (s > 0.0 && s < scale) {
                double py = fromY + s * dy;
                double pz = fromZ + s * dz;
                if (minY - 1.0E-7 < py && py < maxY + 1.0E-7 && minZ - 1.0E-7 < pz && pz < maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        if (dy > 1.0E-7) {
            double s = (minY - fromY) / dy;
            if (s > 0.0 && s < scale) {
                double px = fromX + s * dx;
                double pz = fromZ + s * dz;
                if (minX - 1.0E-7 < px && px < maxX + 1.0E-7 && minZ - 1.0E-7 < pz && pz < maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dy < -1.0E-7) {
            double s = (maxY - fromY) / dy;
            if (s > 0.0 && s < scale) {
                double px = fromX + s * dx;
                double pz = fromZ + s * dz;
                if (minX - 1.0E-7 < px && px < maxX + 1.0E-7 && minZ - 1.0E-7 < pz && pz < maxZ + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        if (dz > 1.0E-7) {
            double s = (minZ - fromZ) / dz;
            if (s > 0.0 && s < scale) {
                double px = fromX + s * dx;
                double py = fromY + s * dy;
                if (minX - 1.0E-7 < px && px < maxX + 1.0E-7 && minY - 1.0E-7 < py && py < maxY + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        } else if (dz < -1.0E-7) {
            double s = (maxZ - fromZ) / dz;
            if (s > 0.0 && s < scale) {
                double px = fromX + s * dx;
                double py = fromY + s * dy;
                if (minX - 1.0E-7 < px && px < maxX + 1.0E-7 && minY - 1.0E-7 < py && py < maxY + 1.0E-7) {
                    scale = s;
                    hit = true;
                }
            }
        }

        return hit ? scale : -1.0;
    }
}
