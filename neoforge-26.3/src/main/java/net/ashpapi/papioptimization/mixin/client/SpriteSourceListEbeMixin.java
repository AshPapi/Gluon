package net.ashpapi.papioptimization.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SpriteSourceList.class, priority = 1500)
public abstract class SpriteSourceListEbeMixin {

    @Shadow
    @Final
    private List<SpriteSource> sources;

    @Inject(
        method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/atlas/SpriteSourceList;",
        at = @At("RETURN")
    )
    private static void papi$appendBlockEntitySpritesToBlockAtlas(
            ResourceManager resourceManager,
            Identifier location,
            CallbackInfoReturnable<SpriteSourceList> cir
    ) {
        if (!AtlasIds.BLOCKS.equals(location)) {
            return;
        }
        SpriteSourceList list = cir.getReturnValue();
        if (list == null) {
            return;
        }
        List<SpriteSource> src = ((SpriteSourceListEbeMixin) (Object) list).sources;
        if (src instanceof ArrayList<SpriteSource>) {
            src.add(new DirectoryLister("entity/chest", "entity/chest/"));
            src.add(new DirectoryLister("entity/shulker", "entity/shulker/"));
            src.add(new DirectoryLister("entity/decorated_pot", "entity/decorated_pot/"));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/skeleton/skeleton")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/skeleton/wither_skeleton")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/zombie/zombie")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/creeper/creeper")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/piglin/piglin")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/enderdragon/dragon")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/banner/banner_base")));
            src.add(new SingleFile(Identifier.withDefaultNamespace("entity/banner/base")));
            PapiEbeRuntime.markAtlasPatched();
        }
    }
}
