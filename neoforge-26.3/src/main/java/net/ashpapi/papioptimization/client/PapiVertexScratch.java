package net.ashpapi.papioptimization.client;

import org.joml.Vector3f;

public final class PapiVertexScratch {

    public static final ThreadLocal<Vector3f> NORMAL = ThreadLocal.withInitial(Vector3f::new);

    private PapiVertexScratch() {
    }
}
