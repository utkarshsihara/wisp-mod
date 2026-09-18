package com.wispmod.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.wispmod.WispMod;
import com.wispmod.data.WispDefinition;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * All network payloads for the Wisp Creator system.
 *
 * S2C:  SyncDefinitionsPayload   — full definition list, sent on join and after any accepted edit.
 * C2S:  RequestEditPayload       — create/edit/duplicate/delete requests from the GUI.
 *
 * NOTE on 1.21.x networking API: payloads are plain records implementing CustomPayload with a
 * PacketCodec, registered via PayloadTypeRegistry, and sent/received via ServerPlayNetworking /
 * ClientPlayNetworking. Wire this up in WispMod#registerNetworking and WispModClient#registerNetworking.
 */
public final class WispPayloads {

    public static final Identifier SYNC_ID = Identifier.of(WispMod.MOD_ID, "sync_definitions");
    public static final Identifier REQUEST_ID = Identifier.of(WispMod.MOD_ID, "request_edit");

    private WispPayloads() {}

    // ---- S2C: full definition sync ----
    public record SyncDefinitionsPayload(String jsonArray) implements CustomPayload {
        public static final CustomPayload.Id<SyncDefinitionsPayload> ID = new CustomPayload.Id<>(SYNC_ID);
        public static final PacketCodec<RegistryByteBuf, SyncDefinitionsPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeString(value.jsonArray(), 262144),
                        buf -> new SyncDefinitionsPayload(buf.readString(262144))
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

        public static SyncDefinitionsPayload of(JsonArray arr) {
            return new SyncDefinitionsPayload(arr.toString());
        }

        public JsonArray toJsonArray() {
            return JsonParser.parseString(jsonArray).getAsJsonArray();
        }
    }

    // ---- C2S: edit request ----
    public enum Action { CREATE, EDIT, DUPLICATE, DELETE }

    public record RequestEditPayload(String action, String definitionJson, String targetId)
            implements CustomPayload {
        public static final CustomPayload.Id<RequestEditPayload> ID = new CustomPayload.Id<>(REQUEST_ID);
        public static final PacketCodec<RegistryByteBuf, RequestEditPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeString(value.action(), 32);
                            buf.writeString(value.definitionJson() == null ? "" : value.definitionJson(), 65536);
                            buf.writeString(value.targetId() == null ? "" : value.targetId(), 64);
                        },
                        buf -> new RequestEditPayload(buf.readString(32), buf.readString(65536), buf.readString(64))
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

        public static RequestEditPayload createOrEdit(Action action, WispDefinition def) {
            return new RequestEditPayload(action.name(), def.toJson().toString(), def.getInternalId());
        }

        public static RequestEditPayload delete(String internalId) {
            return new RequestEditPayload(Action.DELETE.name(), "", internalId);
        }
    }
}
