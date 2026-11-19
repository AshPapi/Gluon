package net.ashpapi.papioptimization.mixin.pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NodeEvaluator.class, priority = 1500)
public interface NodeEvaluatorAccessor {

    @Accessor("mob")
    @Nullable Mob papi$getMob();
}
