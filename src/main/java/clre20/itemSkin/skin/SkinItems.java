package clre20.itemSkin.skin;

import clre20.itemSkin.pack.PackBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.Set;

/**
 * 物品上的外觀讀寫。
 * <ul>
 *   <li>外觀：item_model 組件，命名空間 itemskin（例如 itemskin:2d/dragon_sword）</li>
 *   <li>隱藏標籤：PDC itemskin:skin = 外觀 id（看不到，Item-lock 比對時會忽略）</li>
 * </ul>
 * 只在主執行緒使用。
 */
public final class SkinItems {

    private final NamespacedKey tagKey;
    private final Plugin plugin;
    private final Set<Material> tools;

    public SkinItems(Plugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "skin");
        Set<Material> t = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) {
                continue;
            }
            String n = m.name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")
                    || n.endsWith("_HOE") || n.endsWith("_SPEAR")) {
                t.add(m);
            }
        }
        for (String n : new String[]{"TRIDENT", "MACE", "STICK", "BLAZE_ROD", "BREEZE_ROD", "DEBUG_STICK"}) {
            Material m = Material.getMaterial(n);
            if (m != null) {
                t.add(m);
            }
        }
        this.tools = t;
    }

    /** 套用外觀；tag 為 false 時順便移除舊的標籤。 */
    public void apply(ItemStack item, Skin skin, boolean tag, boolean handheldForTools) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean handheld = handheldForTools && tools.contains(item.getType());
        meta.setItemModel(new NamespacedKey(plugin, skin.modelPath(handheld)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (tag) {
            pdc.set(tagKey, PersistentDataType.STRING, skin.id());
        } else {
            pdc.remove(tagKey);
        }
        item.setItemMeta(meta);
    }

    /** 移除 ItemSkin 的外觀與標籤；沒有可移除的東西時回傳 false。其他插件設定的 item_model 不會動。 */
    public boolean remove(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean changed = false;
        if (isOurs(meta)) {
            meta.setItemModel(null);
            changed = true;
        }
        if (meta.getPersistentDataContainer().has(tagKey)) {
            meta.getPersistentDataContainer().remove(tagKey);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    /** 物品上的外觀 id（2d/xxx、3d/xxx）；沒有時回傳 null。優先看 item_model，其次看標籤。 */
    public String skinId(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        if (isOurs(meta)) {
            String key = meta.getItemModel().getKey();
            if (key.startsWith("2d_handheld/")) {
                return "2d/" + key.substring("2d_handheld/".length());
            }
            return key;
        }
        return meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
    }

    public boolean hasTag(ItemMeta meta) {
        return meta != null && meta.getPersistentDataContainer().has(tagKey);
    }

    /** 物品的 item_model 是 ItemSkin 設定的。 */
    public boolean isOurs(ItemMeta meta) {
        return meta != null && meta.hasItemModel()
                && PackBuilder.NAMESPACE.equals(meta.getItemModel().getNamespace());
    }

    /** 物品的 item_model 是其他插件或材質包設定的。 */
    public boolean hasForeignModel(ItemMeta meta) {
        return meta != null && meta.hasItemModel()
                && !PackBuilder.NAMESPACE.equals(meta.getItemModel().getNamespace());
    }

    /** 給介面預覽用：設定 item_model（不加標籤）。 */
    public void preview(ItemMeta meta, Skin skin) {
        meta.setItemModel(new NamespacedKey(plugin, skin.modelPath(false)));
    }
}
