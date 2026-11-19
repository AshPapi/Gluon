package net.ashpapi.papioptimization.mixin.memory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = EntityTickList.class, priority = 1500)
public abstract class EntityTickListLeakMixin {

    @Shadow private Int2ObjectMap<Entity> active;
    @Shadow private Int2ObjectMap<Entity> passive;

    @Inject(method = "forEach", at = @At("RETURN"))
    private void papi$clearPassiveBuffer(Consumer<Entity> output, CallbackInfo ci) {
        if (this.passive != this.active && !this.passive.isEmpty()) {
            this.passive.clear();
        }
    }
}
