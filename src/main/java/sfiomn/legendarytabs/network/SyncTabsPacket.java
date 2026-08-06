package sfiomn.legendarytabs.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.data.TabDataLoader;
import sfiomn.legendarytabs.data.TabRegistry;

import java.util.HashMap;
import java.util.Map;

public record SyncTabsPacket(Map<ResourceLocation, String> tabsJson) implements CustomPacketPayload {

    public static final Type<SyncTabsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LegendaryTabs.MOD_ID, "sync_tabs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTabsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.stringUtf8(32767)),
            SyncTabsPacket::tabsJson,
            SyncTabsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncTabsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            LegendaryTabs.LOGGER.info("Received sync tabs packet with {} tab definitions", packet.tabsJson().size());
            TabDataLoader loader = TabDataLoader.getInstance();
            if (loader == null) {
                loader = new TabDataLoader();
            }
            loader.loadFromStrings(packet.tabsJson());
            TabRegistry.getInstance().reloadTabs();
        });
    }
}
