package com.wispmod.entity;

import com.wispmod.data.WispDefinition;
import com.wispmod.data.WispDefinitionManager;
import com.wispmod.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * A closed box sitting on the ground. Right-clicking it plays an opening effect,
 * removes the box, and spawns the matching {@link WispEntity} with its intro greeting.
 *
 * This mirrors the *concept* described (box in world -> right click -> Wisp emerges with an
 * intro) but is an original implementation, not derived from any other mod's code.
 */
public class WispBoxEntity extends Entity {

    private static final TrackedData<String> WISP_ID =
            DataTracker.registerData(WispBoxEntity.class, TrackedDataHandlerRegistry.STRING);

    private int openingTicks = -1; // -1 = idle, >=0 = playing open animation before spawn
    private static final int OPEN_ANIMATION_TICKS = 20; // 1 second

    public WispBoxEntity(EntityType<? extends WispBoxEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(WISP_ID, "wisp_default");
    }

    public void setWispId(String id) {
        this.dataTracker.set(WISP_ID, id);
    }

    public String getWispId() {
        return this.dataTracker.get(WISP_ID);
    }

    public WispDefinition getDefinition() {
        return WispDefinitionManager.get(getWispId()).orElse(WispDefinitionManager.getFallback());
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (openingTicks >= 0) return ActionResult.CONSUME; // already opening

        if (!this.getWorld().isClient) {
            openingTicks = 0; // begin opening sequence server-side
            ServerWorld sw = (ServerWorld) this.getWorld();
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.NEUTRAL, 0.7f, 1.1f);
            sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.3, this.getZ(),
                    12, 0.2, 0.2, 0.2, 0.02);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (openingTicks < 0) return;

        openingTicks++;
        if (!this.getWorld().isClient && openingTicks >= OPEN_ANIMATION_TICKS) {
            spawnWisp((ServerWorld) this.getWorld());
            this.discard();
        }
    }

    private void spawnWisp(ServerWorld world) {
        WispEntity wisp = new WispEntity(ModEntities.WISP, world);
        wisp.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), 0);
        wisp.setWispId(this.getWispId());
        world.spawnEntity(wisp);
        wisp.sayIntroLine();

        world.playSound(null, wisp.getX(), wisp.getY(), wisp.getZ(),
                SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.NEUTRAL, 1.0f, 1.4f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, wisp.getX(), wisp.getY() + 0.5, wisp.getZ(),
                10, 0.3, 0.3, 0.3, 0.01);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("WispId")) {
            setWispId(nbt.getString("WispId"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("WispId", getWispId());
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 4096.0;
    }
}
