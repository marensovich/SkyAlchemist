package org.k0rv1nes.skyAlchemist;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class AlchemistPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private FileConfiguration config;
    private final Map<String, Inventory> openInventories = new HashMap<>();
    private PlayerPointsAPI playerPointsAPI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        PlayerPoints playerPoints = (PlayerPoints) Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints != null) {
            playerPointsAPI = playerPoints.getAPI();
        } else {
            getLogger().severe("PlayerPoints не найден! Отключение плагина.");
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("alchemist")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("alchemist.reload")) {
                    sender.sendMessage(colorize(config.getString("messages.no-permission", "&cУ вас нет прав для выполнения этой команды.")));
                    return true;
                }
                reloadConfig();
                config = getConfig();
                sender.sendMessage(colorize(config.getString("messages.reload-success", "&aКонфигурация успешно перезагружена.")));
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(colorize(Objects.requireNonNull(config.getString("messages.only-player"))));
                return true;
            }

            Inventory menu = createMenu();
            openInventories.put(player.getName(), menu);
            player.openInventory(menu);
            return true;
        }
        return false;
    }

    private Inventory createMenu() {
        String menuName = config.getString("menu.name", "&7Алхимик:");
        Inventory inventory = Bukkit.createInventory(null, 54, colorize(menuName));

        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack confirmButton = createButton(config.getString("menu.cross-button-material", "GREEN_STAINED_GLASS_PANE"),
                config.getString("menu.cross-button-name", "&a&lСкрестить зелья"));
        ItemStack cancelButton = createButton(config.getString("menu.quit-button-material", "RED_STAINED_GLASS_PANE"),
                config.getString("menu.quit-button-name", "&cВыйти"));

        for (int i = 36; i < 54; i++) {
            inventory.setItem(i, glassPane);
        }
        inventory.setItem(45, cancelButton);
        inventory.setItem(49, new ItemStack(Material.GLASS_BOTTLE));
        inventory.setItem(53, confirmButton);

        return inventory;
    }

    private ItemStack createButton(String materialName, String displayName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(displayName));
            button.setItemMeta(meta);
        }
        return button;
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
            if (slot == 45) {
                player.closeInventory();
            } else if (slot == 53) {
                processPotions(player, inventory);
            }
            return;
        }

        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && (cursorItem.getType() == Material.POTION || cursorItem.getType() == Material.SPLASH_POTION || cursorItem.getType() == Material.LINGERING_POTION)) {
            event.setCancelled(false);
            return;
        }

        if (event.getClick().isRightClick() && inventory.getItem(slot) != null &&
                (inventory.getItem(slot).getType() == Material.POTION || inventory.getItem(slot).getType() == Material.SPLASH_POTION || inventory.getItem(slot).getType() == Material.LINGERING_POTION)) {
            player.getInventory().addItem(inventory.getItem(slot));
            inventory.setItem(slot, null);
            return;
        }

        event.setCancelled(true);
    }

    private void processPotions(Player player, Inventory inventory) {
        List<PotionEffect> potionEffects = new ArrayList<>();
        Material potionType = null;
        boolean isSplash = false;
        boolean isLingering = false;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)) {
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    potionEffects.addAll(meta.getCustomEffects());

                    PotionData potionData = meta.getBasePotionData();
                    PotionEffectType effectType = potionData.getType().getEffectType();
                    if (effectType != null) {
                        PotionEffect effect = new PotionEffect(effectType, potionData.isExtended() ? 9600 : 3600, potionData.isUpgraded() ? 1 : 0);
                        potionEffects.add(effect);
                    }

                    if (potionType == null) {
                        potionType = item.getType();
                    } else if (potionType != item.getType()) {
                        player.sendMessage(colorize(config.getString("menu.not-enough-money", "&cНа вашем счете недостаточно средств")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    if (item.getType() == Material.SPLASH_POTION) {
                        isSplash = true;
                    } else if (item.getType() == Material.LINGERING_POTION) {
                        isLingering = true;
                    }
                }
            }
        }

        if (potionEffects.isEmpty()) {
            player.sendMessage(colorize(config.getString("menu.not-enough-money", "&cНа вашем счете недостаточно средств")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack resultPotion = createResultPotion(potionType, isSplash, isLingering, potionEffects);
        double price = calculatePrice(potionEffects.size());
        int roundedPrice = (int) Math.round(price);

        if (playerPointsAPI.look(player.getUniqueId()) < roundedPrice) {
            player.sendMessage(colorize(config.getString("menu.not-enough-money", "&cНа вашем счете недостаточно средств")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        playerPointsAPI.take(player.getUniqueId(), roundedPrice);

        String withdrawMessage = config.getString("economy.withdraw-message", "&aС вас списано {amount} $.");
        withdrawMessage = withdrawMessage.replace("{amount}", String.valueOf(roundedPrice));
        player.sendMessage(colorize(withdrawMessage));

        player.getInventory().addItem(resultPotion);
        inventory.clear();
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private ItemStack createResultPotion(Material potionType, boolean isSplash, boolean isLingering, List<PotionEffect> effects) {
        ItemStack resultPotion;
        if (isSplash) {
            resultPotion = new ItemStack(Material.SPLASH_POTION);
        } else if (isLingering) {
            resultPotion = new ItemStack(Material.LINGERING_POTION);
        } else {
            resultPotion = new ItemStack(Material.POTION);
        }

        PotionMeta meta = (PotionMeta) resultPotion.getItemMeta();
        if (meta == null) return null;

        for (PotionEffect effect : effects) {
            meta.addCustomEffect(effect, true);
        }

        List<Integer> colorRGB = config.getIntegerList("menu.cross-potion-color");
        if (colorRGB.size() == 3) {
            meta.setColor(Color.fromRGB(colorRGB.get(0), colorRGB.get(1), colorRGB.get(2)));
        } else {
            meta.setColor(Color.PURPLE);
        }

        meta.setDisplayName(colorize(config.getString("menu.cross-potion-name", "&eСкрещенное зелье")));
        resultPotion.setItemMeta(meta);
        return resultPotion;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        if (openInventories.remove(player.getName()) == inventory) {
            for (int i = 0; i < 36; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)) {
                    player.getInventory().addItem(item);
                }
            }
        }
    }

    private double calculatePrice(int effectCount) {
        double basePrice = config.getDouble("start-price", 100.0);
        double multiplier = config.getDouble("price-procent", 1.5);
        return basePrice * Math.pow(multiplier, effectCount);
    }

    private String colorize(String text) {
        return text.replace('&', '§');
    }
}