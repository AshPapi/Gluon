package net.ashpapi.papioptimization.server;

public final class PapiAutosaveState {

    private static final ThreadLocal<Boolean> IN_AUTOSAVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PapiAutosaveState() {}

    public static boolean isInAutoSave() {
        return IN_AUTOSAVE.get();
    }

    public static void setInAutoSave(boolean active) {
        IN_AUTOSAVE.set(active);
    }
}
