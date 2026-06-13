package com.mmcp.item;

import com.mmcp.MMCPMod;
import com.mmcp.entity.ModEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MMCPMod.MODID);

    public static final Supplier<SpawnEggItem> AI_SPAWN_EGG = ITEMS.register("ai_spawn_egg",
            () -> new SpawnEggItem(ModEntities.AI_ENTITY.get(), 0x44AA44, 0x335533,
                    new Item.Properties().stacksTo(64))
    );
}