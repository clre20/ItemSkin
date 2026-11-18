package clre20.itemSkin.config;

import clre20.itemSkin.ItemSkin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * config.yml。啟動與重載時自動補上缺少的設定，原本的值保留。
 * 所有讀取都有預設值與範圍限制，設定檔寫錯也不會出錯。
 * 常用的值在 load() 時讀成欄位，事件中不再查 YAML。
 */
public final class PluginConfig {

    private final ItemSkin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();
    private int lastFilled;

    private volatile Set<Material> blockedMaterials = EnumSet.noneOf(Material.class);

    public PluginConfig(ItemSkin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlFiles.Loaded loaded = YamlFiles.load(plugin, "config.yml", null,
                (file, reason) -> plugin.getLogger().warning("config.yml: " + reason));
        yaml = loaded.yaml();
        lastFilled = loaded.filled();

        Set<Material> blocked = EnumSet.noneOf(Material.class);
        for (String s : yaml.getStringList("apply.blocked-materials")) {
            if (s == null) {
                continue;
            }
            Material m = Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
            if (m != null) {
                blocked.add(m);
            }
        }
        blockedMaterials = blocked;
    }

    public int lastFilled() {
        return lastFilled;
    }

    public String prefix() {
        return yaml.getString("prefix", "&8[&dItemSkin&8] &r");
    }

    // ───── 外觀 ─────

    public int maxTextureBytes() {
        return clamp(yaml.getInt("skins.max-texture-kb", 1024), 1, 16384) * 1024;
    }

    public int maxTextureSize() {
        return clamp(yaml.getInt("skins.max-texture-size", 1024), 16, 8192);
    }

    public int maxModelBytes() {
        return clamp(yaml.getInt("skins.max-model-kb", 512), 1, 8192) * 1024;
    }

    public int maxTexturesPerModel() {
        return clamp(yaml.getInt("skins.max-textures-per-model", 32), 1, 256);
    }

    public int maxSkins() {
        return clamp(yaml.getInt("skins.max-skins", 500), 1, 10000);
    }

    public boolean handheldForTools() {
        return yaml.getBoolean("skins.handheld-for-tools", true);
    }

    // ───── 材質包 ─────

    public long maxPackBytes() {
        return clamp(yaml.getInt("pack.max-size-mb", 64), 1, 512) * 1024L * 1024L;
    }

    public int minFormat() {
        return clamp(yaml.getInt("pack.min-format", 46), 1, 9999);
    }

    public int maxFormat() {
        return Math.max(minFormat(), clamp(yaml.getInt("pack.max-format", 999), 1, 9999));
    }

    public long joinDelayTicks() {
        return clamp(yaml.getInt("pack.join-delay-ticks", 20), 0, 1200);
    }

    public boolean sendOnJoin() {
        return yaml.getBoolean("pack.send-on-join", true);
    }

    public boolean packRequired() {
        return yaml.getBoolean("pack.required", false);
    }

    public boolean resendOnUpdate() {
        return yaml.getBoolean("pack.resend-on-update", true);
    }

    // ───── 套用 ─────

    public boolean perSkinPermission() {
        return yaml.getBoolean("apply.per-skin-permission", false);
    }

    public boolean allowOverrideOtherModels() {
        return yaml.getBoolean("apply.allow-override-other-models", false);
    }

    public boolean isBlocked(Material material) {
        return blockedMaterials.contains(material);
    }

    public boolean tagDefault() {
        return yaml.getBoolean("apply.tag-default", true);
    }

    // ───── 更新檢查 ─────

    public boolean updateCheckOnStartup() {
        return yaml.getBoolean("update-checker.check-on-startup", true);
    }

    public boolean updateNotifyOnJoin() {
        return yaml.getBoolean("update-checker.notify-on-join", true);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
