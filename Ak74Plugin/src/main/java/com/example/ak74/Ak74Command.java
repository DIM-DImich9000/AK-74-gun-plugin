package com.example.ak74;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Ak74Command implements CommandExecutor {

    private final Ak74Plugin plugin;

    public Ak74Command(Ak74Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Использование: /ak74 <give|ammo>")
                    .color(NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                player.getInventory().addItem(plugin.getWeaponManager().createAk74());
                player.sendMessage(Component.text("Выдан AK-74.")
                        .color(NamedTextColor.GREEN));
            }
            case "ammo" -> {
                player.getInventory().addItem(plugin.getWeaponManager().createAmmo(30));
                player.sendMessage(Component.text("Выдано 30 патронов 5.45×39.")
                        .color(NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Неизвестная подкоманда.")
                    .color(NamedTextColor.RED));
        }
        return true;
    }
}