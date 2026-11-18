package clre20.itemSkin.pack;

import org.bukkit.entity.Player;

/** 材質包的下載來源。實作放在獨立類別，沒裝 HttpHub 時不會載入 HttpHub 的類別。 */
public interface PackHost {

    /** 發布或更新材質包內容（網址不變）。 */
    void publish(byte[] zip);

    /** 給這個玩家的下載網址；取不到時回傳 null。 */
    String url(Player player);

    /** HTTP 伺服器是否在運作。 */
    boolean running();

    /** 移除已註冊的路徑。 */
    void close();
}
