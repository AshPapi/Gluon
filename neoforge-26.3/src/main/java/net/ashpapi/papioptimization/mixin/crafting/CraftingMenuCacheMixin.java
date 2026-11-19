package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiCraftingMenuCache;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CraftingMenu.class, priority = 1500)
public abstract class CraftingMenuCacheMixin {

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void papi$reuseLastMatchedRecipe(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> lastRecipe,
            Operation<Void> original
    ) {
        int epoch = PapiRecipeEpoch.current();
        PapiCraftingMenuCache cache = (menu instanceof PapiCraftingMenuCache c) ? c : null;
        RecipeHolder<CraftingRecipe> effectiveLast = lastRecipe;
        if (effectiveLast == null && cache != null) {
            if (cache.papi$getLastRecipeEpoch() == epoch) {
                effectiveLast = cache.papi$getLastRecipe();
            } else {
                cache.papi$setLastRecipe(null);
            }
        }
        original.call(menu, level, player, craftSlots, resultSlots, effectiveLast);
        if (cache != null) {
            if (resultSlots.getItem(0).isEmpty() || PapiRecipeEpoch.current() != epoch) {
                cache.papi$setLastRecipe(null);
                cache.papi$setLastRecipeEpoch(PapiRecipeEpoch.current());
            } else {
                RecipeHolder<?> used = resultSlots.getRecipeUsed();
                if (used != null && used.value() instanceof CraftingRecipe) {
                    cache.papi$setLastRecipe((RecipeHolder<CraftingRecipe>) used);
                    cache.papi$setLastRecipeEpoch(epoch);
                }
            }
        }
    }
}
