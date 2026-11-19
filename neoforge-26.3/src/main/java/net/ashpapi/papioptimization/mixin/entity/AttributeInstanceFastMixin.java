package net.ashpapi.papioptimization.mixin.entity;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AttributeInstance.class, priority = 1500)
public abstract class AttributeInstanceFastMixin {

    @Shadow @Final
    private Holder<Attribute> attribute;

    @Shadow @Final
    private Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation;

    @Shadow @Final
    private Map<Identifier, AttributeModifier> modifierById;

    @Shadow
    private double baseValue;

    @Inject(method = "calculateValue", at = @At("HEAD"), cancellable = true)
    private void papi$fastCalculateValue(CallbackInfoReturnable<Double> cir) {
        double base = this.baseValue;
        if (this.modifierById.isEmpty()) {
            cir.setReturnValue(this.attribute.value().sanitizeValue(base));
            return;
        }
        Map<Identifier, AttributeModifier> addMap = this.modifiersByOperation.get(AttributeModifier.Operation.ADD_VALUE);
        if (addMap != null && !addMap.isEmpty()) {
            for (AttributeModifier mod : addMap.values()) {
                base += mod.amount();
            }
        }
        double result = base;
        Map<Identifier, AttributeModifier> mulBaseMap = this.modifiersByOperation.get(AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (mulBaseMap != null && !mulBaseMap.isEmpty()) {
            for (AttributeModifier mod : mulBaseMap.values()) {
                result += base * mod.amount();
            }
        }
        Map<Identifier, AttributeModifier> mulTotalMap = this.modifiersByOperation.get(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (mulTotalMap != null && !mulTotalMap.isEmpty()) {
            for (AttributeModifier mod : mulTotalMap.values()) {
                result *= 1.0 + mod.amount();
            }
        }
        cir.setReturnValue(this.attribute.value().sanitizeValue(result));
    }
}
