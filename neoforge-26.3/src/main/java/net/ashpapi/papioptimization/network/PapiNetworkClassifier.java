package net.ashpapi.papioptimization.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public final class PapiNetworkClassifier {

    private static final String GAME_PACKET_PREFIX = "net.minecraft.network.protocol.game.";

    private PapiNetworkClassifier() {}

    public static boolean isImmediateFlushPacket(Packet<?> packet) {
        if (packet == null) {
            return true;
        }
        String name = packet.getClass().getName();
        if (!name.startsWith(GAME_PACKET_PREFIX)) {
            return true;
        }
        if (name.startsWith("net.minecraft.network.protocol.game.Serverbound")) {
            return true;
        }
        return packet instanceof ClientboundBundlePacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundBlockChangedAckPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundDamageEventPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundExplodePacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundOpenScreenPacket
                || packet instanceof ClientboundMountScreenOpenPacket
                || packet instanceof ClientboundOpenBookPacket
                || packet instanceof ClientboundOpenSignEditorPacket
                || packet instanceof ClientboundMerchantOffersPacket
                || packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundSetCursorItemPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket
                || packet instanceof ClientboundSetHeldSlotPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundDisguisedChatPacket
                || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundChunkBatchFinishedPacket
                || packet instanceof ClientboundStartConfigurationPacket;
    }
}
