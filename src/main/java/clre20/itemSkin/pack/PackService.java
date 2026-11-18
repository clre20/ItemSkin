package clre20.itemSkin.pack;

import clre20.itemSkin.ItemSkin;
import clre20.itemSkin.skin.SkinLibrary;
import clre20.itemSkin.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 材質包：背景建立、交給 HttpHub、發送給玩家、追蹤每位玩家的載入狀態。
 * <p>
 * 執行緒：建立材質包只用一條背景執行緒（同時只會有一個建立工作）；其餘都在主執行緒。
 * 玩家狀態以 UUID 為鍵，離線時移除。
 */
public final class PackService implements Listener {

    /** 固定的材質包 UUID：重新發送時用戶端會取代舊版，不影響其他插件的材質包。 */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("ItemSkin:resource-pack".getBytes(StandardCharsets.UTF_8));

    public enum Status { NONE, PENDING, ACCEPTED, DOWNLOADED, LOADED, DECLINED, FAILED, DISCARDED }

    /** 一位玩家的材質包狀態；hash 是最後一次發送的版本。 */
    private static final class PlayerPack {
        Status status = Status.NONE;
        String hash;
    }

    private final ItemSkin plugin;
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ItemSkin-PackBuilder");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerPack> players = new HashMap<>();

    private volatile SkinLibrary library = SkinLibrary.EMPTY;
    private volatile String hash;
    private byte[] zip;
    private PackHost host;
    private boolean building;
    private boolean hostWarned;

    public PackService(ItemSkin plugin) {
        this.plugin = plugin;
    }

    public SkinLibrary library() {
        return library;
    }

    /** 目前材質包的 SHA-1；還沒建立或建立失敗時為 null。 */
    public String hash() {
        return hash;
    }

    public boolean building() {
        return building;
    }

    // ───────── 建立 ─────────

