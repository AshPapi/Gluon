package net.ashpapi.papioptimization.mixin.memory;

import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Property.class, priority = 1500)
public abstract class PropertyInternMixin<T extends Comparable<T>> {

    @Shadow @Final @Mutable private String name;
    @Shadow @Final private Class<T> clazz;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$internName(String name, Class<T> clazz, CallbackInfo ci) {
        if (this.name != null) {
            this.name = this.name.intern();
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void papi$fastEquals(Object o, CallbackInfoReturnable<Boolean> cir) {
        if (this == o) {
            cir.setReturnValue(true);
            return;
        }
        if (o instanceof Property<?> that) {
            PropertyInternMixin<?> other = (PropertyInternMixin<?>) (Object) that;
            cir.setReturnValue(this.clazz.equals(other.clazz) && this.name == other.name);
        } else {
            cir.setReturnValue(false);
        }
    }
}
