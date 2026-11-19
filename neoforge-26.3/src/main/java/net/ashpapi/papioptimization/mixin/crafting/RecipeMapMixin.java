package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.ashpapi.papioptimization.access.FastRecipeMap;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.crafting.PapiRecipeIndex;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Mixin(value = RecipeMap.class, priority = 1500)
public abstract class RecipeMapMixin implements FastRecipeMap {

    @Shadow
    public abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

    @Unique
    private final ConcurrentHashMap<RecipeType<?>, PapiRecipeIndex<?, ?>> papi$indices = new ConcurrentHashMap<>();

    @Unique
    private volatile int papi$tagEpoch = -1;

    @Inject(method = "order", at = @At("RETURN"))
    private void papi$onOrder(Object2IntMap<ResourceKey<Recipe<?>>> map, CallbackInfo ci) {
        this.papi$indices.clear();
        this.papi$tagEpoch = PapiTagEpoch.current();
    }

    @WrapMethod(method = "getRecipesFor")
    private <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> papi$fastGetRecipesFor(
            RecipeType<T> type,
            I input,
            Level level,
            Operation<Stream<RecipeHolder<T>>> original
    ) {
        if (input.isEmpty()) {
            return Stream.empty();
        }
        return this.papi$getOrCreateIndex(type).findMatchingStream(input, level);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, T extends Recipe<I>> PapiRecipeIndex<I, T> papi$getOrCreateIndex(RecipeType<T> type) {
        int currentEpoch = PapiTagEpoch.current();
        if (this.papi$tagEpoch != currentEpoch) {
            synchronized (this.papi$indices) {
                if (this.papi$tagEpoch != currentEpoch) {
                    this.papi$indices.clear();
                    this.papi$tagEpoch = currentEpoch;
                }
            }
        }
        PapiRecipeIndex<?, ?> index = this.papi$indices.get(type);
        if (index == null) {
            index = this.papi$indices.computeIfAbsent(type, k -> new PapiRecipeIndex<>(this.byType(type)));
        }
        return (PapiRecipeIndex<I, T>) index;
    }

    @Override
    public <I extends RecipeInput, T extends Recipe<I>> RecipeHolder<T>[] papi$getRecipesArray(RecipeType<T> type) {
        return this.papi$getOrCreateIndex(type).orderedRecipes();
    }
}
