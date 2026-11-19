package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FilePackResources.class, priority = 1500)
public abstract class FilePackResourcesMixin {

    @Unique
    private volatile @Nullable Set<String> papi$clientNamespaces;

    @Unique
    private volatile @Nullable Set<String> papi$serverNamespaces;

    @WrapMethod(method = "getNamespaces(Lnet/minecraft/server/packs/PackType;)Ljava/util/Set;")
    private Set<String> papi$cacheZipNamespaces(PackType type, Operation<Set<String>> original) {
        if (type == PackType.CLIENT_RESOURCES) {
            Set<String> cached = this.papi$clientNamespaces;
            if (cached != null) {
                return cached;
            }
            Set<String> computed = original.call(type);
            if (computed != null) {
                computed = Set.copyOf(computed);
                this.papi$clientNamespaces = computed;
            }
            return computed;
        }
        if (type == PackType.SERVER_DATA) {
            Set<String> cached = this.papi$serverNamespaces;
            if (cached != null) {
                return cached;
            }
            Set<String> computed = original.call(type);
            if (computed != null) {
                computed = Set.copyOf(computed);
                this.papi$serverNamespaces = computed;
            }
            return computed;
        }
        return original.call(type);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void papi$clearNamespaceCacheOnClose(CallbackInfo ci) {
        this.papi$clientNamespaces = null;
        this.papi$serverNamespaces = null;
    }
}
