package io.github.faustyu.hotpotato;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

public class HotPotato extends JavaPlugin implements Listener {

    private boolean gameActive = false;
    private Player currentPotatoHolder = null;
    private int INITIAL_TIMER_SECONDS = 30;
    private int timerTaskId = -1;
    private int secondsLeft = INITIAL_TIMER_SECONDS;
    private final double NEARBY_RANGE = 5.0; // Радиус для определения близких игроков
    private Location gameStartLocation;
    private Location deathLocation;
    private int roundCount = 0;
    private double timerDecrement = 0.5; // Уменьшение таймера на 0.5 секунды с каждым раундом
    private List<Player> initialPlayers = new ArrayList<>();
    private List<Player> survivingPlayers = new ArrayList<>();
    private int initialPlayerCount;
    private Player winner;

    private Scoreboard scoreboard;
    private Team potatoHolderTeam;
    private Team survivingPlayersTeam;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLocations();
        setupScoreboard();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("hotpotato").setExecutor(new HotPotatoCommand(this));
        getLogger().info("Hot Potato plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        stopGame();
        getLogger().info("Hot Potato plugin has been disabled!");
    }

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        // Создаем команду для игрока с картошкой (красное свечение)
        potatoHolderTeam = scoreboard.registerNewTeam("potatoHolder");
        potatoHolderTeam.setColor(ChatColor.RED);
        potatoHolderTeam.setAllowFriendlyFire(true);
        potatoHolderTeam.setCanSeeFriendlyInvisibles(false);

        // Создаем команду для выживших (зеленое свечение)
        survivingPlayersTeam = scoreboard.registerNewTeam("survivors");
        survivingPlayersTeam.setColor(ChatColor.GREEN);
        survivingPlayersTeam.setAllowFriendlyFire(false);
        survivingPlayersTeam.setCanSeeFriendlyInvisibles(false);

        // Устанавливаем опцию свечения и цвет свечения
        potatoHolderTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        survivingPlayersTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        try {
            // В новых версиях Minecraft (1.9+)
            potatoHolderTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
            survivingPlayersTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        } catch (Exception e) {
            // Игнорируем ошибки в старых версиях
        }

