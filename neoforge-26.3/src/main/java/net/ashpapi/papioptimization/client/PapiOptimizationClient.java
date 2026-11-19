package net.ashpapi.papioptimization.client;

import java.util.Locale;
import net.ashpapi.papioptimization.PapiOptimizationMod;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.command.PapiStatusCommand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
@Mod(value = PapiOptimizationMod.MODID, dist = Dist.CLIENT)
public final class PapiOptimizationClient {

    public PapiOptimizationClient() {
        PapiStatusCommand.registerClientStatusProvider(
                PapiEbeRuntime::getActiveBackend,
                PapiOptimizationClient::formatOcclusionStats
        );
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PapiStatusCommand.registerClient(event.getDispatcher());
    }

    private static String formatOcclusionStats() {
        return String.format(
                Locale.ROOT,
                "ent=%d(%d), be=%d(%d), part=%d(%d)",
                PapiOcclusionEngine.getLastFrameCulledEntities(),
                PapiOcclusionEngine.getTotalCulledEntities(),
                PapiOcclusionEngine.getLastFrameCulledBlockEntities(),
                PapiOcclusionEngine.getTotalCulledBlockEntities(),
                PapiOcclusionEngine.getLastFrameCulledParticles(),
                PapiOcclusionEngine.getTotalCulledParticles()
        );
    }
}
