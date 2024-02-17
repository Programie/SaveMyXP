package com.selfcoders.savemyxp;

import com.selfcoders.bukkitlibrary.Experience;
import com.selfcoders.bukkitlibrary.SignUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
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

import java.util.List;

public final class SaveMyXP extends JavaPlugin implements Listener {
    final static String FIRST_LINE = "[SaveMyXP]";

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

        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signBlock == null) {
            return;
        }

        if (!player.hasPermission("savemyxp.create")) {
            player.sendMessage(ChatColor.RED + "You do not have the required permissions to create XP signs!");
            event.setCancelled(true);
            return;
        }

        if (!signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }

        SignData signData = getSignData(signBlock.getLocation());
        signData.set(player, 0);

        // Use delayed task, otherwise the sign is not updated if using sign.setLine() instead of event.setLine()
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> updateSign(signBlock));

        player.sendMessage(ChatColor.GREEN + "XP sign placed");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        Sign signBlock = SignUtils.getSignFromBlock(block);

        if (signBlock == null) {
            return;
        }

        if (!isXPSign(signBlock)) {
            return;
        }

        if (!signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
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
                updateSign(signBlock);
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
                updateSign(signBlock);
                player.sendMessage(ChatColor.GREEN + "Transferred " + withdrawXP + " XP from your XP sign");
                break;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!Tag.ALL_SIGNS.isTagged(blockType)) {
            if (hasBlockXPSign(block)) {
                event.setCancelled(true);
            }

            return;
        }

        Sign signBlock = SignUtils.getSignFromBlock(block);

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

    private void updateSign(Sign sign) {
        SignData signData = getSignData(sign.getLocation());
        int xp = signData.getXP();

        OfflinePlayer player = getServer().getOfflinePlayer(signData.getUUID());
        String playerName = player.getName();

        int level = (int) Experience.getLevelFromExp(xp);
        int remainingXP = xp - Experience.getExpFromLevel(level);

        SignSide signSide = sign.getSide(Side.FRONT);

        signSide.setLine(0, ChatColor.BLUE + FIRST_LINE);
        signSide.setLine(1, playerName == null ? "" : playerName);
        signSide.setLine(2, "Level: " + level);
        signSide.setLine(3, "XP: " + remainingXP);

        sign.update();
    }

    private boolean isXPSign(String[] lines) {
        return FIRST_LINE.equalsIgnoreCase(ChatColor.stripColor(lines[0]));
    }

    private boolean isXPSign(Sign sign) {
        return isXPSign(sign.getSide(Side.FRONT).getLines());
    }

    private boolean hasBlockXPSign(Block block) {
        return SignUtils.hasBlockSign(block, this::isXPSign);
    }

    private boolean hasBlockXPSign(List<Block> blocks) {
        return SignUtils.hasBlockSign(blocks, this::isXPSign);
    }
}