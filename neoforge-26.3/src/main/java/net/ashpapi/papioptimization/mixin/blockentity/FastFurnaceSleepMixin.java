package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Fast Furnace Sleeping:
 * Bypasses redundant server ticks and SingleRecipeInput allocations when a furnace
 * is unlit, has no cooking progress, and lacks either smelting input or fuel.
 * Resumes instantaneously the tick items are inserted. 100% vanilla parity.
 */
@Mixin(value = AbstractFurnaceBlockEntity.class, priority = 1500)
public abstract class FastFurnaceSleepMixin {

    @Shadow protected NonNullList<ItemStack> items;
    @Shadow private int litTimeRemaining;
    @Shadow private int cookingTimer;

    @Unique
    private @Nullable RecipeHolder<? extends AbstractCookingRecipe> papi$curRecipe;

    @Unique
    private @Nullable ItemStack papi$cachedInputSnapshot;

    @Unique
    private int papi$cachedRecipeEpoch = -1;

    @Unique
    private boolean papi$inputFailed;

    @Unique
    private boolean papi$outputBlocked;

    @Unique
    private @Nullable ItemStack papi$blockedOutputSnapshot;

    @Unique
    private int papi$blockedOutputCount;

    @Unique
    private static final ThreadLocal<AbstractFurnaceBlockEntity @Nullable []> PAPI$ACTIVE_FURNACE =
            ThreadLocal.withInitial(() -> new AbstractFurnaceBlockEntity[1]);

    @Unique
    private boolean papi$matchesInputKey(ItemStack input, int recipeEpoch) {
        ItemStack snap = this.papi$cachedInputSnapshot;
        return this.papi$cachedRecipeEpoch == recipeEpoch
                && snap != null
                && ItemStack.isSameItemSameComponents(snap, input);
    }

    @Unique
    private boolean papi$matchesBlockedOutput(ItemStack output) {
        if (output.isEmpty()) {
            return this.papi$blockedOutputSnapshot == null && this.papi$blockedOutputCount == 0;
        }
        ItemStack snap = this.papi$blockedOutputSnapshot;
        return snap != null
                && this.papi$blockedOutputCount == output.getCount()
                && ItemStack.isSameItemSameComponents(snap, output);
    }

    @Unique
    private void papi$invalidateFurnaceCache() {
        this.papi$inputFailed = false;
        this.papi$outputBlocked = false;
    }

    @WrapMethod(method = "serverTick")
    private static void papi$sleepIdleFurnace(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity furnace,
            Operation<Void> original
    ) {
        FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;

        // Active furnace: burning or currently cooking
        if (access.litTimeRemaining <= 0 && access.cookingTimer <= 0) {
            // Unlit and not cooking: if input is empty or fuel is empty, furnace cannot perform work
            ItemStack input = access.items.get(0); // SLOT_INPUT
            ItemStack fuel = access.items.get(1);  // SLOT_FUEL

            if (input.isEmpty() || fuel.isEmpty()) {
                return;
            }

            int recipeEpoch = PapiRecipeEpoch.current();
            if (access.papi$matchesInputKey(input, recipeEpoch)) {
                if (access.papi$inputFailed) {
                    return;
                }
                if (access.papi$outputBlocked && access.papi$matchesBlockedOutput(access.items.get(2))) {
                    return;
                }
            }
        }

        AbstractFurnaceBlockEntity[] holder = PAPI$ACTIVE_FURNACE.get();
        AbstractFurnaceBlockEntity prev = holder[0];
        holder[0] = furnace;
        try {
            original.call(level, pos, state, furnace);
        } finally {
            holder[0] = prev;
        }
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;"
        )
    )
    private static Optional<RecipeHolder<? extends AbstractCookingRecipe>> papi$cachedFurnaceRecipeLookup(
            RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> check,
            RecipeInput recipeInput,
            ServerLevel level,
            Operation<Optional<RecipeHolder<? extends AbstractCookingRecipe>>> original
    ) {
        AbstractFurnaceBlockEntity furnace = PAPI$ACTIVE_FURNACE.get()[0];
        if (furnace == null || !(recipeInput instanceof SingleRecipeInput single)) {
            return original.call(check, recipeInput, level);
        }
        ItemStack input = single.item();
        if (input.isEmpty()) {
            return Optional.empty();
        }
        FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;
        int recipeEpoch = PapiRecipeEpoch.current();
        if (access.papi$matchesInputKey(input, recipeEpoch)) {
            if (access.papi$inputFailed) {
                return Optional.empty();
            }
            if (access.papi$curRecipe != null) {
                return Optional.of(access.papi$curRecipe);
            }
        }
        Optional<RecipeHolder<? extends AbstractCookingRecipe>> result = original.call(check, recipeInput, level);
        access.papi$cachedInputSnapshot = input.copyWithCount(1);
        access.papi$cachedRecipeEpoch = recipeEpoch;
        if (result.isPresent()) {
            access.papi$curRecipe = result.get();
            access.papi$inputFailed = false;
        } else {
            access.papi$curRecipe = null;
            access.papi$inputFailed = true;
            access.papi$outputBlocked = false;
        }
        return result;
    }

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;canBurn(Lnet/minecraft/core/NonNullList;ILnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private static boolean papi$trackBlockedFurnaceOutput(
            NonNullList<ItemStack> items,
            int maxStackSize,
            ItemStack assembled,
            Operation<Boolean> original
    ) {
        boolean canBurn = original.call(items, maxStackSize, assembled);
        AbstractFurnaceBlockEntity furnace = PAPI$ACTIVE_FURNACE.get()[0];
        if (furnace != null) {
            FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;
            if (!canBurn) {
                ItemStack out = items.get(2);
                access.papi$outputBlocked = true;
                if (out.isEmpty()) {
                    access.papi$blockedOutputSnapshot = null;
                    access.papi$blockedOutputCount = 0;
                } else {
                    access.papi$blockedOutputSnapshot = out.copyWithCount(1);
                    access.papi$blockedOutputCount = out.getCount();
                }
            } else {
                access.papi$outputBlocked = false;
            }
        }
        return canBurn;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"))
    private void papi$onFurnaceSetItem(int slot, ItemStack stack, boolean isSetChanged, CallbackInfo ci) {
        this.papi$invalidateFurnaceCache();
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }
}
