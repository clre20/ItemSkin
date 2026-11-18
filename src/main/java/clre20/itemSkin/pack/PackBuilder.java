package clre20.itemSkin.pack;

import clre20.itemSkin.skin.Skin;
import clre20.itemSkin.skin.SkinLibrary;
import clre20.itemSkin.skin.SkinType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 讀取 skins 資料夾並產生材質包（背景執行緒執行，不碰任何 Bukkit 物件）。
 * <ul>
 *   <li>只讀資料夾第一層（2D）或第二層（3D）的一般檔案，不跟隨捷徑（symlink）。</li>
 *   <li>讀檔前先檢查大小；PNG 檢查檔頭與尺寸；模型 .json 用 Gson 解析後重新輸出。</li>
 *   <li>zip 內的檔案依路徑排序、時間固定，內容沒變時雜湊也不變，玩家不用重新下載。</li>
 * </ul>
 */
public final class PackBuilder {

    public static final String NAMESPACE = "itemskin";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final long ZIP_TIME = 1_000_000_000_000L; // 固定時間戳，讓雜湊只取決於內容
    private static final Gson GSON = new Gson();

    /** 建立材質包需要的設定（在主執行緒先讀好）。 */
    public record Settings(Path skinsDir, int maxTextureBytes, int maxTextureSize, int maxModelBytes,
                           int maxTexturesPerModel, int maxSkins, long maxPackBytes,
                           int minFormat, int maxFormat, String description) {
    }

    /** 略過某個外觀的原因：reasonKey 是 messages.yml console.reason-* 的鍵，vars 為變數。 */
    public record Skip(String skin, String reasonKey, Object[] vars) {
    }

    /** 結果。error 不為 null 時代表整個材質包無法使用（zip 為 null）。 */
    public record Result(SkinLibrary library, byte[] zip, String sha1, List<Skip> skipped,
                         String errorKey, Object[] errorVars) {
    }

    private final Settings s;
    private final List<Skip> skipped = new ArrayList<>();
    private final TreeMap<String, byte[]> files = new TreeMap<>();
    private final List<Skin> skins = new ArrayList<>();
    private long totalRead;

    private PackBuilder(Settings settings) {
        this.s = settings;
    }

    public static Result build(Settings settings) {
        return new PackBuilder(settings).run();
    }

    private Result run() {
        try {
            Files.createDirectories(s.skinsDir().resolve("2d"));
            Files.createDirectories(s.skinsDir().resolve("3d"));
        } catch (IOException e) {
            return new Result(SkinLibrary.EMPTY, null, null, skipped, "pack-build-failed", new Object[]{"error", e.getMessage()});
        }
        load2d(s.skinsDir().resolve("2d"));
        load3d(s.skinsDir().resolve("3d"));

        files.put("pack.mcmeta", packMeta());
        byte[] zip;
        try {
            zip = zip();
        } catch (IOException e) {
            return new Result(SkinLibrary.EMPTY, null, null, skipped, "pack-build-failed", new Object[]{"error", e.getMessage()});
        }
        SkinLibrary library = new SkinLibrary(skins);
        if (zip.length > s.maxPackBytes()) {
            return new Result(library, null, null, skipped, "pack-too-large",
                    new Object[]{"size", size(zip.length), "max", size(s.maxPackBytes())});
        }
        return new Result(library, zip, sha1(zip), skipped, null, null);
    }

    // ───────── 2D ─────────

    private void load2d(Path dir) {
        Map<String, Path> found = new TreeMap<>();
        for (Path p : list(dir)) {
            String file = p.getFileName().toString();
            if (!file.toLowerCase(Locale.ROOT).endsWith(".png") || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            String name = file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT);
            found.putIfAbsent(name, p);
        }
        for (Map.Entry<String, Path> e : found.entrySet()) {
            String name = e.getKey();
            String id = "2d/" + name;
            if (!NAME.matcher(name).matches()) {
                skip(id, "reason-bad-name");
                continue;
            }
            if (skins.size() >= s.maxSkins()) {
                skip(id, "reason-limit", "max", s.maxSkins());
                continue;
            }
            byte[] png = readPng(id, e.getValue());
            if (png == null) {
                continue;
            }
            byte[] mcmeta = null;
            Path metaPath = e.getValue().resolveSibling(e.getValue().getFileName() + ".mcmeta");
            if (Files.isRegularFile(metaPath, LinkOption.NOFOLLOW_LINKS)) {
                mcmeta = readJson(id, metaPath);
                if (mcmeta == null) {
                    continue;
                }
            }
            String tex = NAMESPACE + ":item/2d/" + name;
            files.put("assets/itemskin/textures/item/2d/" + name + ".png", png);
            if (mcmeta != null) {
                files.put("assets/itemskin/textures/item/2d/" + name + ".png.mcmeta", mcmeta);
            }
            files.put("assets/itemskin/models/item/2d/" + name + ".json", flatModel("minecraft:item/generated", tex));
            files.put("assets/itemskin/models/item/2d_handheld/" + name + ".json", flatModel("minecraft:item/handheld", tex));
            files.put("assets/itemskin/items/2d/" + name + ".json", itemDefinition("item/2d/" + name));
            files.put("assets/itemskin/items/2d_handheld/" + name + ".json", itemDefinition("item/2d_handheld/" + name));
            skins.add(new Skin(SkinType.TWO_D, name));
        }
    }

