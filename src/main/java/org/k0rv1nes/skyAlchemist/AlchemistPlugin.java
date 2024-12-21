package org.k0rv1nes.skyAlchemist;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class AlchemistPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private static Economy economy = null;
    private final Map<String, Inventory> openInventories = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("alchemist")).setExecutor(this);
        getLogger().info("AlchemistPlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("AlchemistPlugin disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Inventory menu = createMenu();
        openInventories.put(player.getName(), menu);
        player.openInventory(menu);
        return true;
    }

    private Inventory createMenu() {
        Inventory inventory = Bukkit.createInventory(null, 54, "Alchemist");

        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack confirmButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemStack cancelButton = new ItemStack(Material.REDSTONE_BLOCK);

        for (int i = 36; i < 54; i++) {
            inventory.setItem(i, glassPane);
        }
        inventory.setItem(45, cancelButton);
        inventory.setItem(49, new ItemStack(Material.GLASS_BOTTLE));  // Placeholder for the result potion
        inventory.setItem(53, confirmButton);

        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!openInventories.containsValue(inventory)) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot >= 36) {
            event.setCancelled(true);
            if (slot == 45) { // Cancel
                player.closeInventory();
            } else if (slot == 53) { // Confirm
                processPotions(player, inventory);
            }
            return;
        }

        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && cursorItem.getType() == Material.POTION) {
            return; // Разрешить добавление зелья
        }

        event.setCancelled(true);
    }

    private void processPotions(Player player, Inventory inventory) {
        ItemStack result = createResultPotion(inventory);
        if (result == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage("You need to combine at least one potion.");
            return;
        }

        double price = calculatePrice(inventory);
        if (economy.getBalance(player) < price) {
            player.sendMessage("You don't have enough money!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdrawPlayer(player, price);
        player.getInventory().addItem(result);
        inventory.clear();
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private ItemStack createResultPotion(Inventory inventory) {
        List<PotionEffect> potionEffects = new ArrayList<>();
        boolean isValidPotionType = true;
        Material potionType = null;
        boolean isSplash = false;
        boolean isLingering = false;

        // Логирование на каждом шаге
        getLogger().info("Scanning inventory for potions...");

        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.POTION && item.hasItemMeta()) {
                getLogger().info("Potion found at slot " + i);

                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    if (potionType == null) {
                        potionType = item.getType();
                    } else if (potionType != item.getType()) {
                        isValidPotionType = false;
                        break; // Если типы разные, прекращаем обработку
                    }

                    // Определяем, является ли зелье туманным или взрывным
                    if (item.getType() == Material.SPLASH_POTION) {
                        isSplash = true;
                    } else if (item.getType() == Material.LINGERING_POTION) {
                        isLingering = true;
                    }

                    // Добавляем эффекты этого зелья в список
                    potionEffects.addAll(meta.getCustomEffects());
                }
            }
        }

        // Логирование, если нет эффектов
        if (potionEffects.isEmpty()) {
            getLogger().info("No potions with effects found.");
        }

        if (!isValidPotionType || potionEffects.isEmpty()) {
            return null;
        }

        // Если есть разные типы зелий (например, обычное и туманное), возвращаем null
        if (isSplash && isLingering || isSplash && potionType != Material.SPLASH_POTION || isLingering && potionType != Material.LINGERING_POTION) {
            return null;
        }

        // Создаем новое зелье в зависимости от типа
        ItemStack resultPotion;
        if (isSplash) {
            resultPotion = new ItemStack(Material.SPLASH_POTION);
        } else if (isLingering) {
            resultPotion = new ItemStack(Material.LINGERING_POTION);
        } else {
            resultPotion = new ItemStack(Material.POTION); // Обычное зелье
        }

        PotionMeta resultMeta = (PotionMeta) resultPotion.getItemMeta();
        if (resultMeta == null) return null;

        // Добавляем все эффекты из собранных зелий
        for (PotionEffect effect : potionEffects) {
            resultMeta.addCustomEffect(effect, true); // Добавляем эффект
        }

        resultMeta.setColor(Color.PURPLE); // Можно добавить логику для определения цвета
        resultPotion.setItemMeta(resultMeta);
        return resultPotion;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        if (openInventories.remove(player.getName()) == inventory) {
            for (int i = 0; i < 36; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() == Material.POTION) {
                    player.getInventory().addItem(item);
                }
            }
        }
    }




    private double calculatePrice(Inventory inventory) {
        int potionCount = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.POTION) {
                potionCount++;
            }
        }

        double basePrice = 10.0;
        double multiplier = 1.5;
        return basePrice * Math.pow(multiplier, potionCount - 1);
    }
}
