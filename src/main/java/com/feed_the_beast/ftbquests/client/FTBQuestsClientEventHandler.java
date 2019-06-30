package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.events.SidebarButtonCreatedEvent;
import com.feed_the_beast.ftblib.events.client.CustomClickEvent;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.block.BlockDetector;
import com.feed_the_beast.ftbquests.events.ClearFileCacheEvent;
import com.feed_the_beast.ftbquests.item.FTBQuestsItems;
import com.feed_the_beast.ftbquests.item.ItemLootCrate;
import com.feed_the_beast.ftbquests.net.MessageSubmitTask;
import com.feed_the_beast.ftbquests.quest.loot.LootCrate;
import com.feed_the_beast.ftbquests.quest.task.ObservationTask;
import com.feed_the_beast.ftbquests.tile.TileProgressScreenCore;
import com.feed_the_beast.ftbquests.tile.TileTaskScreenCore;
import com.feed_the_beast.ftbquests.util.RayMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

/**
 * @author LatvianModder
 */
@Mod.EventBusSubscriber(modid = FTBQuests.MOD_ID, value = Side.CLIENT)
public class FTBQuestsClientEventHandler
{
	private static final ResourceLocation QUESTS_BUTTON = new ResourceLocation(FTBQuests.MOD_ID, "quests");

	public static TextureAtlasSprite inputBlockSprite;
	private static List<ObservationTask> observationTasks = null;

	private static void addModel(Item item, String variant)
	{
		ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), variant));
	}

	@SubscribeEvent
	public static void registerModels(ModelRegistryEvent event)
	{
		addModel(FTBQuestsItems.SCREEN, "facing=north");
		addModel(FTBQuestsItems.PROGRESS_DETECTOR, "normal");

		for (BlockDetector.Variant variant : BlockDetector.Variant.VALUES)
		{
			ModelLoader.setCustomModelResourceLocation(FTBQuestsItems.DETECTOR, variant.ordinal(), new ModelResourceLocation(FTBQuestsItems.DETECTOR.getRegistryName(), "variant=" + variant.getName()));
		}

		addModel(FTBQuestsItems.PROGRESS_SCREEN, "facing=north");
		addModel(FTBQuestsItems.CHEST, "facing=north");
		addModel(FTBQuestsItems.LOOT_CRATE_STORAGE, "normal");
		addModel(FTBQuestsItems.LOOT_CRATE_OPENER, "normal");
		addModel(FTBQuestsItems.BARRIER, "normal");

		addModel(FTBQuestsItems.BOOK, "inventory");
		addModel(FTBQuestsItems.LOOTCRATE, "inventory");

		ClientRegistry.bindTileEntitySpecialRenderer(TileTaskScreenCore.class, new RenderTaskScreen());
		ClientRegistry.bindTileEntitySpecialRenderer(TileProgressScreenCore.class, new RenderProgressScreen());
	}

	@SubscribeEvent
	public static void registerItemColors(ColorHandlerEvent.Item event)
	{
		event.getItemColors().registerItemColorHandler((stack, tintIndex) -> {
			LootCrate crate = ItemLootCrate.getCrate(null, stack);
			return crate == null ? 0xFFFFFFFF : (0xFF000000 | crate.color.rgb());
		}, FTBQuestsItems.LOOTCRATE);
	}

	@SubscribeEvent
	public static void onSidebarButtonCreated(SidebarButtonCreatedEvent event)
	{
		if (event.getButton().id.equals(QUESTS_BUTTON))
		{
			event.getButton().setCustomTextHandler(() ->
			{
				if (ClientQuestFile.exists())
				{
					if (!ClientQuestFile.existsWithTeam())
					{
						return "[!]";
					}

					int r = ClientQuestFile.INSTANCE.getUnclaimedRewards(Minecraft.getMinecraft().player.getUniqueID(), ClientQuestFile.INSTANCE.self, true);

					if (r > 0)
					{
						return Integer.toString(r);
					}
				}

				return "";
			});

			event.getButton().setTooltipHandler(list -> {
				if (ClientQuestFile.exists() && !ClientQuestFile.existsWithTeam())
				{
					list.add(TextFormatting.GRAY + I18n.format("sidebar_button.ftbquests.quests.no_team"));
				}
			});
		}
	}

	@SubscribeEvent
	public static void onFileCacheClear(ClearFileCacheEvent event)
	{
		observationTasks = null;
	}

	@SubscribeEvent
	public static void onKeyEvent(InputEvent.KeyInputEvent event)
	{
		if (FTBQuestsClient.KEY_QUESTS.isPressed())
		{
			ClientQuestFile.INSTANCE.openQuestGui(Minecraft.getMinecraft().player);
		}
	}

	@SubscribeEvent
	public static void onCustomClick(CustomClickEvent event)
	{
		if (event.getID().getNamespace().equals(FTBQuests.MOD_ID) && "open_gui".equals(event.getID().getPath()))
		{
			ClientQuestFile.INSTANCE.openQuestGui(Minecraft.getMinecraft().player);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onTextureStitchPre(TextureStitchEvent.Pre event)
	{
		inputBlockSprite = event.getMap().registerSprite(new ResourceLocation(FTBQuests.MOD_ID, "blocks/screen_front_input"));
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event)
	{
		Minecraft mc = Minecraft.getMinecraft();

		if (event.phase == TickEvent.Phase.START && mc.world != null && ClientQuestFile.existsWithTeam())
		{
			if (observationTasks == null)
			{
				observationTasks = ClientQuestFile.INSTANCE.collect(ObservationTask.class);
			}

			if (observationTasks.isEmpty())
			{
				return;
			}

			RayMatcher.Data data = RayMatcher.Data.get(mc.world, mc.objectMouseOver);

			for (ObservationTask task : observationTasks)
			{
				if (task.matcher.matches(data) && !task.isComplete(ClientQuestFile.INSTANCE.self) && task.quest.canStartTasks(ClientQuestFile.INSTANCE.self))
				{
					new MessageSubmitTask(task.id).sendToServer();
				}
			}
		}
	}
}