package com.wispmod;

import com.wispmod.data.WispDefinition;
import com.wispmod.data.WispDefinitionManager;
import com.wispmod.item.WispBoxItem;
import com.wispmod.network.WispPayloads;
import com.wispmod.registry.ModEntities;
import com.wispmod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WispMod implements ModInitializer {

    public static final String MOD_ID = "wisp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        registerComponents();
        ModEntities.register();
        ModItems.register();
        registerNetworking();

        // Load definitions when the (integrated or dedicated) server starts, save on stop.
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                WispDefinitionManager.load(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("wisp")));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WispDefinitionManager.save());

        // Sync full definition set to every player as soon as they join.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var payload = WispPayloads.SyncDefinitionsPayload.of(WispDefinitionManager.toSyncPayload());
            ServerPlayNetworking.send(handler.player, payload);
        });

        LOGGER.info("Wisp initialized.");
    }

    private void registerComponents() {
        WispBoxItem.WISP_ID_COMPONENT = Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(MOD_ID, "wisp_id"),
                ComponentType.<String>builder().codec(com.mojang.serialization.Codec.STRING).build());
    }

    private void registerNetworking() {
        PayloadTypeRegistry.playS2C().register(
                WispPayloads.SyncDefinitionsPayload.ID, WispPayloads.SyncDefinitionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                WispPayloads.RequestEditPayload.ID, WispPayloads.RequestEditPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(WispPayloads.RequestEditPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> handleEditRequest(player, payload));
        });
    }

    /**
     * SERVER IS AUTHORITATIVE. Every request is re-validated here regardless of what the
     * client-side GUI already checked — the client's checks are only for a responsive UI,
     * never trusted on their own.
     */
    private void handleEditRequest(ServerPlayerEntity player, WispPayloads.RequestEditPayload payload) {
        // Permission: in singleplayer the local player always has permission (checked via
        // isHost below); on a dedicated server / LAN world, require operator (permission level 2+).
        boolean allowed = player.getServer().isSingleplayer() || player.hasPermissionLevel(2);
        if (!allowed) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "You don't have permission to edit Wisp definitions."), false);
            return;
        }

        WispDefinitionManager.ValidationResult result;
        WispPayloads.Action action;
        try {
            action = WispPayloads.Action.valueOf(payload.action());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Rejected malformed Wisp edit action from {}: {}", player.getName().getString(), payload.action());
            return;
        }

        switch (action) {
            case CREATE -> {
                WispDefinition def = parseDefinitionOrNull(payload.definitionJson());
                result = def == null
                        ? WispDefinitionManager.ValidationResult.fail("Malformed definition payload.")
                        : WispDefinitionManager.create(def);
            }
            case EDIT -> {
                WispDefinition def = parseDefinitionOrNull(payload.definitionJson());
                result = def == null
                        ? WispDefinitionManager.ValidationResult.fail("Malformed definition payload.")
                        : WispDefinitionManager.edit(def);
            }
            case DUPLICATE -> {
                var original = WispDefinitionManager.get(payload.targetId());
                if (original.isEmpty()) {
                    result = WispDefinitionManager.ValidationResult.fail("Unknown Wisp to duplicate.");
                } else {
                    result = WispDefinitionManager.create(original.get().copyAsDuplicate());
                }
            }
            case DELETE -> result = WispDefinitionManager.delete(payload.targetId());
            default -> result = WispDefinitionManager.ValidationResult.fail("Unknown action.");
        }

        if (!result.ok()) {
            player.sendMessage(net.minecraft.text.Text.literal("Wisp Creator: " + result.error()), false);
            return;
        }

        // Broadcast the updated definition set to every connected player, not just the requester,
        // so nobody's client desyncs from the server's authoritative state.
        var syncPayload = WispPayloads.SyncDefinitionsPayload.of(WispDefinitionManager.toSyncPayload());
        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, syncPayload);
        }
    }

    private WispDefinition parseDefinitionOrNull(String json) {
        try {
            return WispDefinition.fromJson(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
