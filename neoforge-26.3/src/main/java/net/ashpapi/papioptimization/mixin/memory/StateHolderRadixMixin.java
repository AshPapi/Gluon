package net.ashpapi.papioptimization.mixin.memory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiRadixStateHolder;
import net.ashpapi.papioptimization.memory.PapiRadixTable;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = StateHolder.class, priority = 1500)
public abstract class StateHolderRadixMixin<O, S> implements PapiRadixStateHolder<S> {

    @Shadow @Final protected O owner;
    @Shadow @Final private Comparable<?>[] propertyValues;
    @Shadow private S[][] neighbors;

    @Unique
    private PapiRadixTable<S> papi$radixTable;

    @Unique
    private int papi$stateIndex;

    @Override
    public void papi$attachRadix(PapiRadixTable<S> table, int stateIndex) {
        this.papi$radixTable = table;
        this.papi$stateIndex = stateIndex;
        this.neighbors = null;
    }

    @Override
    public boolean papi$hasRadix() {
        return this.papi$radixTable != null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapMethod(method = "setValueInternal")
    private <T extends Comparable<T>, V extends T> S papi$fastSetValueInternal(
            Property<T> property,
            int propIdx,
            V value,
            Operation<S> original
    ) {
        PapiRadixTable<S> table = this.papi$radixTable;
        if (table == null || propIdx < 0 || propIdx >= table.valueCounts.length) {
            return original.call(property, propIdx, value);
        }
        int newInternal = property.getInternalIndex(value);
        if (newInternal < 0 || newInternal >= table.valueCounts[propIdx]) {
            throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value"
            );
        }
        Comparable<?> currentVal = this.propertyValues[propIdx];
        if (currentVal == value || value.equals(currentVal)) {
            return (S) (Object) this;
        }
        int oldInternal = ((Property) property).getInternalIndex(currentVal);
        int targetIdx = this.papi$stateIndex + (newInternal - oldInternal) * table.strides[propIdx];
        return table.states[targetIdx];
    }
}
