package com.wispmod;

import com.mojang.blaze3d.platform.InputUtil;
import com.wispmod.client.gui.WispCreatorScreen;
import com.wispmod.data.WispDefinitionManager;
import com.wispmod.network.WispPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public class WispModClient implements ClientModInitializer {

    private static KeyBinding openCreatorKey;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WispPayloads.SyncDefinitionsPayload.ID, (payload, context) ->
                context.client().execute(() -> WispDefinitionManager.applyFullSync(payload.toJsonArray())));

        openCreatorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wisp.open_creator",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_G, // default: G — reassignable in Controls options
                "category.wisp"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCreatorKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new WispCreatorScreen());
                }
            }
        });

        WispMod.LOGGER.info("Wisp client initialized. Press G to open the Wisp Creator.");
    }
}
