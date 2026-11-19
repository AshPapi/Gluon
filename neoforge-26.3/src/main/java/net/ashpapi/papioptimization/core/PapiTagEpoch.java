package net.ashpapi.papioptimization.core;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

public final class PapiTagEpoch {

    private static final AtomicInteger EPOCH = new AtomicInteger(1);

    private static final ClassValue<Boolean> DEFAULT_PATH_TYPE_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!type.getName().startsWith("net.minecraft.")) {
                return Boolean.FALSE;
            }
            try {
                Class<?> declaring = type.getMethod(
                        "getBlockPathType",
                        BlockState.class,
                        BlockGetter.class,
                        BlockPos.class,
                        Mob.class
                ).getDeclaringClass();
                return declaring == IBlockExtension.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    private PapiTagEpoch() {}

    public static int current() {
        return EPOCH.get();
    }

    public static void bump() {
        EPOCH.incrementAndGet();
    }

    public static boolean isCacheableBlock(Block block) {
        return block != null && DEFAULT_PATH_TYPE_CACHE.get(block.getClass());
    }
}
