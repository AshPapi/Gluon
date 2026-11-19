package net.ashpapi.papioptimization.client;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.saveddata.maps.MapDecoration;

public final class MapDecorationCache {
    private static final int MAX_CACHE_SIZE = 512;
    private static final Map<MapDecoration, MapRenderState.MapDecorationRenderState> CACHE = new ConcurrentHashMap<>(128);
    private static volatile WeakReference<TextureAtlas> LAST_ATLAS = new WeakReference<>(null);
    private static volatile int LAST_EPOCH = -1;

    private MapDecorationCache() {}

    public static MapRenderState.MapDecorationRenderState getOrCreate(MapDecoration decoration, TextureAtlas sprites) {
        int epoch = PapiClientCaches.epoch();
        if (LAST_EPOCH != epoch || LAST_ATLAS.get() != sprites) {
            CACHE.clear();
            LAST_ATLAS = new WeakReference<>(sprites);
            LAST_EPOCH = epoch;
        }
        if (!decoration.renderOnFrame()) {
            return buildState(decoration, sprites);
        }
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(decoration, dec -> buildState(dec, sprites));
    }

    private static MapRenderState.MapDecorationRenderState buildState(MapDecoration dec, TextureAtlas sprites) {
        MapRenderState.MapDecorationRenderState state = new MapRenderState.MapDecorationRenderState();
        state.type = dec.type();
        state.atlasSprite = sprites.getSprite(dec.getSpriteLocation());
        state.x = dec.x();
        state.y = dec.y();
        state.rot = dec.rot();
        state.name = dec.name().orElse(null);
        state.renderOnFrame = dec.renderOnFrame();
        return state;
    }

    public static void clear() {
        CACHE.clear();
        LAST_ATLAS = new WeakReference<>(null);
        LAST_EPOCH = -1;
    }
}
