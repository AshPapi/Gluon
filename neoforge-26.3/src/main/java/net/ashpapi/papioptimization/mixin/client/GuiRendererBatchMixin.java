package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiRenderer.class, priority = 1500)
public abstract class GuiRendererBatchMixin {

    @Unique
    private static final int PAPI$LOOKAHEAD_WINDOW = 64;

    @Unique
    private static final int PAPI$MAX_COALESCE_ELEMENTS = 4096;

    @Unique
    private final ArrayList<GuiElementRenderState> papi$batchInput = new ArrayList<>(128);

    @Unique
    private boolean[] papi$emitted = new boolean[128];

    @Unique
    private final int[] papi$blockL = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockT = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockR = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockB = new int[PAPI$LOOKAHEAD_WINDOW];

    @WrapOperation(
        method = "addElementsToMeshes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachElement(Ljava/util/function/Consumer;Lnet/minecraft/client/renderer/state/gui/GuiRenderState$TraverseRange;)V"
        )
    )
    private void papi$coalesceNonOverlappingGuiBatches(
            GuiRenderState renderState,
            Consumer<GuiElementRenderState> consumer,
            GuiRenderState.TraverseRange range,
            Operation<Void> original
    ) {
        ArrayList<GuiElementRenderState> input = this.papi$batchInput;
        input.clear();
        try {
            original.call(renderState, (Consumer<GuiElementRenderState>) input::add, range);
            int n = input.size();
            if (n <= 2 || n > PAPI$MAX_COALESCE_ELEMENTS) {
                for (int i = 0; i < n; i++) {
                    consumer.accept(input.get(i));
                }
                return;
            }
            boolean[] emitted = this.papi$emitted;
            if (emitted.length < n) {
                emitted = new boolean[Math.max(n, emitted.length << 1)];
                this.papi$emitted = emitted;
            } else {
                Arrays.fill(emitted, 0, n, false);
            }

            int[] bL = this.papi$blockL;
            int[] bT = this.papi$blockT;
            int[] bR = this.papi$blockR;
            int[] bB = this.papi$blockB;

            for (int i = 0; i < n; i++) {
                if (emitted[i]) {
                    continue;
                }
                GuiElementRenderState first = input.get(i);
                emitted[i] = true;
                consumer.accept(first);

                RenderPipeline firstPipe = first.pipeline();
                if (firstPipe == null || firstPipe.getPrimitiveTopology().connectedPrimitives) {
                    continue;
                }
                ScreenRectangle firstBounds = first.bounds();
                if (firstBounds == null) {
                    continue;
                }
                TextureSetup firstTex = first.textureSetup();
                ScreenRectangle firstScissor = first.scissorArea();

                int limit = Math.min(n, i + 1 + PAPI$LOOKAHEAD_WINDOW);
                int pendingCount = 0;
                int uMinX = Integer.MAX_VALUE;
                int uMinY = Integer.MAX_VALUE;
                int uMaxX = Integer.MIN_VALUE;
                int uMaxY = Integer.MIN_VALUE;

                for (int j = i + 1; j < limit; j++) {
                    if (emitted[j]) {
                        continue;
                    }
                    GuiElementRenderState cand = input.get(j);
                    ScreenRectangle cb = cand.bounds();
                    if (cb == null) {
                        break;
                    }
                    int cl = cb.left();
                    int ct = cb.top();
                    int cr = cb.right();
                    int cbBot = cb.bottom();
                    if (cl > cr || ct > cbBot) {
                        break;
                    }

                    if (cand.pipeline() == firstPipe
                            && Objects.equals(cand.scissorArea(), firstScissor)
                            && Objects.equals(cand.textureSetup(), firstTex)) {
                        boolean blocked = false;
                        if (pendingCount > 0 && cl < uMaxX && cr > uMinX && ct < uMaxY && cbBot > uMinY) {
                            for (int k = 0; k < pendingCount; k++) {
                                if (cl < bR[k] && cr > bL[k] && ct < bB[k] && cbBot > bT[k]) {
                                    blocked = true;
                                    break;
                                }
                            }
                        }
                        if (!blocked) {
                            emitted[j] = true;
                            consumer.accept(cand);
                            continue;
                        }
                    }

                    if (pendingCount >= PAPI$LOOKAHEAD_WINDOW) {
                        break;
                    }
                    bL[pendingCount] = cl;
                    bT[pendingCount] = ct;
                    bR[pendingCount] = cr;
                    bB[pendingCount] = cbBot;
                    pendingCount++;
                    if (cl < uMinX) uMinX = cl;
                    if (ct < uMinY) uMinY = ct;
                    if (cr > uMaxX) uMaxX = cr;
                    if (cbBot > uMaxY) uMaxY = cbBot;
                }
            }
        } finally {
            input.clear();
        }
    }
}
