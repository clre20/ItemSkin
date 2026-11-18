package clre20.itemSkin.skin;

/**
 * 一個外觀。id 的格式為 {@code <2d|3d>/<名稱>}，例如 {@code 2d/dragon_sword}。
 * 只存字串，不持有任何 Bukkit 物件。
 */
public record Skin(SkinType type, String name) {

    public String id() {
        return type.dir() + "/" + name;
    }

    /** 物品要設定的 item_model 路徑（命名空間為 itemskin）。 */
    public String modelPath(boolean handheld) {
        if (type == SkinType.TWO_D && handheld) {
            return "2d_handheld/" + name;
        }
        return type.dir() + "/" + name;
    }

    /** 每個外觀的權限節點，例如 itemskin.skin.2d.dragon_sword。 */
    public String permission() {
        return "itemskin.skin." + type.dir() + "." + name;
    }
}
