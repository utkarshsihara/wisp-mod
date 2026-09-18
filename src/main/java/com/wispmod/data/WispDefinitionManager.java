package com.wispmod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wispmod.WispMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of truth for Wisp definitions, held only on the logical server
 * (including the integrated server in singleplayer). Clients never write here directly —
 * they send a request over the network, the server validates + applies it here, then
 * broadcasts the updated definition back out. See network/ for the C2S/S2C packets.
 */
public final class WispDefinitionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, WispDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static Path storagePath;

    private WispDefinitionManager() {}

    // --- lifecycle ---

    public static void load(Path configDir) {
        storagePath = configDir.resolve("wisp_definitions.json");
        DEFINITIONS.clear();
        if (!Files.exists(storagePath)) {
            seedDefault();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
            for (var el : arr) {
                WispDefinition def = WispDefinition.fromJson(el.getAsJsonObject());
                DEFINITIONS.put(def.getInternalId(), def);
            }
            WispMod.LOGGER.info("Loaded {} Wisp definitions from {}", DEFINITIONS.size(), storagePath);
        } catch (IOException | RuntimeException e) {
            WispMod.LOGGER.error("Failed to load Wisp definitions, starting with defaults only", e);
            seedDefault();
        }
    }

    public static void save() {
        if (storagePath == null) return;
        JsonArray arr = new JsonArray();
        DEFINITIONS.values().forEach(d -> arr.add(d.toJson()));
        try {
            Files.createDirectories(storagePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)) {
                GSON.toJson(arr, writer);
            }
        } catch (IOException e) {
            WispMod.LOGGER.error("Failed to save Wisp definitions", e);
        }
    }

    /** The one built-in default, kept separate from any user-created data. */
    private static void seedDefault() {
        WispDefinition base = new WispDefinition(
                "wisp_default", "Wisp", "#66CCFF", "Wisp Box", "#336699");
        DEFINITIONS.put(base.getInternalId(), base);
    }

    // --- read access (safe for client + server; client's copy comes from S2C sync) ---

    public static Optional<WispDefinition> get(String internalId) {
        return Optional.ofNullable(DEFINITIONS.get(internalId));
    }

    public static Collection<WispDefinition> getAll() {
        return DEFINITIONS.values();
    }

    /** Definition to fall back to if a referenced internalId no longer resolves (should be rare — see delete()). */
    public static WispDefinition getFallback() {
        return DEFINITIONS.computeIfAbsent("wisp_default",
                id -> new WispDefinition(id, "Wisp", "#66CCFF", "Wisp Box", "#336699"));
    }

    // --- server-side mutation, called only after a permission check upstream ---

    public static ValidationResult create(WispDefinition def) {
        ValidationResult vr = validate(def);
        if (!vr.ok()) return vr;
        DEFINITIONS.put(def.getInternalId(), def);
        save();
        return ValidationResult.ok();
    }

    public static ValidationResult edit(WispDefinition def) {
        if (!DEFINITIONS.containsKey(def.getInternalId())) {
            return ValidationResult.fail("Unknown internalId — cannot edit a Wisp that doesn't exist.");
        }
        ValidationResult vr = validate(def);
        if (!vr.ok()) return vr;
        DEFINITIONS.put(def.getInternalId(), def);
        save();
        return ValidationResult.ok();
    }

    /**
     * Soft-delete only: existing placed entities keep referencing this internalId and will
     * render using getFallback() rather than becoming invalid or crashing. The definition
     * disappears from the GUI list but its id is never reused.
     */
    public static ValidationResult delete(String internalId) {
        if (internalId.equals("wisp_default")) {
            return ValidationResult.fail("The default Wisp cannot be deleted.");
        }
        WispDefinition def = DEFINITIONS.get(internalId);
        if (def == null) return ValidationResult.fail("Unknown internalId.");
        def.setDeleted(true);
        save();
        return ValidationResult.ok();
    }

    private static ValidationResult validate(WispDefinition def) {
        if (def.getInternalId() == null || def.getInternalId().isBlank()) {
            return ValidationResult.fail("Missing internal id.");
        }
        if (!WispDefinition.isValidDisplayName(def.getDisplayName())) {
            return ValidationResult.fail("Invalid display name (1-32 chars, no newlines).");
        }
        if (!WispDefinition.isValidDisplayName(def.getBoxDisplayName())) {
            return ValidationResult.fail("Invalid box display name.");
        }
        if (!WispDefinition.isValidHexColor(def.getColorHex())) {
            return ValidationResult.fail("Invalid color hex — expected #RRGGBB.");
        }
        if (!WispDefinition.isValidHexColor(def.getBoxColorHex())) {
            return ValidationResult.fail("Invalid box color hex — expected #RRGGBB.");
        }
        if (!WispDefinition.isValidTextureRef(def.getTextureRef())) {
            return ValidationResult.fail("Invalid texture path.");
        }
        if (!WispDefinition.isValidTextureRef(def.getBoxTextureRef())) {
            return ValidationResult.fail("Invalid box texture path.");
        }
        return ValidationResult.ok();
    }

    // --- client-side sync application (no validation needed — trusts the server) ---

    public static void applyFullSync(JsonArray arr) {
        DEFINITIONS.clear();
        for (var el : arr) {
            WispDefinition def = WispDefinition.fromJson(el.getAsJsonObject());
            DEFINITIONS.put(def.getInternalId(), def);
        }
    }

    public static JsonArray toSyncPayload() {
        JsonArray arr = new JsonArray();
        DEFINITIONS.values().forEach(d -> arr.add(d.toJson()));
        return arr;
    }

    public record ValidationResult(boolean ok, String error) {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
    }
}
