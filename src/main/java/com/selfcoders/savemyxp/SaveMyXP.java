package com.selfcoders.savemyxp;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public final class SaveMyXP extends JavaPlugin implements Listener {
    final static String FIRST_LINE = "[SaveMyXP]";
    final static List<BlockFace> BLOCK_FACES = Arrays.asList(BlockFace.UP, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginManager pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!isXPSign(event.getLines())) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!player.hasPermission("savemyxp.create")) {
            player.sendMessage(ChatColor.RED + "You do not have the required permissions to create XP signs!");
            block.breakNaturally();
            return;
        }

        SignData signData = getSignData(block.getLocation());
        signData.set(player, 0);

        event.setLine(0, ChatColor.BLUE + FIRST_LINE);
        event.setLine(1, player.getName());
        event.setLine(2, "Level: 0");
        event.setLine(3, "XP: 0");

        player.sendMessage(ChatColor.GREEN + "XP sign placed");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        Sign signBlock = getSignFromBlock(block);

        if (signBlock == null) {
            return;
        }

        if (!isXPSign(signBlock)) {
            return;
        }

        Player player = event.getPlayer();

        SignData signData = getSignData(signBlock.getLocation());

        if (!player.getUniqueId().equals(signData.getUUID())) {
            player.sendMessage(ChatColor.RED + "This is not your own XP sign!");
            return;
        }

        if (!player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            player.sendMessage(ChatColor.RED + "You have to hit the sign with your empty hand!");
            return;
        }

        int configTransferAmount = getConfig().getInt("transfer-amount");

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK:
                int addXP = Experience.getExp(player);

                if (addXP == 0) {
                    player.sendMessage(ChatColor.RED + "You do not have any XP!");
                    return;
                }

                // Transfer the configured amount if sneaking and the player has enough XP, otherwise transfer the whole XP to the sign
                if (player.isSneaking()) {
                    if (addXP > configTransferAmount) {
                        addXP = configTransferAmount;
                    }
                }

                signData.addXP(addXP);
                updateSign(block);
                Experience.changeExp(player, -addXP);
                player.sendMessage(ChatColor.GREEN + "Transferred " + addXP + " XP (" + (int) Experience.getLevelFromExp(addXP) + " levels) to your XP sign");
                break;
            case RIGHT_CLICK_BLOCK:
                int signXP = signData.getXP();

                if (signXP == 0) {
                    player.sendMessage(ChatColor.RED + "There are no more XP on your sign!");
                    return;
                }

                int withdrawXP = signXP;

                // Withdraw the configured amount if not sneaking and sign has enough XP, otherwise withdraw the whole amount of XP on the sign
                if (!player.isSneaking() && signXP > configTransferAmount) {
                    withdrawXP = configTransferAmount;
                }

                Experience.changeExp(player, withdrawXP);
                signData.addXP(-withdrawXP);
                updateSign(block);
                player.sendMessage(ChatColor.GREEN + "Transferred " + withdrawXP + " XP from your XP sign");
                break;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (Tag.SIGNS.isTagged(blockType)) {
            Sign signBlock = getSignFromBlock(block);

            if (signBlock == null) {
                return;
            }

            if (!isXPSign(signBlock)) {
                return;
            }

            Player player = event.getPlayer();
            SignData signData = getSignData(signBlock.getLocation());
            boolean isOwnSign = player.getUniqueId().equals(signData.getUUID());

            if (!isOwnSign && !player.hasPermission("savemyxp.destroy-any")) {
                player.sendMessage(ChatColor.RED + "This is not your own XP sign!");
                event.setCancelled(true);
                return;
            }

            if (isOwnSign && signData.getXP() > 0) {
                player.sendMessage(ChatColor.RED + "Please withdraw the stored XP from the sign first");
                event.setCancelled(true);
                return;
            }

            signData.remove();
            player.sendMessage(ChatColor.GREEN + "XP sign removed");
        } else {
            if (hasBlockXPSign(block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (hasBlockXPSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (hasBlockXPSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (hasBlockXPSign(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (hasBlockXPSign(event.blockList())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        saveConfig();
    }

    private SignData getSignData(Location location) {
        return new SignData(this, location);
    }

    private void updateSign(Block block) {
        Sign sign = getSignFromBlock(block);
        if (sign == null) {
            return;
        }

        SignData signData = getSignData(block.getLocation());
        int xp = signData.getXP();

        OfflinePlayer player = getServer().getOfflinePlayer(signData.getUUID());
        String playerName = player.getName();

        int level = (int) Experience.getLevelFromExp(xp);
        int remainingXP = xp - Experience.getExpFromLevel(level);

        sign.setLine(0, ChatColor.BLUE + FIRST_LINE);
        sign.setLine(1, playerName == null ? "" : playerName);
        sign.setLine(2, "Level: " + level);
        sign.setLine(3, "XP: " + remainingXP);
        sign.update();
    }

    private boolean isXPSign(String[] lines) {
        return FIRST_LINE.equalsIgnoreCase(ChatColor.stripColor(lines[0]));
    }

    private boolean isXPSign(Sign sign) {
        return isXPSign(sign.getLines());
    }

    private boolean hasBlockXPSign(Block block) {
        for (BlockFace blockFace : BLOCK_FACES) {
            Block faceBlock = block.getRelative(blockFace);
            Material faceBlockType = faceBlock.getType();

            if (Tag.WALL_SIGNS.isTagged(faceBlockType)) {
                Sign signBlock = (Sign) faceBlock.getState();
                BlockFace attachedFace = ((WallSign) signBlock.getBlockData()).getFacing();
                if (blockFace.equals(attachedFace) && isXPSign(signBlock)) {
                    return true;
                }
            }

            if (blockFace.equals(BlockFace.UP) && Tag.STANDING_SIGNS.isTagged(faceBlockType)) {
                Sign signBlock = (Sign) faceBlock.getState();
                if (isXPSign(signBlock)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasBlockXPSign(List<Block> blocks) {
        for (Block block : blocks) {
            if (hasBlockXPSign(block)) {
                return true;
            }
        }

        return false;
    }

    private Sign getSignFromBlock(Block block) {
        BlockData blockData = block.getBlockData();

        if (!(blockData instanceof WallSign) && !(blockData instanceof org.bukkit.block.data.type.Sign)) {
            return null;
        }

        BlockState blockState = block.getState();

        if (!(blockState instanceof Sign)) {
            return null;
        }

        return (Sign) blockState;
    }
}