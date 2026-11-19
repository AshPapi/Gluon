package net.ashpapi.papioptimization.mixin.startup;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExtraCodecs.TagOrElementLocation.class, priority = 1500)
public abstract class TagOrElementLocationCacheMixin {

    @Shadow @Final private Identifier id;
    @Shadow @Final private boolean tag;

    @Unique
    private String papi$cachedDecoratedId;

    @Inject(method = "decoratedId", at = @At("HEAD"), cancellable = true)
    private void papi$fastDecoratedId(CallbackInfoReturnable<String> cir) {
        String s = this.papi$cachedDecoratedId;
        if (s == null) {
            s = this.tag ? "#" + this.id : this.id.toString();
            this.papi$cachedDecoratedId = s;
        }
        cir.setReturnValue(s);
    }
}
