package alex9303.quickfarm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.minecraft.server.command.CommandManager.*;

public class QuickFarm implements ModInitializer {
    public static final String MOD_ID = "quickfarm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean quickFarmEnabled = true; // Enabled by default

    @Override
    public void onInitialize() {
        LOGGER.info("QuickFarm mod initialized!");

        // Register the toggle command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("togglequickfarm")
                .requires(source -> source.hasPermissionLevel(3)) // Requires operator privileges
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    quickFarmEnabled = !quickFarmEnabled;

                    String status = quickFarmEnabled ? "§2enabled" : "§4disabled";
                    source.sendFeedback(() -> Text.literal("§6[QuickFarm] §rQuick farming globally " + status + "."), true);
                    return 1;
                })));

        // Register block interaction handler for harvesting by interacting
        UseBlockCallback.EVENT.register(this::onBlockUse);
    }

    private ActionResult onBlockUse(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (!quickFarmEnabled || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        ItemStack seed = getPlantableSeed(state.getBlock());

        if (seed == null) {
            return ActionResult.PASS;
        }

        boolean isMature = false;
        if (state.getBlock() instanceof CropBlock crop) {
            isMature = crop.isMature(state);
        } else if (state.getBlock() instanceof NetherWartBlock) {
            isMature = state.get(NetherWartBlock.AGE) >= 3;
        } else {
            return ActionResult.PASS;
        }

        if (!isMature) {
            return ActionResult.PASS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer && !world.isClient) {
            ItemStack heldItem = serverPlayer.getMainHandStack();

            // Get the drops that would normally occur
            List<ItemStack> drops = Block.getDroppedStacks(state, (ServerWorld) world, pos, null, serverPlayer, heldItem);

            // Check if drops contain at least one seed
            boolean hasSeed = false;
            for (ItemStack drop : drops) {
                if (drop.getItem() == seed.getItem() && drop.getCount() >= 1) {
                    hasSeed = true;
                    drop.decrement(1); // Remove one seed from the drops
                    break;
                }
            }

            if (hasSeed) {
                // Play block break sound
                world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);

                // Drop modified items
                for (ItemStack drop : drops) {
                    if (drop.getCount() > 0) {
                        ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                        world.spawnEntity(itemEntity);
                    }
                }

                // Reset crop to age 0
                if (state.getBlock() instanceof CropBlock crop) {
                    world.setBlockState(pos, crop.withAge(0), 2);
                } else if (state.getBlock() instanceof NetherWartBlock) {
                    world.setBlockState(pos, state.with(NetherWartBlock.AGE, 0), 2);
                }
                return ActionResult.SUCCESS; // Cancel normal interaction
            }
        }
        return ActionResult.PASS;
    }

    private ItemStack getPlantableSeed(Block block) {
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT_SEEDS);
        if (block == Blocks.NETHER_WART) return new ItemStack(Items.NETHER_WART);
        return null;
    }
}