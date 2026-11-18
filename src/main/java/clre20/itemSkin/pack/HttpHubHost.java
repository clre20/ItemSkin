package clre20.itemSkin.pack;

import clre20.httpHub.api.HttpHubService;
import clre20.httpHub.api.Route;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 透過 HttpHub 提供材質包：/itemskin/pack.zip。
 * 只在確認 HttpHub 已啟用後才建立這個類別（避免 NoClassDefFoundError）。
 */
public final class HttpHubHost implements PackHost {

    private static final String PATH = "pack.zip";

    private final Plugin plugin;
    private final HttpHubService hub;
    private volatile Route route;

    private HttpHubHost(Plugin plugin, HttpHubService hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** HttpHub 服務不存在時回傳 null。 */
    public static PackHost create(Plugin plugin) {
        HttpHubService hub = HttpHubService.get();
        return hub == null ? null : new HttpHubHost(plugin, hub);
    }

    @Override
    public synchronized void publish(byte[] zip) {
        Route r = route;
        if (r != null && r.active()) {
            r.update(zip, "application/zip");
        } else {
            route = hub.serve(plugin, PATH, zip, "application/zip");
        }
    }

    @Override
    public String url(Player player) {
        Route r = route;
        if (r == null || !r.active()) {
            return null;
        }
        return hub.url(player, r);
    }

    @Override
    public boolean running() {
        return hub.running();
    }

    @Override
    public synchronized void close() {
        Route r = route;
        route = null;
        if (r != null && r.active()) {
            r.remove();
        }
    }
}
