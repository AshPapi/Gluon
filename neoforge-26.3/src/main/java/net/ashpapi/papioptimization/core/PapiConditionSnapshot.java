package net.ashpapi.papioptimization.core;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public record PapiConditionSnapshot(
        int sourceSize,
        MemoryModuleType<?>[] keys,
        MemoryStatus[] statuses
) {
}