    /**
     * 在背景重新讀取外觀並建立材質包，完成後回到主執行緒套用。
     *
     * @return false 代表已經有建立工作在進行
     */
    public boolean rebuild(Consumer<PackBuilder.Result> done) {
        if (building) {
            return false;
        }
        building = true;
        PackBuilder.Settings settings = new PackBuilder.Settings(
                plugin.getDataFolder().toPath().resolve("skins"),
                plugin.config().maxTextureBytes(),
                plugin.config().maxTextureSize(),
                plugin.config().maxModelBytes(),
                plugin.config().maxTexturesPerModel(),
                plugin.config().maxSkins(),
                plugin.config().maxPackBytes(),
                plugin.config().minFormat(),
                plugin.config().maxFormat(),
                Text.plain(plugin.messages().get("pack.description")));
        builder.execute(() -> {
            PackBuilder.Result result;
            try {
                result = PackBuilder.build(settings);
            } catch (Throwable t) {
                result = new PackBuilder.Result(SkinLibrary.EMPTY, null, null, java.util.List.of(),
                        "pack-build-failed", new Object[]{"error", String.valueOf(t.getMessage())});
            }
            PackBuilder.Result r = result;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                building = false;
                apply(r);
                if (done != null) {
                    done.accept(r);
                }
            });
        });
        return true;
    }

    private void apply(PackBuilder.Result r) {
        for (PackBuilder.Skip skip : r.skipped()) {
            String reason = plugin.messages().console(skip.reasonKey(), skip.vars());
            plugin.getLogger().warning(plugin.messages().console("skip", "skin", skip.skin(), "reason", reason));
        }
        library = r.library();
        String oldHash = hash;
        if (r.zip() == null) {
            plugin.getLogger().warning(plugin.messages().console(r.errorKey(), r.errorVars()));
            // 保留上一份可用的材質包，不讓玩家突然看不到外觀
            return;
        }
        zip = r.zip();
        hash = r.sha1();
        plugin.getLogger().info(plugin.messages().console("loaded",
                "skins", library.size(),
                "count2d", library.count(clre20.itemSkin.skin.SkinType.TWO_D),
                "count3d", library.count(clre20.itemSkin.skin.SkinType.THREE_D),
                "skipped", r.skipped().size(),
                "size", PackBuilder.size(zip.length),
                "hash", hash));
        publish();
        if (oldHash != null && !oldHash.equals(hash) && plugin.config().resendOnUpdate()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerPack state = players.get(p.getUniqueId());
                if (state != null && state.status != Status.NONE && state.status != Status.DECLINED) {
                    plugin.messages().send(p, "pack.updated-notify");
                    send(p);
                }
            }
        }
    }

    // ───────── HttpHub ─────────

    /** 連接 HttpHub（啟動時與 HttpHub 啟用時）。 */
    public void connectHost() {
        if (host != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("HttpHub")) {
            if (!hostWarned) {
                plugin.getLogger().warning(plugin.messages().console("httphub-missing"));
                hostWarned = true;
            }
            return;
        }
        try {
            host = HttpHubHost.create(plugin);
        } catch (LinkageError e) {
            host = null;
        }
        if (host == null) {
            if (!hostWarned) {
                plugin.getLogger().warning(plugin.messages().console("httphub-missing"));
                hostWarned = true;
            }
            return;
        }
        hostWarned = false;
        publish();
    }

    private void publish() {
        if (host == null || zip == null) {
            return;
        }
        host.publish(zip);
        plugin.getLogger().info(plugin.messages().console("httphub-published"));
        if (!host.running()) {
            plugin.getLogger().warning(plugin.messages().console("httphub-not-running"));
        }
    }

    public void shutdown() {
        builder.shutdownNow();
        if (host != null) {
            try {
                host.close();
            } catch (LinkageError | RuntimeException ignored) {
                // HttpHub 已先停用
            }
            host = null;
        }
        players.clear();
        zip = null;
    }

    // ───────── 發送 ─────────

    public enum SendResult { SENT, NOT_READY, NO_HOST, NO_URL }

    public SendResult send(Player player) {
        String h = hash;
        if (zip == null || h == null) {
            return SendResult.NOT_READY;
        }
        if (host == null) {
            return SendResult.NO_HOST;
        }
        String url = host.url(player);
        if (url == null) {
            return SendResult.NO_URL;
        }
        URI uri;
        try {
            // 網址加上雜湊：內容變動時網址也跟著變，用戶端不會用到快取的舊檔
            uri = URI.create(url + "?v=" + h);
        } catch (IllegalArgumentException e) {
            return SendResult.NO_URL;
        }
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, h))
                .required(plugin.config().packRequired())
                .prompt(plugin.messages().component("pack.prompt"))
                .replace(false)
                .build();
        player.sendResourcePacks(request);
        PlayerPack state = players.computeIfAbsent(player.getUniqueId(), k -> new PlayerPack());
        state.status = Status.PENDING;
        state.hash = h;
        return SendResult.SENT;
    }

    /** 玩家目前的材質包狀態，對應 messages.yml 的 pack.status-* 鍵。 */
    public String statusKey(Player player) {
        if (zip == null || host == null) {
            return "pack.status-unavailable";
        }
        PlayerPack state = players.get(player.getUniqueId());
        if (state == null) {
            return "pack.status-none";
        }
        return switch (state.status) {
            case NONE -> "pack.status-none";
            case PENDING -> "pack.status-pending";
            case ACCEPTED -> "pack.status-accepted";
            case DOWNLOADED -> "pack.status-downloaded";
            case LOADED -> hash != null && hash.equals(state.hash) ? "pack.status-loaded" : "pack.status-outdated";
            case DECLINED -> "pack.status-declined";
            case FAILED -> "pack.status-failed";
            case DISCARDED -> "pack.status-discarded";
        };
    }

    // ───────── 事件 ─────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.config().sendOnJoin()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                send(p);
            }
        };
        long delay = plugin.config().joinDelayTicks();
        if (delay <= 0) {
            task.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        PlayerPack state = players.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new PlayerPack());
        state.status = switch (event.getStatus()) {
            case ACCEPTED -> Status.ACCEPTED;
            case DOWNLOADED -> Status.DOWNLOADED;
            case SUCCESSFULLY_LOADED -> Status.LOADED;
            case DECLINED -> Status.DECLINED;
            case DISCARDED -> Status.DISCARDED;
            default -> Status.FAILED;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("HttpHub")) {
            connectHost();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("HttpHub")) {
            host = null; // HttpHub 停用時會自動移除路徑
        }
    }
}
