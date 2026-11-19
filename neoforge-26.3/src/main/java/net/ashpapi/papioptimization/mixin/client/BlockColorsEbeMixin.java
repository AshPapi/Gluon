package net.ashpapi.papioptimization.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockColors.class, priority = 1500)
public abstract class BlockColorsEbeMixin {

    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void papi$registerBannerTints(CallbackInfoReturnable<BlockColors> cir) {
        BlockColors colors = cir.getReturnValue();
        if (colors == null) {
            return;
        }
        List<BlockTintSource> dyeSources = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            dyeSources.add(BlockTintSources.constant(DyeColor.byId(i).getTextureDiffuseColor()));
        }
        List<Block> banners = new ArrayList<>(32);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof AbstractBannerBlock && block.getClass().getName().startsWith("net.minecraft.")) {
                banners.add(block);
            }
        }
        if (!banners.isEmpty()) {
            colors.register(dyeSources, banners.toArray(new Block[0]));
        }
    }
}
