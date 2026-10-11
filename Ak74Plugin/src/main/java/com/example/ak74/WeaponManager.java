package com.example.ak74;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================================
 *  WeaponManager — фабрика и хранилище данных для AK-74 и патронов
 * =====================================================================
 *  Отвечает за:
 *   • Создание предмета AK-74 (магазин 30/30, пустой запас)
 *   • Создание предмета патронов 5.45×39
 *   • Проверку, является ли предмет AK-74 / патроном
 *   • Чтение и запись патронов в PersistentDataContainer (PDC)
 *   • Обновление лора предмета при изменении патронов
 *
 *  Оптимизации:
 *   • consumeOneRound() — списание 1 патрона за 1 NBT-операцию
 *   • readAmmo() — чтение обоих счётчиков за 1 операцию
 *   • updateLore() вызывается только при перезарядке / таймером
 * =====================================================================
 */
public class WeaponManager {

    private final Ak74Plugin plugin;

    // ==================== КОНСТАНТЫ ОРУЖИЯ ====================
    /** Ёмкость магазина. */
    public static final int MAG_SIZE = 30;
    /** Максимум патронов в запасе. */
    public static final int MAX_RESERVE = 180;
    /** ID оружия (хранится в PDC). */
    public static final String WEAPON_ID = "ak74";
    /** ID патронов (хранится в PDC). */
    public static final String AMMO_ID = "ammo_545x39";

    // ==================== КОНСТАНТЫ ПРЕДМЕТОВ =================
    /** Материал оружия. */
    private static final Material WEAPON_MATERIAL = Material.NETHERITE_HOE;
    /** Материал патронов. */
    private static final Material AMMO_MATERIAL = Material.IRON_NUGGET;

    public WeaponManager(Ak74Plugin plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  1. СОЗДАНИЕ AK-74
    // =====================================================================
    public ItemStack createAk74() {
        ItemStack item = new ItemStack(WEAPON_MATERIAL);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("AK-74")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Автомат Калашникова 1974")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Патроны: 5.45×39 мм")
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Магазин: " + MAG_SIZE + " / " + MAG_SIZE)
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Запас: 0")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("ЛКМ — Огонь")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("ПКМ — Умная перезарядка")
                .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Ak74Plugin.KEY_WEAPON_ID, PersistentDataType.STRING, WEAPON_ID);
        pdc.set(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, MAG_SIZE);
        pdc.set(Ak74Plugin.KEY_AMMO_RESERVE, PersistentDataType.INTEGER, 0);

        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================
    //  2. СОЗДАНИЕ ПАТРОНОВ
    // =====================================================================
    public ItemStack createAmmo(int amount) {
        if (amount < 1) amount = 1;
        if (amount > 64) amount = 64;

        ItemStack item = new ItemStack(AMMO_MATERIAL, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Патрон 5.45×39")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Боеприпас для AK-74")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Ak74Plugin.KEY_AMMO_ID, PersistentDataType.STRING, AMMO_ID);

        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================
    //  3. ПРОВЕРКИ ПРЕДМЕТОВ
    // =====================================================================
    public boolean isAk74(ItemStack item) {
        if (item == null || item.getType() != WEAPON_MATERIAL) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer()
                .get(Ak74Plugin.KEY_WEAPON_ID, PersistentDataType.STRING);
        return WEAPON_ID.equals(id);
    }

    public boolean isAmmo(ItemStack item) {
        if (item == null || item.getType() != AMMO_MATERIAL) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer()
                .get(Ak74Plugin.KEY_AMMO_ID, PersistentDataType.STRING);
        return AMMO_ID.equals(id);
    }

    // =====================================================================
    //  4. ЧТЕНИЕ ПАТРОНОВ
    // =====================================================================

    /** Медленное чтение магазина. */
    public int getAmmoInMag(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer()
                .getOrDefault(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, 0);
    }

    /** Медленное чтение запаса. */
    public int getAmmoReserve(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer()
                .getOrDefault(Ak74Plugin.KEY_AMMO_RESERVE, PersistentDataType.INTEGER, 0);
    }

    /**
     * ★ БЫСТРОЕ чтение обоих счётчиков за одну операцию.
     * @return int[] {inMag, reserve}
     */
    public int[] readAmmo(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return new int[]{0, 0};

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int inMag = pdc.getOrDefault(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, 0);
        int reserve = pdc.getOrDefault(Ak74Plugin.KEY_AMMO_RESERVE, PersistentDataType.INTEGER, 0);
        return new int[]{inMag, reserve};
    }

    // =====================================================================
    //  5. ЗАПИСЬ ПАТРОНОВ
    // =====================================================================

    /**
     * Полная запись патронов с обновлением лора.
     * Использовать при перезарядке.
     */
    public void setAmmo(ItemStack gun, int inMag, int reserve) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, inMag);
        pdc.set(Ak74Plugin.KEY_AMMO_RESERVE, PersistentDataType.INTEGER, reserve);
        gun.setItemMeta(meta);
        updateLore(gun);
    }

    /**
     * Быстрая запись без обновления лора.
     */
    public void setAmmoFast(ItemStack gun, int inMag, int reserve) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, inMag);
        pdc.set(Ak74Plugin.KEY_AMMO_RESERVE, PersistentDataType.INTEGER, reserve);
        gun.setItemMeta(meta);
    }

    /**
     * ★ БЫСТРОЕ списание одного патрона из магазина.
     * @return новое количество патронов, или -1, если магазин пуст
     */
    public int consumeOneRound(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return -1;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int inMag = pdc.getOrDefault(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, 0);
        if (inMag <= 0) return -1;

        int newInMag = inMag - 1;
        pdc.set(Ak74Plugin.KEY_AMMO_IN_MAG, PersistentDataType.INTEGER, newInMag);
        gun.setItemMeta(meta);
        return newInMag;
    }

    // =====================================================================
    //  6. ОБНОВЛЕНИЕ ЛОРА
    // =====================================================================
    public void updateLore(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return;

        int[] ammo = readAmmo(gun);
        int inMag = ammo[0];
        int reserve = ammo[1];

        boolean changed = false;

        for (int i = 0; i < lore.size(); i++) {
            String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (plain.startsWith("Магазин:")) {
                lore.set(i, Component.text("Магазин: " + inMag + " / " + MAG_SIZE)
                        .color(inMag > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                changed = true;
            } else if (plain.startsWith("Запас:")) {
                lore.set(i, Component.text("Запас: " + reserve)
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                changed = true;
            }
        }

        if (changed) {
            meta.lore(lore);
            gun.setItemMeta(meta);
        }
    }
}