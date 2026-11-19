package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = ServerEntity.class, priority = 1500)
public abstract class ServerEntityFastMixin {

    @WrapOperation(
        method = "sendChanges",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;equals(Ljava/lang/Object;)Z"
        )
    )
    private boolean papi$fastPassengersEquals(List<?> passengers, Object other, Operation<Boolean> original) {
        if (passengers == other) {
            return true;
        }
        if (other instanceof List<?> otherList && passengers.isEmpty() && otherList.isEmpty()) {
            return true;
        }
        return original.call(passengers, other);
    }
}
