package net.ashpapi.papioptimization.mixin.crafting;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Ingredient.class, priority = 1500)
public interface IngredientAccessor {
    @Accessor("customIngredient")
    @Nullable ICustomIngredient papi$getCustomIngredient();
}
