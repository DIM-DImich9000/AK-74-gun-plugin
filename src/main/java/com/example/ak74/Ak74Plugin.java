package com.example.ak74;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Ak74Plugin extends JavaPlugin {

    private static Ak74Plugin instance;
    private WeaponManager weaponManager;
    private RecipeManager recipeManager;
    private WeaponListener weaponListener;

    public static NamespacedKey KEY_WEAPON_ID;
    public static NamespacedKey KEY_AMMO_IN_MAG;
    public static NamespacedKey KEY_AMMO_RESERVE;
    public static NamespacedKey KEY_AMMO_ID;

    @Override
    public void onEnable() {
        instance = this;

        KEY_WEAPON_ID    = new NamespacedKey(this, "weapon_id");
        KEY_AMMO_IN_MAG  = new NamespacedKey(this, "ammo_in_mag");
        KEY_AMMO_RESERVE = new NamespacedKey(this, "ammo_reserve");
        KEY_AMMO_ID      = new NamespacedKey(this, "ammo_id");

        weaponManager = new WeaponManager(this);
        recipeManager = new RecipeManager(this);

        weaponListener = new WeaponListener(this);
        getServer().getPluginManager().registerEvents(weaponListener, this);
        weaponListener.startActionBarTask();

        getCommand("ak74").setExecutor(new Ak74Command(this));

        recipeManager.registerRecipes();

        getLogger().info("Ak74Plugin v1.2.0 включён на Purpur 26.3!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Ak74Plugin выключен.");
    }

    public static Ak74Plugin getInstance() { return instance; }
    public WeaponManager getWeaponManager() { return weaponManager; }
    public RecipeManager getRecipeManager() { return recipeManager; }
}