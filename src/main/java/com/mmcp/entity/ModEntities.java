package com.mmcp.entity;

import com.mmcp.MMCPMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MMCPMod.MODID);

    public static final Supplier<EntityType<AIEntity>> AI_ENTITY = ENTITIES.register("ai_entity",
            () -> EntityType.Builder.of(AIEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath(MMCPMod.MODID, "ai_entity").toString())
    );
}