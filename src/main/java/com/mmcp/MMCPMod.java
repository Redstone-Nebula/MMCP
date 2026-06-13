package com.mmcp;

import com.mmcp.entity.AIEntity;
import com.mmcp.entity.ModEntities;
import com.mmcp.item.ModItems;
import com.mmcp.mcp.MCPServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCPMod.MODID)
public class MMCPMod {
    public static final String MODID = "mmcp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MMCPMod.class);

    private MCPServer mcpServer;

    public MMCPMod(IEventBus modEventBus) {
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("MMCP AI Mod initializing...");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.AI_ENTITY.get(), AIEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.AI_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.AI_SPAWN_EGG);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        mcpServer = new MCPServer(8932, event.getServer());
        mcpServer.start();
        event.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§b[MMCP]§f MCP Server started on port 8932"), false
        );
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (mcpServer != null) {
            mcpServer.stop();
            LOGGER.info("MCP Server stopped");
        }
    }
}