package com.wispmod.item;

import com.wispmod.data.WispDefinition;
import com.wispmod.data.WispDefinitionManager;
import com.wispmod.entity.WispBoxEntity;
import com.wispmod.registry.ModEntities;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * A stack of this item represents an unopened box for ONE specific custom Wisp — which one
 * is stored via a custom data component (the modern 1.21.x replacement for tagging items with
 * raw NBT). Right-clicking a block with it places a {@link WispBoxEntity} on top of that block.
 */
public class WispBoxItem extends Item {

    // Data component key for "which Wisp this box contains". Registered in WispMod#registerComponents.
    public static net.minecraft.component.ComponentType<String> WISP_ID_COMPONENT;

    public WispBoxItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createFilledStack(WispDefinition def) {
        ItemStack stack = new ItemStack(com.wispmod.registry.ModItems.WISP_BOX);
        stack.set(WISP_ID_COMPONENT, def.getInternalId());
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(def.getBoxDisplayName()));
        return stack;
    }

    public static String getWispId(ItemStack stack) {
        String id = stack.get(WISP_ID_COMPONENT);
        return id != null ? id : "wisp_default";
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        BlockPos placePos = context.getBlockPos().offset(context.getSide());
        WispBoxEntity box = new WispBoxEntity(ModEntities.WISP_BOX_ENTITY, world);
        box.refreshPositionAndAngles(
                placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5,
                0, 0);

        String wispId = getWispId(context.getStack());
        WispDefinition def = WispDefinitionManager.get(wispId).orElse(WispDefinitionManager.getFallback());
        box.setWispId(def.getInternalId());

        world.spawnEntity(box);

        if (context.getPlayer() != null && !context.getPlayer().getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public Text getName(ItemStack stack) {
        String wispId = getWispId(stack);
        return WispDefinitionManager.get(wispId)
                .map(def -> Text.literal(def.getBoxDisplayName()))
                .orElse(super.getName(stack));
    }
}
