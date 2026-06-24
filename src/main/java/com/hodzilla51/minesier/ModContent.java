package com.hodzilla51.minesier;

import java.util.function.Function;

import com.hodzilla51.minesier.block.ComputerBlock;
import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.CableBlock;
import com.hodzilla51.minesier.block.SwitchBlock;
import com.hodzilla51.minesier.block.SwitchBlockEntity;
import com.hodzilla51.minesier.block.TurtleBlock;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.block.WirelessModemBlock;
import com.hodzilla51.minesier.block.WirelessModemBlockEntity;
import com.hodzilla51.minesier.item.DiskContents;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Central registration of MineSIer's blocks, items, and block entities. */
public final class ModContent {
	private ModContent() {
	}

	public static final Identifier COMPUTER_ID = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "computer");
	public static final Identifier CABLE_ID = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "cable");
	public static final Identifier SWITCH_ID = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "switch");
	public static final Identifier TURTLE_ID = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "turtle");
	public static final Identifier WIRELESS_MODEM_ID = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "wireless_modem");

	public static final Block COMPUTER_BLOCK = registerBlock(COMPUTER_ID, ComputerBlock::new);
	public static final Block CABLE_BLOCK = registerBlock(CABLE_ID, CableBlock::new);
	public static final Block SWITCH_BLOCK = registerBlock(SWITCH_ID, SwitchBlock::new);
	public static final Block TURTLE_BLOCK = registerBlock(TURTLE_ID, TurtleBlock::new);
	public static final Block WIRELESS_MODEM_BLOCK = registerBlock(WIRELESS_MODEM_ID, WirelessModemBlock::new);

	public static final BlockEntityType<ComputerBlockEntity> COMPUTER_BLOCK_ENTITY =
		registerBlockEntity(COMPUTER_ID, ComputerBlockEntity::new, COMPUTER_BLOCK);
	public static final BlockEntityType<SwitchBlockEntity> SWITCH_BLOCK_ENTITY =
		registerBlockEntity(SWITCH_ID, SwitchBlockEntity::new, SWITCH_BLOCK);
	public static final BlockEntityType<TurtleBlockEntity> TURTLE_BLOCK_ENTITY =
		registerBlockEntity(TURTLE_ID, TurtleBlockEntity::new, TURTLE_BLOCK);
	public static final BlockEntityType<WirelessModemBlockEntity> WIRELESS_MODEM_BLOCK_ENTITY =
		registerBlockEntity(WIRELESS_MODEM_ID, WirelessModemBlockEntity::new, WIRELESS_MODEM_BLOCK);

	/** Data component carrying a disk's program files (travels with the disk item). */
	public static final DataComponentType<DiskContents> DISK_CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "disk_contents"),
		DataComponentType.<DiskContents>builder()
			.persistent(DiskContents.CODEC)
			.networkSynchronized(DiskContents.STREAM_CODEC)
			.build());

	/** A floppy-style disk: portable storage for programs. */
	public static final Item DISK = registerDisk();

	/** Creative tab gathering all MineSIer content. */
	public static final CreativeModeTab TAB = registerTab();

	private static CreativeModeTab registerTab() {
		Identifier id = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "main");
		CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
			.title(Component.translatable("itemGroup.minesier.main"))
			.icon(() -> new ItemStack(COMPUTER_BLOCK))
			.displayItems((params, output) -> {
				output.accept(COMPUTER_BLOCK);
				output.accept(CABLE_BLOCK);
				output.accept(SWITCH_BLOCK);
				output.accept(WIRELESS_MODEM_BLOCK);
				output.accept(TURTLE_BLOCK);
				output.accept(DISK);
			})
			.build();
		return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
	}

	private static Item registerDisk() {
		Identifier id = Identifier.fromNamespaceAndPath(MineSIer.MOD_ID, "disk");
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, id,
			new Item(new Item.Properties().setId(key).stacksTo(1).component(DISK_CONTENTS, DiskContents.EMPTY)));
	}

	private static Block registerBlock(Identifier id, Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		// No requiresCorrectToolForDrops(): without mineable/tool tags it would block all
		// drops; these machine blocks simply drop themselves (loot tables) when mined.
		Block block = Registry.register(BuiltInRegistries.BLOCK, id,
			factory.apply(BlockBehaviour.Properties.of().strength(2.0F).setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, id,
			new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));

		return block;
	}

	private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntity(Identifier id,
			FabricBlockEntityTypeBuilder.Factory<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
			FabricBlockEntityTypeBuilder.create(factory, block).build());
	}

	/** Forces class-load so the static initializers above run. */
	public static void init() {
	}
}
