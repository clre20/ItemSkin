package clre20.itemSkin.skin;

public enum SkinType {
    TWO_D("2d"),
    THREE_D("3d");

    private final String dir;

    SkinType(String dir) {
        this.dir = dir;
    }

    /** 資料夾名稱，也是 id 的前綴。 */
    public String dir() {
        return dir;
    }
}
