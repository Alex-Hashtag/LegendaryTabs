package sfiomn.legendarytabs.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import sfiomn.legendarytabs.LegendaryTabs;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config
{
	public static final ForgeConfigSpec CLIENT_SPEC;
	public static final Client CLIENT;

	static
	{
		final Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
		CLIENT_SPEC = client.getRight();
		CLIENT = client.getLeft();
	}

	public static void register(FMLJavaModLoadingContext context)
	{
		Path configPath = LegendaryTabs.modConfigPath;

		try {
			Files.createDirectory(configPath);
		} catch (FileAlreadyExistsException ignored) {
		} catch (IOException e) {
			LegendaryTabs.LOGGER.error("Failed to create Legendary Tabs config directory " + configPath);
			e.printStackTrace();
		}


		context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, LegendaryTabs.MOD_ID + "/" + LegendaryTabs.MOD_ID +"-client.toml");
	}

	public static class Client
	{
		public final ForgeConfigSpec.BooleanValue verboseLoggingEnabled;

		public final ForgeConfigSpec.IntValue tabsMenuOffsetX;
		public final ForgeConfigSpec.IntValue tabsMenuOffsetY;
		public final ForgeConfigSpec.BooleanValue includeOpenedScreenTab;
		public final ForgeConfigSpec.BooleanValue inventoryTabEnabled;

		Client(ForgeConfigSpec.Builder builder)
		{
			builder.push("general").comment(" General mod settings");
			verboseLoggingEnabled = builder
					.comment(" If enabled, logs detailed info/debug messages about tab loading, sizing, and input simulation.",
							" Warnings and errors are always logged regardless of this setting. Leave disabled unless troubleshooting.")
					.define("Verbose Logging Enabled", false);
			builder.pop();

			builder.push("tabs-menu").comment(
					" Configuration about the tabs menu overlaid on top of screens",
					" Most integration tabs (Backpacked, Travelers Backpack, Diet, FTB Quests/Teams,",
					" Xaero's/Journey Map, Reskillable, Pufferfish's Skills, Passive Skill Tree, ...) are",
					" now defined entirely by datapacks under data/legendarytabs/tabs/*.json - toggle a",
					" tab there via its \"enabled\" field instead of here. The Inventory tab is the one",
					" remaining built-in Java tab, so it's still the only per-tab toggle left in this config.");
			tabsMenuOffsetX = builder
					.comment(" The X and Y offset of the tabs menu. Set both to 0 for no offset.", " By default, will be rendered above minecraft menus. Set it to 10000 to disable it completely.")
					.defineInRange("Tabs Menu Display X Offset", 2, -10000, 10000);
			tabsMenuOffsetY = builder
					.defineInRange("Tabs Menu Display Y Offset", 0, -10000, 10000);
			includeOpenedScreenTab = builder
					.comment(" If enabled, show current tab opened in the tabs menu.")
					.define("Include Opened Screen Tab", true);
			inventoryTabEnabled = builder
					.comment(" If enabled, show the inventory button in the tabs menu.")
					.define("Inventory Tab Enabled ", true);
			builder.pop();
		}
	}

	public static class Server
	{
		Server(ForgeConfigSpec.Builder builder)
		{

		}
	}

	public static class Baked
	{
		public static boolean verboseLoggingEnabled;

		// Tabs Menu
		public static boolean includeOpenedScreenTab;

		public static int tabsMenuOffsetX;
		public static int tabsMenuOffsetY;

		public static boolean inventoryTabEnabled;

		public static void bakeClient()
		{
			try
			{
				verboseLoggingEnabled = CLIENT.verboseLoggingEnabled.get();
				Configurator.setLevel(LegendaryTabs.LOGGER.getName(), verboseLoggingEnabled ? Level.DEBUG : Level.WARN);

				LegendaryTabs.LOGGER.debug("Load Client configuration from file");

				includeOpenedScreenTab = CLIENT.includeOpenedScreenTab.get();

				tabsMenuOffsetX = CLIENT.tabsMenuOffsetX.get();
				tabsMenuOffsetY = CLIENT.tabsMenuOffsetY.get();

				inventoryTabEnabled = CLIENT.inventoryTabEnabled.get();
			}
			catch (Exception e)
			{
				LegendaryTabs.LOGGER.warn("An exception was caused trying to load the client config for Legendary Survival Overhaul.");
				e.printStackTrace();
			}
		}
	}
}
