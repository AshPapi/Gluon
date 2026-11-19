package net.ashpapi.papioptimization.mixin.startup;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TagEntry.class, priority = 1500)
public abstract class TagEntryCacheMixin {

    @Shadow @Final private Identifier id;
    @Shadow @Final private boolean tag;

    @Unique
    private ExtraCodecs.TagOrElementLocation papi$cachedLocation;

    @Inject(method = "elementOrTag", at = @At("HEAD"), cancellable = true)
    private void papi$fastElementOrTag(CallbackInfoReturnable<ExtraCodecs.TagOrElementLocation> cir) {
        ExtraCodecs.TagOrElementLocation loc = this.papi$cachedLocation;
        if (loc == null) {
            loc = new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
            this.papi$cachedLocation = loc;
        }
        cir.setReturnValue(loc);
    }
}
