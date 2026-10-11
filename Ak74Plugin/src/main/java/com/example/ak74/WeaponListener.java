package com.example.ak74;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * =====================================================================
 *  WeaponListener — вся боевая механика AK-74
 * =====================================================================
 *  Функции:
 *   • Автоматическая стрельба при удержании ЛКМ (~650 в/мин)
 *   • Умная перезарядка на ПКМ
 *   • ★ В креативе — бесконечные патроны (магазин не тратится)
 *   • Урон 2.5 сердца за выстрел (игрок без брони умирает за 4 выстрела)
 *   • Броня работает штатно, без хедшотов
 *   • Сокращённая неуязвимость (2 тика)
 *   • Без отдачи стрелка
 *   • Лёгкая трасса пули (END_ROD)
 *   • Динамический HUD в ActionBar
 * =====================================================================
 */
public class WeaponListener implements Listener {

    // ==================== НАСТРОЙКИ СТРЕЛЬБЫ ====================
    /** Интервал между выстрелами (мс). ~650 в/мин = 95 мс. */
    private static final long FIRE_INTERVAL_MS = 95L;
    /** Частиц у дула (дульная вспышка). */
    private static final int MUZZLE_PARTICLES = 3;

    // ==================== НАСТРОЙКИ УРОНА ======================
    /**
     * Урон за выстрел (HP, 1 HP = 0.5 сердца).
     * 5.0 = 2.5 сердца → игрок без брони (20 HP) умирает за 4 выстрела.
     */
    private static final double BULLET_DAMAGE = 5.0;

    /** Множитель урона по монстрам. 1.0 = обычный. */
    private static final double MOB_DAMAGE_MULTIPLIER = 1.0;

    /**
     * Максимальная неуязвимость цели после попадания (тики).
     * 2 тика (0.1 сек) — адаптация под автомат: 10 выстрелов/сек проходят.
     */
    private static final int MAX_NO_DAMAGE_TICKS = 2;

    private static final double BULLET_RANGE = 60.0;
    private static final double BULLET_HITBOX = 0.3;
    private static final double MIN_ENTITY_SEARCH_DISTANCE = 2.0;

    /** Сила отбрасывания цели при попадании. 0 = отключить. */
    private static final double TARGET_KNOCKBACK = 0.35;

    // ==================== НАСТРОЙКИ ТРАССЕРА ===================
    private static final boolean TRACER_ENABLED = true;
    private static final double TRACER_STEP = 3.0;
    private static final double TRACER_MAX_DISTANCE = 30.0;
    private static final Particle TRACER_PARTICLE = Particle.END_ROD;

    // ==================== ИНТЕРВАЛЫ ОБНОВЛЕНИЙ =================
    private static final long HUD_INTERVAL_TICKS = 5L;
    private static final long LORE_INTERVAL_TICKS = 20L;

    // ==================== ВНУТРЕННИЕ ПОЛЯ ======================
    private final Ak74Plugin plugin;
    private final WeaponManager wm;
    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Set<UUID> ourDamage = new HashSet<>();

    public WeaponListener(Ak74Plugin plugin) {
        this.plugin = plugin;
        this.wm = plugin.getWeaponManager();
    }

