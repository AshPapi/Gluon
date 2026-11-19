package net.ashpapi.papioptimization.mixin.memory;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import net.ashpapi.papioptimization.access.PapiRadixStateHolder;
import net.ashpapi.papioptimization.memory.PapiRadixTable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StateDefinition.class, priority = 1500)
public abstract class StateDefinitionRadixMixin<O, S extends StateHolder<O, S>> {

    @Shadow @Final private ImmutableList<S> states;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapMethod(method = "createMultiPropertyStates")
    private static <O, S extends StateHolder<O, S>> ImmutableList<S> papi$createStatesWithoutNeighborMatrix(
            O owner,
            StateDefinition.Factory<O, S> factory,
            Map<String, Property<?>> propertiesByName,
            Operation<ImmutableList<S>> original
    ) {
        int propCount = propertiesByName.size();
        if (propCount <= 1) {
            return original.call(owner, factory, propertiesByName);
        }

        Property<?>[] keys = propertiesByName.values().toArray(new Property<?>[propCount]);
        Comparable<?>[][] valueLists = new Comparable<?>[propCount][];
        long totalLong = 1L;
        for (int k = 0; k < propCount; k++) {
            Property p = keys[k];
            List<?> possible = p.getPossibleValues();
            int size = possible.size();
            if (size <= 0) {
                return original.call(owner, factory, propertiesByName);
            }
            totalLong *= (long) size;
            if (totalLong > 262144L) {
                return original.call(owner, factory, propertiesByName);
            }
            Comparable<?>[] vals = new Comparable<?>[size];
            for (int v = 0; v < size; v++) {
                Comparable val = (Comparable) possible.get(v);
                if (p.getInternalIndex(val) != v) {
                    return original.call(owner, factory, propertiesByName);
                }
                vals[v] = val;
            }
            valueLists[k] = vals;
        }

        int totalCount = (int) totalLong;
        int[] valueCounts = new int[propCount];
        for (int k = 0; k < propCount; k++) {
            valueCounts[k] = valueLists[k].length;
        }
        int[] strides = new int[propCount];
        strides[propCount - 1] = 1;
        for (int k = propCount - 2; k >= 0; k--) {
            strides[k] = strides[k + 1] * valueCounts[k + 1];
        }

        S[] stateArray = (S[]) new StateHolder[totalCount];
        PapiRadixTable<S> table = new PapiRadixTable<>(stateArray, strides, valueCounts);
        int[] odometer = new int[propCount];

        for (int i = 0; i < totalCount; i++) {
            Comparable<?>[] stateValues = new Comparable<?>[propCount];
            for (int k = 0; k < propCount; k++) {
                stateValues[k] = valueLists[k][odometer[k]];
            }
            S state = factory.create(owner, keys, stateValues);
            stateArray[i] = state;
            ((PapiRadixStateHolder<S>) state).papi$attachRadix(table, i);

            for (int k = propCount - 1; k >= 0; k--) {
                int next = odometer[k] + 1;
                if (next < valueLists[k].length) {
                    odometer[k] = next;
                    break;
                }
                odometer[k] = 0;
            }
        }

        return ImmutableList.copyOf(stateArray);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$buildRadixTable(CallbackInfo ci) {
        int count = this.states.size();
        if (count <= 1) {
            return;
        }
        S first = this.states.get(0);
        if (((PapiRadixStateHolder<S>) first).papi$hasRadix()) {
            return;
        }
        Property<?>[] keys = first.getProperties().toArray(new Property<?>[0]);
        int propCount = keys.length;
        if (propCount == 0) {
            return;
        }

        int[] valueCounts = new int[propCount];
        for (int k = 0; k < propCount; k++) {
            valueCounts[k] = keys[k].getPossibleValues().size();
        }
        int[] strides = new int[propCount];
        strides[propCount - 1] = 1;
        for (int k = propCount - 2; k >= 0; k--) {
            strides[k] = strides[k + 1] * valueCounts[k + 1];
        }

        S[] stateArray = (S[]) new StateHolder[count];
        for (int i = 0; i < count; i++) {
            S st = this.states.get(i);
            int computedIdx = 0;
            for (int k = 0; k < propCount; k++) {
                Property p = keys[k];
                Comparable val = st.getValue(p);
                int internalIdx = p.getInternalIndex(val);
                if (internalIdx < 0 || internalIdx >= valueCounts[k]) {
                    return;
                }
                computedIdx += internalIdx * strides[k];
            }
            if (computedIdx != i) {
                return;
            }
            stateArray[i] = st;
        }

        PapiRadixTable<S> table = new PapiRadixTable<>(stateArray, strides, valueCounts);
        for (int i = 0; i < count; i++) {
            ((PapiRadixStateHolder<S>) stateArray[i]).papi$attachRadix(table, i);
        }
    }
}
