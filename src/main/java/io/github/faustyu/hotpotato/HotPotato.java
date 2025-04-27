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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        // Телепортировать всех игроков на стартовую позицию
        for (Player player : players) {
            player.teleport(gameStartLocation);
            player.setGameMode(GameMode.ADVENTURE);
            player.setGlowing(false);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.setScoreboard(scoreboard);
            survivingPlayersTeam.addEntry(player.getName());
        }

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
        timerTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    endRound();
                } else {
                    // Обновить title для всех игроков
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.equals(currentPotatoHolder)) {
                            player.sendTitle(
                                    ChatColor.RED + "У ВАС ГОРЯЧАЯ КАРТОШКА!",
                                    ChatColor.YELLOW + "Осталось: " + secondsLeft + " сек",
                                    5, 10, 5
                            );
                        } else {
                            player.sendTitle(
                                    ChatColor.GREEN + "Горячая картошка у: " + currentPotatoHolder.getName(),
                                    ChatColor.YELLOW + "Осталось: " + secondsLeft + " сек",
                                    5, 10, 5
                            );
                        }
                    }
                    secondsLeft--;
                }
            }
        }.runTaskTimer(this, 0L, 20L).getTaskId();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Раунд " + roundCount + " начался! У " +
                currentPotatoHolder.getName() + " горячая картошка!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Время таймера: " + secondsLeft + " секунд");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Осталось игроков: " + activePlayers.size() + "/" + initialPlayers.size());
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

        // Добавляем игрока с картошкой в команду и включаем свечение
        potatoHolderTeam.addEntry(player.getName());
        player.setGlowing(true);
    }

    public void stopGame() {
        if (!gameActive) {
            return;
        }

        // Отменить таймер
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
        }

        // Удалить картошку у текущего держателя
        if (currentPotatoHolder != null) {
            removePotato(currentPotatoHolder);
        }

        // Очищаем команды
        for (String entry : potatoHolderTeam.getEntries()) {
            potatoHolderTeam.removeEntry(entry);
        }
        for (String entry : survivingPlayersTeam.getEntries()) {
            survivingPlayersTeam.removeEntry(entry);
        }

        // Сбрасываем эффекты у всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGlowing(false);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.setGameMode(GameMode.SURVIVAL);
        }

        gameActive = false;
        currentPotatoHolder = null;
        initialPlayers.clear();
        Bukkit.broadcastMessage(ChatColor.RED + "Игра 'Горячая картошка' остановлена!");
    }

    private void endRound() {
        if (!gameActive || currentPotatoHolder == null) {
            return;
        }

        Bukkit.broadcastMessage(ChatColor.RED + "Время вышло! " + currentPotatoHolder.getName() +
                " взорвался с горячей картошкой!");

        // Взрыв (только визуальный эффект)
        currentPotatoHolder.getWorld().createExplosion(
                currentPotatoHolder.getLocation(),
                0.0F,
                false,
                false
        );

        // Отключаем свечение для проигравшего
        currentPotatoHolder.setGlowing(false);
        if (potatoHolderTeam.hasEntry(currentPotatoHolder.getName())) {
            potatoHolderTeam.removeEntry(currentPotatoHolder.getName());
        }
        if (survivingPlayersTeam.hasEntry(currentPotatoHolder.getName())) {
            survivingPlayersTeam.removeEntry(currentPotatoHolder.getName());
        }

        // Перевести проигравшего в спектатор и телепортировать на локацию смерти
        currentPotatoHolder.setGameMode(GameMode.SPECTATOR);
        currentPotatoHolder.teleport(deathLocation);
        currentPotatoHolder.sendMessage(ChatColor.RED + "Вы проиграли этот раунд!");

        // Найти и перевести близких игроков в режим спектатора
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(currentPotatoHolder) &&
                    player.getGameMode() != GameMode.SPECTATOR &&
                    player.getLocation().distance(currentPotatoHolder.getLocation()) <= NEARBY_RANGE) {

                // Отключаем свечение для игроков рядом
                player.setGlowing(false);
                if (potatoHolderTeam.hasEntry(player.getName())) {
                    potatoHolderTeam.removeEntry(player.getName());
                }
                if (survivingPlayersTeam.hasEntry(player.getName())) {
                    survivingPlayersTeam.removeEntry(player.getName());
                }

                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(deathLocation);
                player.sendMessage(ChatColor.RED + "Вы были слишком близко к взрыву картошки!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() +
                        " был поблизости и тоже выбывает из игры!");
            }
        }

        // Удалить картошку
        removePotato(currentPotatoHolder);

        // Проверить, остались ли игроки
        List<Player> survivingPlayers = getSurvivingPlayers();

        if (survivingPlayers.size() <= 1) {
            if (survivingPlayers.size() == 1) {
                // Объявить победителя
                Player winner = survivingPlayers.get(0);
                announceWinner(winner);
            } else {
                Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
                Bukkit.broadcastMessage(ChatColor.GOLD + "➤ Игра окончена! Нет победителей.");
                Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
            }
            stopGame();
        } else {
            // Начать новый раунд
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Следующий раунд начнется через 5 секунд!");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Осталось игроков: " + survivingPlayers.size() + "/" + initialPlayers.size());

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (gameActive) {
                        startRound();
                    }
                }
            }.runTaskLater(this, 5 * 20L);
        }
    }

    private void announceWinner(Player winner) {
        // Яркое объявление победителя в чате
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GOLD + "      ⭐ ПОБЕДИТЕЛЬ! ⭐");
        Bukkit.broadcastMessage(ChatColor.GOLD + "➤ " + ChatColor.GREEN + winner.getName() + ChatColor.GOLD + " выиграл игру 'Горячая картошка'!");
        Bukkit.broadcastMessage(ChatColor.GOLD + "➤ Выжил последним из " + initialPlayers.size() + " игроков!");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════════════");

        // Сделать эффектный титул для победителя
        winner.sendTitle(
                ChatColor.GOLD + "ПОБЕДА!",
                ChatColor.YELLOW + "Вы последний выживший!",
                10, 70, 20
        );

        // Сделать титул для всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != winner) {
                player.sendTitle(
                        ChatColor.GOLD + "ИГРА ОКОНЧЕНА",
                        ChatColor.GREEN + "Победитель: " + winner.getName(),
                        10, 70, 20
                );
            }
        }

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
        meta.setDisplayName(ChatColor.RED + "Горячая картошка");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Передай эту картошку другому игроку!");
        lore.add(ChatColor.RED + "У тебя " + secondsLeft + " секунд!");
        meta.setLore(lore);
        hotPotato.setItemMeta(meta);

        // Дать картошку игроку
        player.getInventory().setItem(0, hotPotato);
        player.sendMessage(ChatColor.RED + "Вам дали горячую картошку! У вас " +
                secondsLeft + " секунд, чтобы передать её!");
    }

    private void removePotato(Player player) {
        // Удалить все картошки из инвентаря игрока
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.BAKED_POTATO) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!gameActive) {
            return;
        }

        Player player = event.getPlayer();

        // Проверить, является ли игрок текущим держателем картошки
        if (player.equals(currentPotatoHolder)) {
            // Проверить, является ли цель игроком
            if (event.getRightClicked() instanceof Player) {
                Player target = (Player) event.getRightClicked();

                // Убедиться, что цель не в режиме наблюдателя
                if (target.getGameMode() != GameMode.SPECTATOR) {
                    // Отключить свечение у текущего игрока
                    player.setGlowing(false);
                    if (potatoHolderTeam.hasEntry(player.getName())) {
                        potatoHolderTeam.removeEntry(player.getName());
                    }

                    // Передать картошку
                    removePotato(player);
                    currentPotatoHolder = target;
                    givePotato(target);

                    // Установить свечение для нового держателя картошки
                    setGlowingEffect(target);

                    Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() +
                            " передал горячую картошку игроку " + target.getName() + "!");

                    // Уведомить всех игроков о новом держателе картошки
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(
                                ChatColor.YELLOW + "Картошка у " + target.getName(),
                                ChatColor.YELLOW + "Осталось: " + secondsLeft + " сек",
                                5, 20, 5
                        );
                    }
                }
            }
        }
    }
}

// Класс команды для управления плагином
class HotPotatoCommand implements org.bukkit.command.CommandExecutor {

    private final HotPotato plugin;

    public HotPotatoCommand(HotPotato plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Использование: /hotpotato <start|stop|setstart|setdeath>");
            return false;
        }

        if (args[0].equalsIgnoreCase("start")) {
            plugin.startGame();
            return true;
        } else if (args[0].equalsIgnoreCase("stop")) {
            plugin.stopGame();
            return true;
        } else if (args[0].equalsIgnoreCase("setstart")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда может быть использована только игроком!");
                return false;
            }
            Player player = (Player) sender;
            plugin.setStartLocation(player.getLocation());
            return true;
        } else if (args[0].equalsIgnoreCase("setdeath")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда может быть использована только игроком!");
                return false;
            }
            Player player = (Player) sender;
            plugin.setDeathLocation(player.getLocation());
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Использование: /hotpotato <start|stop|setstart|setdeath>");
            return false;
        }
    }
}