package clre20.itemSkin.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 目前載入的所有外觀（不可變的快照，可在任何執行緒讀取）。 */
public final class SkinLibrary {

    public static final SkinLibrary EMPTY = new SkinLibrary(List.of());

    private final List<Skin> all;
    private final List<Skin> twoD;
    private final List<Skin> threeD;
    private final Map<String, Skin> byId;

    public SkinLibrary(List<Skin> skins) {
        List<Skin> sorted = new ArrayList<>(skins);
        sorted.sort((a, b) -> a.type() != b.type()
                ? a.type().compareTo(b.type())
                : a.name().compareTo(b.name()));
        List<Skin> d2 = new ArrayList<>();
        List<Skin> d3 = new ArrayList<>();
        Map<String, Skin> map = new HashMap<>();
        for (Skin s : sorted) {
            (s.type() == SkinType.TWO_D ? d2 : d3).add(s);
            map.put(s.id(), s);
        }
        this.all = Collections.unmodifiableList(sorted);
        this.twoD = Collections.unmodifiableList(d2);
        this.threeD = Collections.unmodifiableList(d3);
        this.byId = Collections.unmodifiableMap(map);
    }

    public List<Skin> all() {
        return all;
    }

    /** type 為 null 時回傳全部。 */
    public List<Skin> of(SkinType type) {
        if (type == null) {
            return all;
        }
        return type == SkinType.TWO_D ? twoD : threeD;
    }

    public Skin get(String id) {
        return id == null ? null : byId.get(id);
    }

    public int size() {
        return all.size();
    }

    public int count(SkinType type) {
        return of(type).size();
    }
}
