package com.wispmod.registry;

import com.wispmod.WispMod;
import com.wispmod.entity.WispBoxEntity;
import com.wispmod.entity.WispEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {

    public static EntityType<WispEntity> WISP;
    public static EntityType<WispBoxEntity> WISP_BOX_ENTITY;

    private ModEntities() {}

    public static void register() {
        RegistryKey<EntityType<?>> wispKey =
                RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(WispMod.MOD_ID, "wisp"));
        WISP = Registry.register(Registries.ENTITY_TYPE, wispKey,
                EntityType.Builder.create(WispEntity::new, SpawnGroup.CREATURE)
                        .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                        .maxTrackingRange(48)
                        .trackingTickInterval(2)
                        .build(wispKey));

        RegistryKey<EntityType<?>> boxKey =
                RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(WispMod.MOD_ID, "wisp_box"));
        WISP_BOX_ENTITY = Registry.register(Registries.ENTITY_TYPE, boxKey,
                EntityType.Builder.create(WispBoxEntity::new, SpawnGroup.MISC)
                        .dimensions(EntityDimensions.fixed(0.75f, 0.75f))
                        .maxTrackingRange(32)
                        .trackingTickInterval(10)
                        .build(boxKey));
    }
}
