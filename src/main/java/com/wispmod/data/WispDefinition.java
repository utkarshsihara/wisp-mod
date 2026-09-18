package com.wispmod.data;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single custom Wisp "type" — everything the Wisp Creator GUI lets you configure.
 *
 * IMPORTANT: {@link #internalId} is permanent and never changes once created.
 * {@link #displayName} and everything else can be edited freely at any time;
 * entities already placed in the world only ever store the internalId, and
 * look everything else up live from the current definition. That's what makes
 * renaming/recoloring safe for existing saved worlds.
 */
public class WispDefinition {

    private final String internalId;      // e.g. "wisp_0007" — stable forever
    private String displayName;           // e.g. "Storm"
    private String colorHex;              // e.g. "#008CFF"
    private String boxDisplayName;        // e.g. "Storm Box"
    private String boxColorHex;           // e.g. "#0033AA"

    // Resource paths — these are references only. The mod does not generate,
    // ship, or modify the actual PNG files. If a path doesn't resolve to a
    // real resource, we render a fallback and log a warning instead of crashing.
    private String textureRef;            // e.g. "wisp:textures/verity/custom/storm.png"
    private String boxTextureRef;         // e.g. "wisp:textures/verity/custom/storm_box.png"

    // Ability configuration (all optional / can be disabled)
    private boolean abilityEnabled;
    private String abilityType;           // "NONE", "REGEN_AURA", "SPEED_AURA", "GLOW_AURA", etc.
    private int cooldownTicks;
    private int durationTicks;
    private int amplifier;
    private double range;
    private boolean particlesVisible;

    // Custom intro / idle chat lines shown when the Wisp emerges or is greeted.
    // Kept short and data-driven so you can rewrite them per-Wisp from the GUI.
    private List<String> introLines;
    private List<String> idleLines;

    private boolean deleted; // soft-delete flag — see WispDefinitionManager#delete

    public WispDefinition(String internalId, String displayName, String colorHex,
                           String boxDisplayName, String boxColorHex) {
        this.internalId = internalId;
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.boxDisplayName = boxDisplayName;
        this.boxColorHex = boxColorHex;
        this.textureRef = "";
        this.boxTextureRef = "";
        this.abilityEnabled = false;
        this.abilityType = "NONE";
        this.cooldownTicks = 200;
        this.durationTicks = 100;
        this.amplifier = 0;
        this.range = 4.0;
        this.particlesVisible = true;
        this.introLines = new ArrayList<>(List.of("Hi there! I'm " + displayName + "."));
        this.idleLines = new ArrayList<>(List.of("..."));
        this.deleted = false;
    }

    public static String newInternalId() {
        return "wisp_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public WispDefinition copyAsDuplicate() {
        WispDefinition copy = new WispDefinition(newInternalId(), displayName + " Copy",
                colorHex, boxDisplayName + " Copy", boxColorHex);
        copy.textureRef = this.textureRef;
        copy.boxTextureRef = this.boxTextureRef;
        copy.abilityEnabled = this.abilityEnabled;
        copy.abilityType = this.abilityType;
        copy.cooldownTicks = this.cooldownTicks;
        copy.durationTicks = this.durationTicks;
        copy.amplifier = this.amplifier;
        copy.range = this.range;
        copy.particlesVisible = this.particlesVisible;
        copy.introLines = new ArrayList<>(this.introLines);
        copy.idleLines = new ArrayList<>(this.idleLines);
        return copy;
    }

    // --- getters / setters ---

    public String getInternalId() { return internalId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String v) { this.colorHex = v; }

    public String getBoxDisplayName() { return boxDisplayName; }
    public void setBoxDisplayName(String v) { this.boxDisplayName = v; }

    public String getBoxColorHex() { return boxColorHex; }
    public void setBoxColorHex(String v) { this.boxColorHex = v; }

    public String getTextureRef() { return textureRef; }
    public void setTextureRef(String v) { this.textureRef = v; }

    public String getBoxTextureRef() { return boxTextureRef; }
    public void setBoxTextureRef(String v) { this.boxTextureRef = v; }

    public boolean isAbilityEnabled() { return abilityEnabled; }
    public void setAbilityEnabled(boolean v) { this.abilityEnabled = v; }

    public String getAbilityType() { return abilityType; }
    public void setAbilityType(String v) { this.abilityType = v; }

    public int getCooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int v) { this.cooldownTicks = Math.max(0, v); }

    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int v) { this.durationTicks = Math.max(1, v); }

    public int getAmplifier() { return amplifier; }
    public void setAmplifier(int v) { this.amplifier = Math.max(0, Math.min(v, 4)); }

    public double getRange() { return range; }
    public void setRange(double v) { this.range = Math.max(0.5, Math.min(v, 32.0)); }

    public boolean isParticlesVisible() { return particlesVisible; }
    public void setParticlesVisible(boolean v) { this.particlesVisible = v; }

    public List<String> getIntroLines() { return introLines; }
    public void setIntroLines(List<String> v) { this.introLines = new ArrayList<>(v); }

    public List<String> getIdleLines() { return idleLines; }
    public void setIdleLines(List<String> v) { this.idleLines = new ArrayList<>(v); }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }

    // --- validation (server calls this before ever accepting a create/edit request) ---

    public static boolean isValidHexColor(String hex) {
        if (hex == null) return false;
        return hex.matches("^#[0-9a-fA-F]{6}$");
    }

    public static boolean isValidDisplayName(String name) {
        return name != null && !name.isBlank() && name.length() <= 32
                && !name.contains("\n") && !name.contains("\t");
    }

    /** Rejects path traversal / absolute paths / anything outside the expected asset folder. */
    public static boolean isValidTextureRef(String ref) {
        if (ref == null || ref.isEmpty()) return true; // empty = "use fallback"
        if (ref.contains("..") || ref.startsWith("/") || ref.contains("\\")) return false;
        return ref.matches("^[a-z0-9_./-]+$") && ref.length() <= 256;
    }

    // --- serialization ---

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("internalId", internalId);
        o.addProperty("displayName", displayName);
        o.addProperty("colorHex", colorHex);
        o.addProperty("boxDisplayName", boxDisplayName);
        o.addProperty("boxColorHex", boxColorHex);
        o.addProperty("textureRef", textureRef);
        o.addProperty("boxTextureRef", boxTextureRef);
        o.addProperty("abilityEnabled", abilityEnabled);
        o.addProperty("abilityType", abilityType);
        o.addProperty("cooldownTicks", cooldownTicks);
        o.addProperty("durationTicks", durationTicks);
        o.addProperty("amplifier", amplifier);
        o.addProperty("range", range);
        o.addProperty("particlesVisible", particlesVisible);
        o.addProperty("deleted", deleted);
        com.google.gson.JsonArray intro = new com.google.gson.JsonArray();
        introLines.forEach(intro::add);
        o.add("introLines", intro);
        com.google.gson.JsonArray idle = new com.google.gson.JsonArray();
        idleLines.forEach(idle::add);
        o.add("idleLines", idle);
        return o;
    }

    public static WispDefinition fromJson(JsonObject o) {
        WispDefinition d = new WispDefinition(
                o.get("internalId").getAsString(),
                o.get("displayName").getAsString(),
                o.get("colorHex").getAsString(),
                o.get("boxDisplayName").getAsString(),
                o.get("boxColorHex").getAsString()
        );
        d.textureRef = o.has("textureRef") ? o.get("textureRef").getAsString() : "";
        d.boxTextureRef = o.has("boxTextureRef") ? o.get("boxTextureRef").getAsString() : "";
        d.abilityEnabled = o.has("abilityEnabled") && o.get("abilityEnabled").getAsBoolean();
        d.abilityType = o.has("abilityType") ? o.get("abilityType").getAsString() : "NONE";
        d.cooldownTicks = o.has("cooldownTicks") ? o.get("cooldownTicks").getAsInt() : 200;
        d.durationTicks = o.has("durationTicks") ? o.get("durationTicks").getAsInt() : 100;
        d.amplifier = o.has("amplifier") ? o.get("amplifier").getAsInt() : 0;
        d.range = o.has("range") ? o.get("range").getAsDouble() : 4.0;
        d.particlesVisible = !o.has("particlesVisible") || o.get("particlesVisible").getAsBoolean();
        d.deleted = o.has("deleted") && o.get("deleted").getAsBoolean();
        if (o.has("introLines")) {
            List<String> lines = new ArrayList<>();
            o.getAsJsonArray("introLines").forEach(e -> lines.add(e.getAsString()));
            d.introLines = lines;
        }
        if (o.has("idleLines")) {
            List<String> lines = new ArrayList<>();
            o.getAsJsonArray("idleLines").forEach(e -> lines.add(e.getAsString()));
            d.idleLines = lines;
        }
        return d;
    }
}
