package net.ashpapi.papioptimization.memory;

public final class PapiRadixTable<S> {
    public final S[] states;
    public final int[] strides;
    public final int[] valueCounts;

    public PapiRadixTable(S[] states, int[] strides, int[] valueCounts) {
        this.states = states;
        this.strides = strides;
        this.valueCounts = valueCounts;
    }
}
