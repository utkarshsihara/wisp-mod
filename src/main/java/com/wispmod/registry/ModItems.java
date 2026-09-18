package com.wispmod.registry;

import com.wispmod.WispMod;
import com.wispmod.item.WispBoxItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {

    public static Item WISP_BOX;

    private ModItems() {}

    public static void register() {
        RegistryKey<Item> boxKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(WispMod.MOD_ID, "wisp_box"));
        WISP_BOX = Registry.register(Registries.ITEM, boxKey,
                new WispBoxItem(new Item.Settings().registryKey(boxKey).maxCount(16)));

        // The default definition's box shows up in the creative tab; custom ones are obtained
        // via the Wisp Creator GUI's "Give Box" action (see WispCreatorScreen) since there's no
        // fixed set of them to register ahead of time — internalId is stored on the ItemStack.
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(WISP_BOX));
    }
}
