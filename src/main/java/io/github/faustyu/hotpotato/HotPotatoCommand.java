 // ... existing code ...

@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
        sender.sendMessage(ChatColor.RED + "Используйте: /hotpotato <start|stop|setstart|setdeath|fast>");
        return true;
    }

    switch (args[0].toLowerCase()) {
        case "start":
            plugin.startGame();
            break;
        case "stop":
            plugin.stopGame();
            break;
        case "setstart":
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда только для игроков!");
                return true;
            }
            plugin.setStartLocation(((Player) sender).getLocation());
            break;
        case "setdeath":
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда только для игроков!");
                return true;
            }
            plugin.setDeathLocation(((Player) sender).getLocation());
            break;
        case "fast":
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эта команда только для игроков!");
                return true;
            }
            plugin.startFastGame();
            break;
        default:
            sender.sendMessage(ChatColor.RED + "Неизвестная команда!");
            break;
    }
    return true;
}

// ... existing code ...