package sfiomn.legendarytabs.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class LegendaryTabsNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(LegendaryTabsNetwork::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(SyncTabsPacket.TYPE, SyncTabsPacket.STREAM_CODEC, SyncTabsPacket::handle);
    }

    public static void syncTabsToAll(SyncTabsPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void syncTabsToPlayer(SyncTabsPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
