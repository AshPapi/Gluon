package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.access.PapiGuiNodeBounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.state.gui.GuiRenderState$Node", priority = 1500)
public abstract class GuiRenderStateNodeMixin implements PapiGuiNodeBounds {

    @Unique
    private boolean papi$hasBounds;

    @Unique
    private int papi$minX = Integer.MAX_VALUE;

    @Unique
    private int papi$minY = Integer.MAX_VALUE;

    @Unique
    private int papi$maxX = Integer.MIN_VALUE;

    @Unique
    private int papi$maxY = Integer.MIN_VALUE;

    @Unique
    private void papi$expandBounds(ScreenArea area) {
        if (area == null) {
            return;
        }
        ScreenRectangle rect = area.bounds();
        if (rect == null) {
            return;
        }
        int left = rect.left();
        int top = rect.top();
        int right = rect.right();
        int bottom = rect.bottom();
        if (!this.papi$hasBounds) {
            this.papi$hasBounds = true;
            this.papi$minX = left;
            this.papi$minY = top;
            this.papi$maxX = right;
            this.papi$maxY = bottom;
        } else {
            if (left < this.papi$minX) this.papi$minX = left;
            if (top < this.papi$minY) this.papi$minY = top;
            if (right > this.papi$maxX) this.papi$maxX = right;
            if (bottom > this.papi$maxY) this.papi$maxY = bottom;
        }
    }

    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void papi$onAddGuiElement(GuiElementRenderState state, CallbackInfo ci) {
        papi$expandBounds(state);
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void papi$onAddItem(GuiItemRenderState state, CallbackInfo ci) {
        papi$expandBounds(state);
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void papi$onAddText(GuiTextRenderState state, CallbackInfo ci) {
        papi$expandBounds(state);
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void papi$onAddPip(PictureInPictureRenderState state, CallbackInfo ci) {
        papi$expandBounds(state);
    }

    @Override
    public boolean papi$mayIntersect(int left, int top, int right, int bottom) {
        if (!this.papi$hasBounds) {
            return false;
        }
        return left < this.papi$maxX && right > this.papi$minX && top < this.papi$maxY && bottom > this.papi$minY;
    }
}