    // =====================================================================
    //  1. СТРЕЛЬБА (УДЕРЖАНИЕ ЛКМ)
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!wm.isAk74(item)) return;

        // Rate-of-fire
        long now = System.currentTimeMillis();
        Long last = lastShot.get(player.getUniqueId());
        if (last != null && (now - last) < FIRE_INTERVAL_MS) return;
        lastShot.put(player.getUniqueId(), now);

        shoot(player, item);
    }

    // =====================================================================
    //  2. ПКМ — ПЕРЕЗАРЯДКА
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!wm.isAk74(item)) return;

        event.setCancelled(true);
        reload(player, item);
    }

    // =====================================================================
    //  3. ЗАЩИТА ОТ НЕЖЕЛАТЕЛЬНЫХ ДЕЙСТВИЙ
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (wm.isAk74(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player player)) return;

        // Пропускаем урон от плагина — иначе самоблокировка
        if (ourDamage.contains(player.getUniqueId())) return;

        if (wm.isAk74(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (wm.isAk74(event.getMainHandItem()) || wm.isAk74(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // =====================================================================
    //  4. ЛОГИКА ВЫСТРЕЛА (с бесконечными патронами в креативе)
    // =====================================================================
    private void shoot(Player player, ItemStack gun) {
        // ★ В креативе патроны не тратятся
        boolean creative = (player.getGameMode() == GameMode.CREATIVE);

        int newInMag = -1;
        if (!creative) {
            newInMag = wm.consumeOneRound(gun);
        } else {
            newInMag = wm.getAmmoInMag(gun);
        }

        if (newInMag < 0) {
            // Магазин пуст (только в выживании)
            player.sendActionBar(Component.text("Магазин пуст! ПКМ — перезарядка")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            return;
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        // Звук выстрела
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 2.0f);

        // Дульная вспышка
        Location muzzle = eye.clone().add(dir.clone().multiply(0.5));
        player.getWorld().spawnParticle(Particle.CRIT, muzzle, MUZZLE_PARTICLES, 0.1, 0.1, 0.1, 0.01);

        // Урон + трасса
        applyBulletDamage(player, eye, dir);
    }

    // =====================================================================
    //  5. УРОН ОТ ПУЛИ + ТРАССА
    // =====================================================================
    private void applyBulletDamage(Player shooter, Location eye, Vector dir) {
        World world = shooter.getWorld();

        // --- 1. Трассировка блока ---
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, BULLET_RANGE);
        double blockDist = BULLET_RANGE;
        if (blockHit != null && blockHit.getHitPosition() != null) {
            blockDist = blockHit.getHitPosition().distance(eye.toVector());
        }

        // --- 2. Трассировка сущности ---
        LivingEntity target = null;
        double entityDist = BULLET_RANGE;

        if (blockDist > MIN_ENTITY_SEARCH_DISTANCE) {
            RayTraceResult entityHit = world.rayTraceEntities(
                    eye, dir, Math.min(BULLET_RANGE, blockDist), BULLET_HITBOX,
                    e -> !e.equals(shooter) && e instanceof LivingEntity && !e.isDead()
            );
            if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity le) {
                target = le;
                entityDist = entityHit.getHitPosition().distance(eye.toVector());
            }
        }

        double hitDistance = Math.min(blockDist, entityDist);

        // --- 3. Трасса ---
        if (TRACER_ENABLED) {
            drawTracer(world, eye, dir, hitDistance);
        }

        // --- 4. Урон ---
        if (target != null && entityDist <= blockDist) {
            double damage = BULLET_DAMAGE;
            if (target instanceof Monster) damage *= MOB_DAMAGE_MULTIPLIER;

            // Помечаем стрелка — чтобы onMelee пропустил наше событие
            UUID shooterId = shooter.getUniqueId();
            ourDamage.add(shooterId);
            try {
                target.setMaximumNoDamageTicks(MAX_NO_DAMAGE_TICKS);
                if (target.getNoDamageTicks() > MAX_NO_DAMAGE_TICKS) {
                    target.setNoDamageTicks(MAX_NO_DAMAGE_TICKS);
                }
                target.damage(damage, shooter);
            } finally {
                ourDamage.remove(shooterId);
            }

            // Визуальные эффекты
            Location hitLoc = target.getLocation().add(0, target.getHeight() / 2, 0);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 6, 0.3, 0.3, 0.3, 0.05);
            world.playSound(hitLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.5f);

            // Нокбэк цели (не стрелка)
            if (TARGET_KNOCKBACK > 0) {
                Vector knock = dir.clone().normalize().multiply(TARGET_KNOCKBACK).setY(0.15);
                target.setVelocity(target.getVelocity().add(knock));
            }

        } else if (blockHit != null && blockHit.getHitPosition() != null) {
            // Попадание в блок — искра
            Location impact = blockHit.getHitPosition().toLocation(world);
            world.spawnParticle(Particle.CRIT, impact, 3, 0.05, 0.05, 0.05, 0.01);
        }
    }

    // =====================================================================
    //  6. ЛЁГКАЯ ТРАССА ПУЛИ
    // =====================================================================
    private void drawTracer(World world, Location eye, Vector dir, double distance) {
        double max = Math.min(distance, TRACER_MAX_DISTANCE);
        if (max <= TRACER_STEP) return;

        Vector step = dir.clone().normalize().multiply(TRACER_STEP);
        Location point = eye.clone();

        for (double d = TRACER_STEP; d < max; d += TRACER_STEP) {
            point.add(step);
            world.spawnParticle(TRACER_PARTICLE, point, 1, 0, 0, 0, 0);
        }
    }

    // =====================================================================
    //  7. ПЕРЕЗАРЯДКА (в креативе патроны из воздуха)
    // =====================================================================
    private void reload(Player player, ItemStack gun) {
        int inMag = wm.getAmmoInMag(gun);
        int reserve = wm.getAmmoReserve(gun);

        if (inMag >= WeaponManager.MAG_SIZE) {
            player.sendActionBar(Component.text("Магазин уже полон (30/30)")
                    .color(NamedTextColor.YELLOW));
            return;
        }

        // ★ Режим креатива: заполняем магазин «из воздуха»
        if (player.getGameMode() == GameMode.CREATIVE) {
            wm.setAmmo(gun, WeaponManager.MAG_SIZE, reserve);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 0.8f);
            player.sendActionBar(Component.text("Перезарядка... " + WeaponManager.MAG_SIZE + "/30")
                    .color(NamedTextColor.GREEN));
            updateActionBar(player, gun);
            return;
        }

        // Обычная логика для выживания / приключения
        int pulled = consumeAmmoFromInventory(player, WeaponManager.MAX_RESERVE - reserve);
        reserve += pulled;

        if (reserve <= 0) {
            player.sendActionBar(Component.text("Нет патронов 5.45×39!")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            return;
        }

        int need = WeaponManager.MAG_SIZE - inMag;
        int take = Math.min(need, reserve);
        int newInMag = inMag + take;
        int newReserve = reserve - take;

        wm.setAmmo(gun, newInMag, newReserve);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 0.8f);
        player.sendActionBar(Component.text("Перезарядка... " + newInMag + "/30")
                .color(NamedTextColor.GREEN));

        updateActionBar(player, gun);
    }

    // =====================================================================
    //  8. ФОНОВЫЙ ТАСК: HUD + ЛОР
    // =====================================================================
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (wm.isAk74(item)) {
                    updateActionBar(player, item);
                    wm.updateLore(item);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    public void startActionBarTask() {
        new BukkitRunnable() {
            private long tick = 0;

            @Override
            public void run() {
                tick++;
                boolean doLore = (tick % (LORE_INTERVAL_TICKS / HUD_INTERVAL_TICKS)) == 0;

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (!wm.isAk74(item)) continue;

                    updateActionBar(player, item);
                    if (doLore) wm.updateLore(item);
                }
            }
        }.runTaskTimer(plugin, 0L, HUD_INTERVAL_TICKS);
    }

    private void updateActionBar(Player player, ItemStack gun) {
        int[] ammo = wm.readAmmo(gun);
        int inMag = ammo[0];
        int reserve = ammo[1];

        // В креативе показываем ∞ вместо запаса
        Component bar = Component.text("AK-74  ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("[" + inMag + "/" + WeaponManager.MAG_SIZE + "]")
                        .color(inMag > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (player.getGameMode() == GameMode.CREATIVE) {
            bar = bar.append(Component.text("  Запас: ∞")
                    .color(NamedTextColor.AQUA));
        } else {
            bar = bar.append(Component.text("  Запас: " + reserve)
                    .color(NamedTextColor.YELLOW));
        }

        player.sendActionBar(bar);
    }

    // =====================================================================
    //  9. ПОДБОР ПАТРОНОВ ИЗ ИНВЕНТАРЯ В ЗАПАС
    // =====================================================================
    private int consumeAmmoFromInventory(Player player, int maxAmount) {
        if (maxAmount <= 0) return 0;

        int collected = 0;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (collected >= maxAmount) break;
            if (wm.isAmmo(invItem)) {
                int amount = invItem.getAmount();
                if (collected + amount <= maxAmount) {
                    collected += amount;
                    invItem.setAmount(0);
                } else {
                    int need = maxAmount - collected;
                    invItem.setAmount(amount - need);
                    collected += need;
                }
            }
        }
        return collected;
    }

    // =====================================================================
    //  10. ОЧИСТКА ПРИ ВЫХОДЕ
    // =====================================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastShot.remove(event.getPlayer().getUniqueId());
        ourDamage.remove(event.getPlayer().getUniqueId());
    }
}