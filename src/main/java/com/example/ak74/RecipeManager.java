package com.example.ak74;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

public class RecipeManager {

    private final Ak74Plugin plugin;

    private final NamespacedKey ak74Key;
    private final NamespacedKey ammoKey;

    public RecipeManager(Ak74Plugin plugin) {
        this.plugin = plugin;
        this.ak74Key = new NamespacedKey(plugin, "ak74_craft");
        this.ammoKey = new NamespacedKey(plugin, "ammo_545x39_craft");
    }

    /**
     * Регистрирует все рецепты плагина.
     */
    public void registerRecipes() {
        plugin.getServer().removeRecipe(ak74Key);
        plugin.getServer().removeRecipe(ammoKey);

        registerAk74Recipe();
        registerAmmoRecipe();
    }

    /**
     * Рецепт AK-74:
     *
     *     I I I      ← 3 железных слитка
     *     P G P      ← порох, кремень, порох
     *     S I S      ← палка, ЖЕЛЕЗО, палка
     */
    private void registerAk74Recipe() {
        ShapedRecipe recipe = new ShapedRecipe(ak74Key, plugin.getWeaponManager().createAk74());

        recipe.shape(
                "III",
                "PGP",
                "SIS"
        );

        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('P', Material.GUNPOWDER);
        recipe.setIngredient('G', Material.FLINT);
        recipe.setIngredient('S', Material.STICK);

        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Рецепт AK-74 зарегистрирован.");
    }

    /**
     * Рецепт патронов 5.45×39 (бесформенный).
     * 1 железный самородок + 1 порох = 4 патрона.
     */
    private void registerAmmoRecipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(ammoKey, plugin.getWeaponManager().createAmmo(4));

        recipe.addIngredient(Material.IRON_NUGGET);
        recipe.addIngredient(Material.GUNPOWDER);

        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Рецепт патронов 5.45×39 зарегистрирован.");
    }

    public NamespacedKey getAk74Key() { return ak74Key; }
    public NamespacedKey getAmmoKey() { return ammoKey; }
}