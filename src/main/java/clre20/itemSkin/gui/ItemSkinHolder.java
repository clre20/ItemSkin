package clre20.itemSkin.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * ItemSkin 所有箱子介面的 InventoryHolder（插件簽署）。
 * 其他插件可用 {@code holder instanceof ItemSkinHolder} 或比對類別名稱
 * {@code clre20.itemSkin.gui.ItemSkinHolder}，並用 {@link #plugin()} 確認來源。
 * 介面上的按鈕另外帶有 PDC 標籤 itemskin:gui。
 */
public final class ItemSkinHolder implements InventoryHolder {

    public enum Type { EDITOR, PICKER }

    private final Plugin plugin;
    private final Type type;
    private final UUID viewer;
    private Inventory inventory;

    ItemSkinHolder(Plugin plugin, Type type, UUID viewer) {
        this.plugin = plugin;
        this.type = type;
        this.viewer = viewer;
    }

    Inventory create(int size, net.kyori.adventure.text.Component title) {
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    public Plugin plugin() {
        return plugin;
    }

    public Type type() {
        return type;
    }

    public UUID viewer() {
        return viewer;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