    // ───────── 3D ─────────

    private void load3d(Path dir) {
        Map<String, Path> found = new TreeMap<>();
        for (Path p : list(dir)) {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                found.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p);
            }
        }
        for (Map.Entry<String, Path> e : found.entrySet()) {
            String name = e.getKey();
            String id = "3d/" + name;
            if (!NAME.matcher(name).matches()) {
                skip(id, "reason-bad-name");
                continue;
            }
            if (skins.size() >= s.maxSkins()) {
                skip(id, "reason-limit", "max", s.maxSkins());
                continue;
            }
            load3dSkin(id, name, e.getValue());
        }
    }

    private void load3dSkin(String id, String name, Path folder) {
        List<Path> jsons = new ArrayList<>();
        Map<String, Path> pngs = new TreeMap<>();
        for (Path p : list(folder)) {
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            String file = p.getFileName().toString();
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".json")) {
                jsons.add(p);
            } else if (lower.endsWith(".png")) {
                String tex = lower.substring(0, lower.length() - 4);
                if (!NAME.matcher(tex).matches()) {
                    skip(id, "reason-bad-texture-name", "file", file);
                    return;
                }
                pngs.putIfAbsent(tex, p);
            }
        }
        Path modelPath = pickModel(name, jsons);
        if (modelPath == null) {
            skip(id, "reason-no-model", "skin", name);
            return;
        }
        if (pngs.size() > s.maxTexturesPerModel()) {
            skip(id, "reason-too-many-textures", "count", pngs.size(), "max", s.maxTexturesPerModel());
            return;
        }

        byte[] raw = readLimited(id, modelPath, s.maxModelBytes());
        if (raw == null) {
            return;
        }
        JsonObject model;
        try {
            JsonElement parsed = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                skip(id, "reason-bad-model", "error", "not an object");
                return;
            }
            model = parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            skip(id, "reason-bad-model", "error", shortError(ex));
            return;
        }
        if (!model.has("elements") && !model.has("parent")) {
            skip(id, "reason-bad-model", "error", "no elements / parent");
            return;
        }

        // 貼圖路徑改成指向同資料夾的 png（依檔名比對，不分大小寫）
        if (model.has("textures") && model.get("textures").isJsonObject()) {
            JsonObject textures = model.getAsJsonObject("textures");
            for (String key : new ArrayList<>(textures.keySet())) {
                JsonElement v = textures.get(key);
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
                    textures.remove(key);
                    continue;
                }
                String ref = v.getAsString();
                if (ref.startsWith("#")) {
                    continue;
                }
                String base = ref.substring(ref.lastIndexOf(':') + 1);
                base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (base.endsWith(".png")) {
                    base = base.substring(0, base.length() - 4);
                }
                if (pngs.containsKey(base)) {
                    textures.addProperty(key, NAMESPACE + ":item/3d/" + name + "/" + base);
                }
            }
        }
        // 只接受原版的 parent，其他命名空間的模型不在這個材質包裡
        if (model.has("parent")) {
            JsonElement parent = model.get("parent");
            String p = parent.isJsonPrimitive() ? parent.getAsString() : "";
            int colon = p.indexOf(':');
            if (p.isEmpty() || (colon >= 0 && !p.substring(0, colon).equals("minecraft"))) {
                model.remove("parent");
            }
        }
        model.remove("credit");

        Map<String, byte[]> texFiles = new HashMap<>();
        for (Map.Entry<String, Path> t : pngs.entrySet()) {
            byte[] png = readPng(id, t.getValue());
            if (png == null) {
                return;
            }
            texFiles.put("assets/itemskin/textures/item/3d/" + name + "/" + t.getKey() + ".png", png);
            Path metaPath = t.getValue().resolveSibling(t.getValue().getFileName() + ".mcmeta");
            if (Files.isRegularFile(metaPath, LinkOption.NOFOLLOW_LINKS)) {
                byte[] meta = readJson(id, metaPath);
                if (meta == null) {
                    return;
                }
                texFiles.put("assets/itemskin/textures/item/3d/" + name + "/" + t.getKey() + ".png.mcmeta", meta);
            }
        }
        files.putAll(texFiles);
        files.put("assets/itemskin/models/item/3d/" + name + ".json", GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
        files.put("assets/itemskin/items/3d/" + name + ".json", itemDefinition("item/3d/" + name));
        skins.add(new Skin(SkinType.THREE_D, name));
    }

    private static Path pickModel(String name, List<Path> jsons) {
        if (jsons.size() == 1) {
            return jsons.getFirst();
        }
        for (Path p : jsons) {
            String f = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (f.equals("model.json") || f.equals(name + ".json")) {
                return p;
            }
        }
        return null;
    }

    // ───────── 檔案讀取 ─────────

    private List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            int n = 0;
            for (Path p : stream) {
                out.add(p);
                if (++n >= 20000) { // 防止資料夾裡塞了大量檔案
                    break;
                }
            }
        } catch (IOException ignored) {
            // 讀不到就當作空的
        }
        return out;
    }

    private byte[] readLimited(String id, Path p, long max) {
        try {
            long size = Files.size(p);
            if (size > max) {
                skip(id, "reason-too-large", "size", size(size), "max", size(max));
                return null;
            }
            // 原始檔案總量也受材質包上限限制，避免一次讀進大量資料吃光記憶體
            if (totalRead + size > s.maxPackBytes()) {
                skip(id, "reason-too-large", "size", size(totalRead + size), "max", size(s.maxPackBytes()));
                return null;
            }
            byte[] data = Files.readAllBytes(p);
            if (data.length > max) {
                skip(id, "reason-too-large", "size", size(data.length), "max", size(max));
                return null;
            }
            totalRead += data.length;
            return data;
        } catch (IOException e) {
            skip(id, "reason-read-error", "error", shortError(e));
            return null;
        }
    }

    private byte[] readPng(String id, Path p) {
        byte[] data = readLimited(id, p, s.maxTextureBytes());
        if (data == null) {
            return null;
        }
        if (data.length < 24) {
            skip(id, "reason-bad-png");
            return null;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                skip(id, "reason-bad-png");
                return null;
            }
        }
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            skip(id, "reason-bad-png");
            return null;
        }
        long width = readInt(data, 16);
        long height = readInt(data, 20);
        if (width <= 0 || height <= 0) {
            skip(id, "reason-bad-png");
            return null;
        }
        int max = s.maxTextureSize();
        // 動畫貼圖是直的長條：寬度受限，高度最多可以是寬度上限的 64 倍
        if (width > max || height > (long) max * 64) {
            skip(id, "reason-png-size", "width", width, "height", height, "max", max);
            return null;
        }
        return data;
    }

    /** 讀取並重新輸出 JSON（.mcmeta），格式錯誤時略過整個外觀。 */
    private byte[] readJson(String id, Path p) {
        byte[] raw = readLimited(id, p, 64 * 1024);
        if (raw == null) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) {
                skip(id, "reason-bad-model", "error", p.getFileName() + " not an object");
                return null;
            }
            return GSON.toJson(el).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            skip(id, "reason-bad-model", "error", p.getFileName() + " " + shortError(e));
            return null;
        }
    }

    private static long readInt(byte[] d, int o) {
        return ((d[o] & 0xffL) << 24) | ((d[o + 1] & 0xffL) << 16) | ((d[o + 2] & 0xffL) << 8) | (d[o + 3] & 0xffL);
    }

    // ───────── 產生 JSON 與 zip ─────────

    private static byte[] flatModel(String parent, String texture) {
        JsonObject o = new JsonObject();
        o.addProperty("parent", parent);
        JsonObject t = new JsonObject();
        t.addProperty("layer0", texture);
        o.add("textures", t);
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] itemDefinition(String modelPath) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", NAMESPACE + ":" + modelPath);
        JsonObject o = new JsonObject();
        o.add("model", model);
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", s.description());
        pack.addProperty("pack_format", s.minFormat());
        JsonArray supported = new JsonArray();
        supported.add(s.minFormat());
        supported.add(s.maxFormat());
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", s.minFormat());
        pack.addProperty("max_format", s.maxFormat());
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(ZIP_TIME);
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void skip(String id, String key, Object... vars) {
        skipped.add(new Skip(id, key, vars));
    }

    private static String shortError(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 120 ? m.substring(0, 120) + "…" : m;
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