        try {
            // Задаем цвета свечения
            potatoHolderTeam.setColor(ChatColor.RED);
            survivingPlayersTeam.setColor(ChatColor.GREEN);
        } catch (Exception e) {
            // В случае, если метод не поддерживается в данной версии
            getLogger().warning("Не удалось установить цвет свечения. Возможно, версия сервера не поддерживает эту функцию.");
        }
    }

    private void loadLocations() {
        FileConfiguration config = getConfig();

        // Загрузка локации для старта игры
        if (config.contains("locations.start")) {
            World world = Bukkit.getWorld(config.getString("locations.start.world", "world"));
            double x = config.getDouble("locations.start.x", 0);
            double y = config.getDouble("locations.start.y", 64);
            double z = config.getDouble("locations.start.z", 0);
            float yaw = (float) config.getDouble("locations.start.yaw", 0);
            float pitch = (float) config.getDouble("locations.start.pitch", 0);

            if (world != null) {
                gameStartLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }

        // Загрузка локации для проигравших
        if (config.contains("locations.death")) {
            World world = Bukkit.getWorld(config.getString("locations.death.world", "world"));
            double x = config.getDouble("locations.death.x", 0);
            double y = config.getDouble("locations.death.y", 64);
            double z = config.getDouble("locations.death.z", 0);
            float yaw = (float) config.getDouble("locations.death.yaw", 0);
            float pitch = (float) config.getDouble("locations.death.pitch", 0);

            if (world != null) {
                deathLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    public void setStartLocation(Location location) {
        gameStartLocation = location;

        // Сохранение в конфиг
        FileConfiguration config = getConfig();
        config.set("locations.start.world", location.getWorld().getName());
        config.set("locations.start.x", location.getX());
        config.set("locations.start.y", location.getY());
        config.set("locations.start.z", location.getZ());
        config.set("locations.start.yaw", location.getYaw());
        config.set("locations.start.pitch", location.getPitch());
        saveConfig();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Стартовая локация для игры 'Горячая картошка' установлена!");
    }

    public void setDeathLocation(Location location) {
        deathLocation = location;

        // Сохранение в конфиг
        FileConfiguration config = getConfig();
        config.set("locations.death.world", location.getWorld().getName());
        config.set("locations.death.x", location.getX());
        config.set("locations.death.y", location.getY());
        config.set("locations.death.z", location.getZ());
        config.set("locations.death.yaw", location.getYaw());
        config.set("locations.death.pitch", location.getPitch());
        saveConfig();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Локация для проигравших установлена!");
    }

    public void startGame() {
        if (gameActive) {
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            Bukkit.broadcastMessage(ChatColor.RED + "Нужно как минимум 2 игрока для начала игры!");
            return;
        }

        if (gameStartLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Стартовая локация не установлена! Используйте /hotpotato setstart");
            return;
        }

        if (deathLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Локация для проигравших не установлена! Используйте /hotpotato setdeath");
            return;
        }

        // Сохраняем изначальное количество игроков
        initialPlayers = new ArrayList<>(players);
        survivingPlayers = new ArrayList<>(players);

        // Телепортировать всех игроков на стартовую позицию
        for (Player player : players) {
            // Очищаем инвентарь перед началом игры
            player.getInventory().clear();
            
            player.teleport(gameStartLocation);
            player.setGameMode(GameMode.ADVENTURE);
            player.setGlowing(false);
            
            // Устанавливаем максимальное здоровье и сытость
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            
            // Добавляем эффекты
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            
            // Отключаем PvP
            player.setNoDamageTicks(Integer.MAX_VALUE);
            
            player.setScoreboard(scoreboard);
            survivingPlayersTeam.addEntry(player.getName());
        }

        // Отключаем PvP для всех игроков
        Bukkit.getWorlds().forEach(world -> world.setPVP(false));

        gameActive = true;
        roundCount = 0;
        secondsLeft = INITIAL_TIMER_SECONDS;

        // Запустить обратный отсчет перед началом игры
        Bukkit.broadcastMessage(ChatColor.GREEN + "Игра 'Горячая картошка' начнется через 15 секунд!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Всего игроков: " + initialPlayers.size());

        new BukkitRunnable() {
            int countdown = 15;

            @Override
            public void run() {
                if (countdown <= 0) {
                    // Начать игру
                    startRound();
                    cancel();
                } else {
                    if (countdown <= 5 || countdown == 10 || countdown == 15) {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Игра начнется через " + countdown + " секунд!");

                        // Отправить титул всем игрокам
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendTitle(
                                    ChatColor.GOLD + "Подготовка",
                                    ChatColor.YELLOW + "Старт через " + countdown + " сек",
                                    5, 10, 5
                                    
                            );
                        }
                    }
                    countdown--;
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startRound() {
        // Отменить предыдущий таймер, если он существует
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
        }

        List<Player> activePlayers = getSurvivingPlayers();
        roundCount++;

        // Расчет нового времени таймера с учетом уменьшения
        double timerSeconds = INITIAL_TIMER_SECONDS - (roundCount - 1) * timerDecrement;
        if (timerSeconds < 5) timerSeconds = 5; // Минимум 5 секунд
        secondsLeft = (int) Math.ceil(timerSeconds);

        // Выбрать случайного игрока для начала
        int randomIndex = (int) (Math.random() * activePlayers.size());
        currentPotatoHolder = activePlayers.get(randomIndex);
        givePotato(currentPotatoHolder);

        // Установить красное свечение для игрока с картошкой
        setGlowingEffect(currentPotatoHolder);

        // Запустить таймер
        startTimer();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Раунд " + roundCount + " начался! У " +
                currentPotatoHolder.getName() + " горячая картошка!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Время таймера: " + secondsLeft + " секунд");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Осталось игроков: " + activePlayers.size() + "/" + initialPlayers.size());
    }

    private void startTimer() {
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
        }

        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (!gameActive || currentPotatoHolder == null) {
                    return;
                }

                secondsLeft--;
                
                // Обновляем action bar для всех игроков
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        String message = ChatColor.YELLOW + "Картошка у " + currentPotatoHolder.getName() + 
                                       ChatColor.RED + " | " + ChatColor.YELLOW + "Осталось: " + secondsLeft + " сек";
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
                    }
                }

                if (secondsLeft <= 0) {
                    endRound();
                }
            }
        }, 0L, 20L);
    }

    // Устанавливаем эффект свечения для игрока с картошкой
    private void setGlowingEffect(Player player) {
        // Сначала очищаем команду "potatoHolder"
        for (String entry : potatoHolderTeam.getEntries()) {
            potatoHolderTeam.removeEntry(entry);
        }

        // Сбрасываем свечение всем игрокам
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
        }

        // Добавляем всех выживших в команду выживших
        for (Player p : getSurvivingPlayers()) {
            if (!p.equals(player)) { // Не добавляем текущего держателя картошки
                survivingPlayersTeam.addEntry(p.getName());
                p.setGlowing(true);
            }
        }

        // Добавляем игрока с картошкой в команду и включаем свечение
        potatoHolderTeam.addEntry(player.getName());
        player.setGlowing(true);
    }

    public void stopGame() {
        if (!gameActive) {
            return;
        }

        gameActive = false;
        currentPotatoHolder = null;
        roundCount = 0;

        // Возвращаем всех игроков в режим приключения и телепортируем на стартовую локацию
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            player.setGlowing(false);
            
            // Телепортируем на стартовую локацию
            if (gameStartLocation != null) {
                player.teleport(gameStartLocation);
            }
        }

        // Очищаем команды
        for (String entry : potatoHolderTeam.getEntries()) {
            potatoHolderTeam.removeEntry(entry);
        }
        for (String entry : survivingPlayersTeam.getEntries()) {
            survivingPlayersTeam.removeEntry(entry);
        }

        // Воспроизводим звук победы
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Проверяем, есть ли победитель
        if (winner != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "      ⭐ ПОБЕДИТЕЛЬ! ⭐");
            Bukkit.broadcastMessage(ChatColor.GOLD + "➤ " + ChatColor.YELLOW + winner.getName() + 
                ChatColor.GOLD + " выиграл игру 'Горячая картошка'!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "➤ Выжил последним из " + 
                ChatColor.YELLOW + initialPlayers.size() + ChatColor.GOLD + " игроков!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
        } else {
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "      ⭐ ИГРА ОСТАНОВЛЕНА! ⭐");
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
        }

        // Очищаем списки
        survivingPlayers.clear();
        winner = null;
    }

    private void endRound() {
        if (!gameActive || currentPotatoHolder == null) {
            return;
        }

        // Отменить таймер
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
        }

        // Воспроизводим звук TNT
        currentPotatoHolder.getWorld().playSound(currentPotatoHolder.getLocation(), 
            Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        // Удаляем игрока с картошкой из списка выживших
        survivingPlayers.remove(currentPotatoHolder);
        currentPotatoHolder.setGameMode(GameMode.SPECTATOR);
        currentPotatoHolder.setGlowing(false);
        potatoHolderTeam.removeEntry(currentPotatoHolder.getName());

        // Список игроков, которые выбыли в этом раунде
        List<Player> eliminatedThisRound = new ArrayList<>();
        eliminatedThisRound.add(currentPotatoHolder);

        // Проверяем игроков в радиусе 5 блоков
        for (Player nearbyPlayer : currentPotatoHolder.getWorld().getPlayers()) {
            if (nearbyPlayer != currentPotatoHolder && 
                nearbyPlayer.getLocation().distance(currentPotatoHolder.getLocation()) <= 5) {
                
                // Воспроизводим звук TNT для каждого задетого игрока
                nearbyPlayer.playSound(nearbyPlayer.getLocation(), 
                    Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                
                // Удаляем игрока из списка выживших
                survivingPlayers.remove(nearbyPlayer);
                eliminatedThisRound.add(nearbyPlayer);
                
                // Очищаем инвентарь и эффекты задетого игрока
                nearbyPlayer.getInventory().clear();
                nearbyPlayer.removePotionEffect(PotionEffectType.SPEED);
                nearbyPlayer.removePotionEffect(PotionEffectType.JUMP_BOOST);
                
                // Переводим в режим наблюдателя и отключаем свечение
                nearbyPlayer.setGameMode(GameMode.SPECTATOR);
                nearbyPlayer.setGlowing(false);
                survivingPlayersTeam.removeEntry(nearbyPlayer.getName());
                
                Bukkit.broadcastMessage(ChatColor.DARK_RED + nearbyPlayer.getName() + 
                    ChatColor.RED + " был задет взрывом и выбыл из игры!");
            }
        }
        
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "Время вышло! " + 
            ChatColor.RED + currentPotatoHolder.getName() + 
            ChatColor.DARK_RED + " выбыл из игры!");

        // Проверяем, остались ли выжившие
        if (survivingPlayers.isEmpty()) {
            gameActive = false;
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "      ⭐ НИЧЬЯ! ⭐");
            Bukkit.broadcastMessage(ChatColor.GOLD + "➤ Все игроки выбыли одновременно!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
            
            // Воспроизводим звук ничьей
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            return;
        }

        // Проверяем, остался ли один игрок
        if (survivingPlayers.size() == 1) {
            winner = survivingPlayers.get(0);
            stopGame();
            return;
        }

        // Начинаем следующий раунд
        Bukkit.getScheduler().runTaskLater(this, this::startRound, 100L);
    }

    private void announceWinner(Player winner) {
        // Отправляем сообщение о победителе в чат
        Bukkit.broadcastMessage(ChatColor.GOLD + "ИГРА ОКОНЧЕНА! " + 
                ChatColor.GREEN + "Победитель: " + winner.getName());

        // Запустить фейерверки вокруг победителя
        launchFireworks(winner);
    }

    private void launchFireworks(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Запускаем несколько фейерверков с разными цветами
        for (int i = 0; i < 5; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Создаем фейерверк в локации игрока
                    Firework fw = world.spawn(loc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();

                    // Случайный тип фейерверка
                    FireworkEffect.Type[] types = FireworkEffect.Type.values();
                    FireworkEffect.Type type = types[(int) (Math.random() * types.length)];

                    // Случайные цвета
                    int r = (int) (Math.random() * 255);
                    int g = (int) (Math.random() * 255);
                    int b = (int) (Math.random() * 255);

                    FireworkEffect effect = FireworkEffect.builder()
                            .withColor(org.bukkit.Color.fromRGB(r, g, b))
                            .withFade(org.bukkit.Color.fromRGB(255-r, 255-g, 255-b))
                            .with(type)
                            .trail(true)
                            .flicker(true)
                            .build();

                    meta.addEffect(effect);
                    meta.setPower(1);  // Высота полета
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(this, i * 5L);  // Запускаем с интервалом в 5 тиков
        }
    }

    private List<Player> getSurvivingPlayers() {
        List<Player> survivors = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                survivors.add(player);
            }
        }
        return survivors;
    }

    private void givePotato(Player player) {
        // Создать "горячую картошку"
        ItemStack hotPotato = new ItemStack(Material.BAKED_POTATO);
        ItemMeta meta = hotPotato.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "Горячая картошка");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Передай эту картошку другому игроку!");
        lore.add(ChatColor.RED + "У тебя " + secondsLeft + " секунд!");
        meta.setLore(lore);
        hotPotato.setItemMeta(meta);

        // Дать картошку игроку
        player.getInventory().setItem(0, hotPotato);
        
        // Добавляем эффекты скорости и высокого прыжка
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false));
        
        // Воспроизводим звук получения картошки
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        player.sendMessage(ChatColor.DARK_RED + "Вам дали горячую картошку! У вас " +
                ChatColor.RED + secondsLeft + ChatColor.DARK_RED + " секунд, чтобы передать её!");
    }

    private void removePotato(Player player) {
        // Удалить все картошки из инвентаря игрока
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.BAKED_POTATO) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Удаляем эффекты скорости и высокого прыжка
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!gameActive) {
            return;
        }

        Player player = event.getPlayer();

        if (player.equals(currentPotatoHolder)) {
            if (event.getRightClicked() instanceof Player) {
                Player target = (Player) event.getRightClicked();

                if (target.getGameMode() != GameMode.SPECTATOR) {
                    // Проверяем, что картошка в главной руке
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (mainHand.getType() != Material.BAKED_POTATO) {
                        player.sendMessage(ChatColor.RED + "Вы должны держать горячую картошку в главной руке!");
                        return;
                    }

                    player.setGlowing(false);
                    if (potatoHolderTeam.hasEntry(player.getName())) {
                        potatoHolderTeam.removeEntry(player.getName());
                    }
                    survivingPlayersTeam.addEntry(player.getName());
                    player.setGlowing(true);

                    removePotato(player);
                    currentPotatoHolder = target;
                    givePotato(target);

                    setGlowingEffect(target);

                    // Отправляем сообщение в чат только о передаче картошки
                    Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() +
                            " передал горячую картошку игроку " + target.getName() + "!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!gameActive) return;
        
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (droppedItem.getType() == Material.BAKED_POTATO) {
            event.setCancelled(true);
            // Воспроизводим звук ошибки
            event.getPlayer().playSound(event.getPlayer().getLocation(), 
                Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            event.getPlayer().sendMessage(ChatColor.RED + "Нельзя выкидывать горячую картошку!");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (player.equals(currentPotatoHolder)) {
            // Проверяем, что игрок пытается положить картошку в контейнер
            if (event.getClickedBlock() != null) {
                Material clickedType = event.getClickedBlock().getType();
                if (clickedType == Material.CHEST || 
                    clickedType == Material.TRAPPED_CHEST ||
                    clickedType == Material.BARREL ||
                    clickedType == Material.FURNACE ||
                    clickedType == Material.BLAST_FURNACE ||
                    clickedType == Material.SMOKER ||
                    clickedType == Material.HOPPER ||
                    clickedType == Material.DROPPER ||
                    clickedType == Material.DISPENSER ||
                    clickedType == Material.SHULKER_BOX ||
                    clickedType == Material.WHITE_SHULKER_BOX ||
                    clickedType == Material.ORANGE_SHULKER_BOX ||
                    clickedType == Material.MAGENTA_SHULKER_BOX ||
                    clickedType == Material.LIGHT_BLUE_SHULKER_BOX ||
                    clickedType == Material.YELLOW_SHULKER_BOX ||
                    clickedType == Material.LIME_SHULKER_BOX ||
                    clickedType == Material.PINK_SHULKER_BOX ||
                    clickedType == Material.GRAY_SHULKER_BOX ||
                    clickedType == Material.LIGHT_GRAY_SHULKER_BOX ||
                    clickedType == Material.CYAN_SHULKER_BOX ||
                    clickedType == Material.PURPLE_SHULKER_BOX ||
                    clickedType == Material.BLUE_SHULKER_BOX ||
                    clickedType == Material.BROWN_SHULKER_BOX ||
                    clickedType == Material.GREEN_SHULKER_BOX ||
                    clickedType == Material.RED_SHULKER_BOX ||
                    clickedType == Material.BLACK_SHULKER_BOX) {
                    
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Нельзя положить горячую картошку в контейнер!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                }
            }
        }
    }

    public void startFastGame() {
        if (gameActive) {
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            Bukkit.broadcastMessage(ChatColor.RED + "Нужно как минимум 2 игрока для начала игры!");
            return;
        }

        if (gameStartLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Стартовая локация не установлена! Используйте /hotpotato setstart");
            return;
        }

        if (deathLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Локация для проигравших не установлена! Используйте /hotpotato setdeath");
            return;
        }

        // Сохраняем изначальное количество игроков
        initialPlayers = new ArrayList<>(players);
        initialPlayerCount = players.size();
        survivingPlayers = new ArrayList<>(players);

        // Телепортировать всех игроков на стартовую позицию
        for (Player player : players) {
            player.getInventory().clear();
            player.teleport(gameStartLocation);
            player.setGameMode(GameMode.ADVENTURE);
            player.setGlowing(false);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            player.setNoDamageTicks(Integer.MAX_VALUE);
            player.setScoreboard(scoreboard);
            survivingPlayersTeam.addEntry(player.getName());
        }

        Bukkit.getWorlds().forEach(world -> world.setPVP(false));

        gameActive = true;
        roundCount = 0;
        secondsLeft = 5; // Уменьшенное время для тестирования

        // Быстрый обратный отсчет (3 секунды)
        Bukkit.broadcastMessage(ChatColor.GREEN + "Быстрая игра 'Горячая картошка' начнется через 3 секунды!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Всего игроков: " + initialPlayers.size());

        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (countdown <= 0) {
                    startRound();
                    cancel();
                } else {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Игра начнется через " + countdown + " секунд!");
                    countdown--;
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }
}