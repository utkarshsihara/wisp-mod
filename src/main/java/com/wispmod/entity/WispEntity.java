package com.wispmod.entity;

import com.wispmod.data.WispDefinition;
import com.wispmod.data.WispDefinitionManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The reusable companion entity. This one class represents EVERY custom Wisp type —
 * it never stores name/color/texture/ability data directly, only its {@code wispId},
 * and looks up the current {@link WispDefinition} live from {@link WispDefinitionManager}
 * every time it needs to render, speak, or apply its ability. That's what lets you
 * rename or recolor a Wisp from the GUI without breaking anything already placed in a world.
 */
public class WispEntity extends PathAwareEntity {

    private static final TrackedData<String> WISP_ID =
            DataTracker.registerData(WispEntity.class, TrackedDataHandlerRegistry.STRING);

    private int abilityCooldown = 0;
    private int idleChatCooldown = 0;
    private float hoverOffset = 0f;

    public WispEntity(EntityType<? extends WispEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true); // floats, like the original concept — not a normal walking mob
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(WISP_ID, "wisp_default");
    }

    public void setWispId(String id) {
        this.dataTracker.set(WISP_ID, id);
        this.setCustomName(Text.literal(getDefinition().getDisplayName()));
        this.setCustomNameVisible(true);
    }

    public String getWispId() {
        return this.dataTracker.get(WISP_ID);
    }

    /** Always resolves live — never cache this across ticks. */
    public WispDefinition getDefinition() {
        return WispDefinitionManager.get(getWispId()).orElse(WispDefinitionManager.getFallback());
    }

    public void sayIntroLine() {
        var lines = getDefinition().getIntroLines();
        if (lines.isEmpty()) return;
        String line = lines.get(this.getRandom().nextInt(lines.size()));
        broadcastChat(line);
    }

    private void sayIdleLine() {
        var lines = getDefinition().getIdleLines();
        if (lines.isEmpty()) return;
        String line = lines.get(this.getRandom().nextInt(lines.size()));
        broadcastChat(line);
    }

    private void broadcastChat(String line) {
        if (this.getWorld().isClient) return;
        Text name = Text.literal(getDefinition().getDisplayName());
        this.getWorld().getPlayers().stream()
                .filter(p -> p.squaredDistanceTo(this) < 64 * 64)
                .forEach(p -> p.sendMessage(Text.literal("<").append(name).append("> " + line), false));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (player.isSneaking()) {
            // Pickup: remove the Wisp and hand back a box item carrying the same internalId,
            // so nothing about the definition is lost.
            this.discard();
            player.sendMessage(Text.literal(getDefinition().getDisplayName() + " goes back into its box."), true);
            // NOTE: actual ItemStack creation wired in WispBoxItem.createFilledStack(...)
            // — call site intentionally left for you to hook up drop-vs-inventory behavior
            // depending on how you want capture to feel.
            return ActionResult.CONSUME;
        }

        sayIdleLine();
        return ActionResult.CONSUME;
    }

    @Override
    public void tick() {
        super.tick();
        hoverOffset += 0.05f;
        if (!this.getWorld().isClient) {
            if (idleChatCooldown > 0) idleChatCooldown--;
            tickAbility();
        }
    }

    private void tickAbility() {
        WispDefinition def = getDefinition();
        if (!def.isAbilityEnabled() || "NONE".equals(def.getAbilityType())) return;
        if (abilityCooldown > 0) { abilityCooldown--; return; }

        var nearby = this.getWorld().getEntitiesByClass(PlayerEntity.class,
                this.getBoundingBox().expand(def.getRange()), p -> true);
        if (nearby.isEmpty()) return;

        switch (def.getAbilityType()) {
            case "REGEN_AURA" -> nearby.forEach(p -> p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.REGENERATION, def.getDurationTicks(), def.getAmplifier())));
            case "SPEED_AURA" -> nearby.forEach(p -> p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SPEED, def.getDurationTicks(), def.getAmplifier())));
            case "GLOW_AURA" -> nearby.forEach(p -> p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.GLOWING, def.getDurationTicks(), 0)));
            default -> { /* unknown/custom ability types are safely ignored */ }
        }

        if (def.isParticlesVisible() && this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    this.getX(), this.getY() + 0.5, this.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        }

        abilityCooldown = def.getCooldownTicks();
    }

    public float getHoverOffset() { return hoverOffset; }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("WispId", getWispId());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("WispId")) {
            // Set the raw tracked value directly (avoid re-sending a custom name update on load)
            this.dataTracker.set(WISP_ID, nbt.getString("WispId"));
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false; // persistent companion — never vanishes on its own
    }
}
