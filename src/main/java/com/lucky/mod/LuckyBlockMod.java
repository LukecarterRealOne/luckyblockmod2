package com.lucky.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class LuckyBlockMod implements ModInitializer {
    public static final String MOD_ID = "luckyblockmod";

    public static final Item LUCKY_BLOCK = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "lucky_block"),
            new LuckyBlockItem(new Item.Settings().maxCount(1)));

    public static final ItemGroup LUCKY_TAB = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(MOD_ID, "main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(LUCKY_BLOCK))
                    .displayName(Text.literal("Lucky Block"))
                    .entries((context, entries) -> entries.add(LUCKY_BLOCK))
                    .build());

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(FxPayload.ID, FxPayload.CODEC);
        Scheduler.init();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(LUCKY_BLOCK));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(LUCKY_BLOCK));
    }
}
