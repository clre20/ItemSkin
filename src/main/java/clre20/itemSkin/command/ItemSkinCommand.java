package clre20.itemSkin.command;

import clre20.itemSkin.ItemSkin;
import clre20.itemSkin.pack.PackBuilder;
import clre20.itemSkin.skin.Skin;
import clre20.itemSkin.skin.SkinType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /itemskin（別名 /items）。 */
public final class ItemSkinCommand implements TabExecutor {

    private enum Sub {
        HELP("help", "itemskin.help"),
        OPEN("open", "itemskin.use"),
        CHECK("check", "itemskin.check"),
        PACK("pack", "itemskin.pack"),
        RELOAD("reload", "itemskin.reload"),
        VERSION("version", "itemskin.version");

        final String name;
        final String permission;

        Sub(String name, String permission) {
            this.name = name;
            this.permission = permission;
        }

        static Sub of(String s) {
            String lower = s.toLowerCase(Locale.ROOT);
            for (Sub sub : values()) {
                if (sub.name.equals(lower)) {
                    return sub;
                }
            }
            return null;
        }
    }

    private final ItemSkin plugin;

    public ItemSkinCommand(ItemSkin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        // 沒有參數：玩家開介面，主控台顯示說明
        Sub sub;
        if (args.length == 0) {
            sub = sender instanceof Player && sender.hasPermission(Sub.OPEN.permission) ? Sub.OPEN : Sub.HELP;
        } else {
            sub = Sub.of(args[0]);
        }
        if (sub == null) {
            plugin.messages().send(sender, "command.unknown", "label", label);
            return true;
        }
        if (!sender.hasPermission(sub.permission)) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        switch (sub) {
            case HELP -> help(sender, label);
            case OPEN -> {
                if (sender instanceof Player p) {
                    plugin.gui().openEditor(p);
                } else {
                    plugin.messages().send(sender, "command.player-only");
                }
            }
            case CHECK -> {
                if (sender instanceof Player p) {
                    check(p, label);
                } else {
                    plugin.messages().send(sender, "command.player-only");
                }
            }
            case PACK -> {
                if (sender instanceof Player p) {
                    plugin.gui().sendPack(p);
                } else {
                    plugin.messages().send(sender, "command.player-only");
                }
            }
            case RELOAD -> reload(sender);
            case VERSION -> plugin.updateNotifier().showTo(sender);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        plugin.messages().raw(sender, "help.header", "version", plugin.getPluginMeta().getVersion());
        for (Sub sub : Sub.values()) {
            if (sender.hasPermission(sub.permission)) {
                plugin.messages().raw(sender, "help.line", "label", label, "sub", sub.name,
                        "desc", plugin.messages().get("help." + sub.name));
            }
        }
    }

    private void check(Player player, String label) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            plugin.messages().send(player, "check.empty-hand");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        plugin.messages().raw(player, "check.header");
        String id = plugin.items().skinId(meta);
        if (id != null) {
            Skin skin = plugin.packs().library().get(id);
            String type = id.startsWith("3d/") ? "gui.type-3d" : "gui.type-2d";
            plugin.messages().raw(player, "check.skin", "skin", id, "type", plugin.messages().get(type));
            if (skin == null) {
                plugin.messages().raw(player, "check.skin-missing");
            }
        } else if (plugin.items().hasForeignModel(meta)) {
            plugin.messages().raw(player, "check.skin-other", "model", meta.getItemModel().toString());
        } else {
            plugin.messages().raw(player, "check.skin-none");
        }
        plugin.messages().raw(player, plugin.items().hasTag(meta) ? "check.tag-yes" : "check.tag-no");
        plugin.messages().raw(player, "check.pack-line",
                "status", plugin.messages().get(plugin.packs().statusKey(player)));
        if (!"pack.status-loaded".equals(plugin.packs().statusKey(player)) && player.hasPermission(Sub.PACK.permission)) {
            plugin.messages().raw(player, "check.pack-hint", "label", label);
        }
    }

    private void reload(CommandSender sender) {
        if (plugin.packs().building()) {
            plugin.messages().send(sender, "command.reload-busy");
            return;
        }
        plugin.loadFiles();
        plugin.messages().send(sender, "command.reloading");
        plugin.packs().rebuild(r -> {
            plugin.gui().refreshOpen();
            if (sender instanceof Player p && !p.isOnline()) {
                return;
            }
            plugin.messages().send(sender, "command.reloaded",
                    "skins", r.library().size(),
                    "count2d", r.library().count(SkinType.TWO_D),
                    "count3d", r.library().count(SkinType.THREE_D),
                    "skipped", r.skipped().size(),
                    "size", r.zip() == null ? "-" : PackBuilder.size(r.zip().length));
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Sub sub : Sub.values()) {
                if (sender.hasPermission(sub.permission) && sub.name.startsWith(prefix)) {
                    out.add(sub.name);
                }
            }
        }
        return out;
    }
}
