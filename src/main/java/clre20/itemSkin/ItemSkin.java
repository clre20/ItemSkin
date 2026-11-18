package clre20.itemSkin;

import clre20.itemSkin.command.ItemSkinCommand;
import clre20.itemSkin.config.Messages;
import clre20.itemSkin.config.PluginConfig;
import clre20.itemSkin.gui.GuiManager;
import clre20.itemSkin.pack.PackService;
import clre20.itemSkin.skin.SkinItems;
import clre20.itemSkin.update.UpdateChecker;
import clre20.itemSkin.update.UpdateNotifier;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemSkin extends JavaPlugin {

    private PluginConfig config;
    private Messages messages;
    private SkinItems items;
    private PackService packs;
    private GuiManager gui;
    private UpdateChecker updates;
    private UpdateNotifier updateNotifier;

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        messages = new Messages(this);
        loadFiles();

        items = new SkinItems(this);
        packs = new PackService(this);
        gui = new GuiManager(this);
        updates = new UpdateChecker(getPluginMeta().getVersion());
        updateNotifier = new UpdateNotifier(this);

        getServer().getPluginManager().registerEvents(packs, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(updateNotifier, this);

        PluginCommand command = getCommand("itemskin");
        if (command != null) {
            ItemSkinCommand executor = new ItemSkinCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        packs.connectHost();
        packs.rebuild(null);
        updateNotifier.checkOnStartup();
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.shutdown();
        }
        if (packs != null) {
            packs.shutdown();
        }
        if (updates != null) {
            updates.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    /** 讀取（並自動補齊）config.yml 與 messages.yml。 */
    public void loadFiles() {
        config.load();
        messages.load();
        if (config.lastFilled() > 0) {
            getLogger().info(messages.console("config-filled", "count", config.lastFilled()));
        }
        if (messages.lastFilled() > 0) {
            getLogger().info(messages.console("messages-filled", "count", messages.lastFilled()));
        }
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public SkinItems items() {
        return items;
    }

    public PackService packs() {
        return packs;
    }

    public GuiManager gui() {
        return gui;
    }

    public UpdateChecker updates() {
        return updates;
    }

    public UpdateNotifier updateNotifier() {
        return updateNotifier;
    }
}
