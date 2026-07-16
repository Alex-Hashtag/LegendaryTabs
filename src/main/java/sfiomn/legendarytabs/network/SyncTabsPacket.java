package sfiomn.legendarytabs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import sfiomn.legendarytabs.LegendaryTabs;
import sfiomn.legendarytabs.data.TabDataLoader;
import sfiomn.legendarytabs.data.TabRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncTabsPacket {
    private final Map<ResourceLocation, String> tabsJson;

    public SyncTabsPacket(Map<ResourceLocation, String> tabsJson) {
        this.tabsJson = tabsJson;
    }

    public Map<ResourceLocation, String> getTabsJson() {
        return tabsJson;
    }

    public static void encode(SyncTabsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.tabsJson.size());
        for (Map.Entry<ResourceLocation, String> entry : msg.tabsJson.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue(), 32767);
        }
    }

    public static SyncTabsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<ResourceLocation, String> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation key = buf.readResourceLocation();
            String value = buf.readUtf(32767);
            map.put(key, value);
        }
        return new SyncTabsPacket(map);
    }

    public static void handle(SyncTabsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LegendaryTabs.LOGGER.info("Received sync tabs packet with {} tab definitions", msg.tabsJson.size());
            TabDataLoader loader = TabDataLoader.getInstance();
            if (loader == null) {
                loader = new TabDataLoader();
            }
            loader.loadFromStrings(msg.tabsJson);
            TabRegistry.getInstance().reloadTabs();
        });
        ctx.get().setPacketHandled(true);
    }
}
