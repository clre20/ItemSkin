package clre20.itemSkin.gui;

import clre20.itemSkin.ItemSkin;
import clre20.itemSkin.pack.PackService;
import clre20.itemSkin.skin.Skin;
import clre20.itemSkin.skin.SkinType;
import clre20.itemSkin.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 箱子介面：外觀編輯（27 格）與外觀清單（54 格）。
 * <ul>
 *   <li>只接受左鍵；介面內的點擊一律取消，放入格由插件自己搬移物品。</li>
 *   <li>放入的物品一直放在編輯介面的那一格裡（不另外複製），關閉、離線、插件停用時歸還。</li>
 *   <li>工作階段以玩家 UUID 為鍵，只保存介面與選項，不持有 Player。</li>
 * </ul>
 */
public final class GuiManager implements Listener {

    // 編輯介面
    private static final int EDITOR_SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_TAG = 10;
    private static final int SLOT_SELECT = 11;
    private static final int SLOT_INPUT = 13;
    private static final int SLOT_SAVE = 15;
    private static final int SLOT_REMOVE = 16;
    private static final int SLOT_PACK = 22;
    private static final int SLOT_CLOSE = 26;

    // 外觀清單
    private static final int PICKER_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_FILTER = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private static final class Session {
        final Inventory editor;
        String selected;
        boolean tag;
        int page;
        SkinType filter;
        /** 正在切換介面（關閉舊介面時不要歸還物品）。 */
        boolean switching;
        /** 目前清單頁顯示的外觀（點擊時對照用）。 */
        List<Skin> shown = List.of();

        Session(Inventory editor, boolean tag) {
            this.editor = editor;
            this.tag = tag;
        }
    }

    private final ItemSkin plugin;
    private final NamespacedKey guiKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** 重新接收材質包的冷卻（UUID → 可再次使用的時間），離線時移除。 */
    private final Map<UUID, Long> packCooldown = new HashMap<>();
    private static final long PACK_COOLDOWN_MS = 5000L;

    public GuiManager(ItemSkin plugin) {
        this.plugin = plugin;
        this.guiKey = new NamespacedKey(plugin, "gui");
    }

    // ───────── 開啟 ─────────

    public void openEditor(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            ItemSkinHolder holder = new ItemSkinHolder(plugin, ItemSkinHolder.Type.EDITOR, player.getUniqueId());
            Inventory inv = holder.create(EDITOR_SIZE, title("gui.editor-title"));
            s = new Session(inv, plugin.config().tagDefault());
            sessions.put(player.getUniqueId(), s);
        }
        renderEditor(player, s);
        show(player, s, s.editor);
    }

    /** 切換到指定介面；被其他插件擋下時結束工作階段並歸還物品。 */
    private void show(Player player, Session s, Inventory inv) {
        Inventory current = player.getOpenInventory().getTopInventory();
        if (current.getHolder(false) instanceof ItemSkinHolder h && h.plugin() == plugin) {
            s.switching = true;
        }
        player.openInventory(inv);
        s.switching = false;
        if (player.getOpenInventory().getTopInventory().getHolder(false) != inv.getHolder(false)) {
            end(player);
        }
    }

    private void openPicker(Player player, Session s) {
        List<Skin> list = visibleSkins(player, s.filter);
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        s.page = Math.max(0, Math.min(s.page, pages - 1));

        ItemSkinHolder holder = new ItemSkinHolder(plugin, ItemSkinHolder.Type.PICKER, player.getUniqueId());
        Inventory inv = holder.create(PICKER_SIZE, title("gui.picker-title", "page", s.page + 1, "pages", pages));
        ItemStack filler = filler();
        for (int i = PAGE_SIZE; i < PICKER_SIZE; i++) {
            inv.setItem(i, filler);
        }
        int from = s.page * PAGE_SIZE;
        int to = Math.min(list.size(), from + PAGE_SIZE);
        s.shown = new ArrayList<>(list.subList(from, to));
        for (int i = 0; i < s.shown.size(); i++) {
            inv.setItem(i, skinIcon(s.shown.get(i), s.shown.get(i).id().equals(s.selected)));
        }
        if (list.isEmpty()) {
            inv.setItem(22, button(Material.BARRIER, "gui.empty-list", "empty"));
        }
        if (s.page > 0) {
            inv.setItem(SLOT_PREV, button(Material.ARROW, "gui.prev", "prev"));
        }
        if (s.page < pages - 1) {
            inv.setItem(SLOT_NEXT, button(Material.ARROW, "gui.next", "next"));
        }
        String filterName = plugin.messages().get(s.filter == null ? "gui.filter-all"
                : s.filter == SkinType.TWO_D ? "gui.filter-2d" : "gui.filter-3d");
        inv.setItem(SLOT_FILTER, button(Material.HOPPER, "gui.filter", "filter", "filter", filterName));
        inv.setItem(SLOT_BACK, button(Material.OAK_DOOR, "gui.back", "back"));
        show(player, s, inv);
    }

    private List<Skin> visibleSkins(Player player, SkinType filter) {
        List<Skin> all = plugin.packs().library().of(filter);
        if (!plugin.config().perSkinPermission()) {
            return all;
        }
        List<Skin> out = new ArrayList<>(all.size());
        for (Skin skin : all) {
            if (canUse(player, skin)) {
                out.add(skin);
            }
        }
        return out;
    }

    private boolean canUse(Player player, Skin skin) {
        return !plugin.config().perSkinPermission()
                || player.hasPermission("itemskin.skin.*")
                || player.hasPermission(skin.permission());
    }

    // ───────── 繪製 ─────────

    private void renderEditor(Player player, Session s) {
        Inventory inv = s.editor;
        ItemStack filler = filler();
        for (int i = 0; i < EDITOR_SIZE; i++) {
            if (i != SLOT_INPUT) {
                inv.setItem(i, filler);
            }
        }
        inv.setItem(SLOT_INFO, button(Material.BOOK, "gui.info", "info"));
        inv.setItem(SLOT_TAG, s.tag
                ? button(Material.LIME_DYE, "gui.tag-on", "tag")
                : button(Material.GRAY_DYE, "gui.tag-off", "tag"));

        Skin selected = plugin.packs().library().get(s.selected);
        if (selected == null) {
            s.selected = null;
            inv.setItem(SLOT_SELECT, button(Material.PAINTING, "gui.select-none", "select"));
        } else {
            ItemStack icon = button(Material.PAINTING, "gui.select", "select",
                    "skin", selected.name(), "type", typeName(selected.type()));
            ItemMeta meta = icon.getItemMeta();
            plugin.items().preview(meta, selected);
            icon.setItemMeta(meta);
            inv.setItem(SLOT_SELECT, icon);
        }
        inv.setItem(SLOT_SAVE, button(Material.LIME_CONCRETE, "gui.save", "save"));
        inv.setItem(SLOT_REMOVE, button(Material.RED_CONCRETE, "gui.remove", "remove"));
        inv.setItem(SLOT_PACK, packButton(player, inv.getItem(SLOT_INPUT)));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "gui.close", "close"));
    }

    private ItemStack packButton(Player player, ItemStack input) {
        String status = plugin.messages().get(plugin.packs().statusKey(player));
        String itemLine;
        String skinId = input == null || input.isEmpty() ? null : plugin.items().skinId(input.getItemMeta());
        if (skinId == null) {
            itemLine = plugin.messages().get("gui.pack-item-none");
        } else if (plugin.packs().library().get(skinId) != null) {
            itemLine = plugin.messages().get("gui.pack-item-ok", "skin", skinId);
        } else {
            itemLine = plugin.messages().get("gui.pack-item-missing", "skin", skinId);
        }
        return button(Material.KNOWLEDGE_BOOK, "gui.pack-status", "pack", "status", status, "item-line", itemLine);
    }

    private ItemStack skinIcon(Skin skin, boolean selected) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        Object[] vars = {"skin", skin.name(), "type", typeName(skin.type())};
        meta.displayName(noItalic(plugin.messages().get("gui.skin.name", vars)));
        List<Component> lore = new ArrayList<>();
        if (selected) {
            lore.add(noItalic(plugin.messages().get("gui.skin-selected-lore")));
            meta.setEnchantmentGlintOverride(true);
        }
        for (String line : plugin.messages().list("gui.skin.lore", vars)) {
            lore.add(noItalic(line));
        }
        meta.lore(lore);
        plugin.items().preview(meta, skin);
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "skin");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String path, String id, Object... vars) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(plugin.messages().get(path + ".name", vars)));
        List<String> raw = plugin.messages().list(path + ".lore", vars);
        if (!raw.isEmpty()) {
            List<Component> lore = new ArrayList<>(raw.size());
            for (String line : raw) {
                lore.add(noItalic(line));
            }
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(plugin.messages().get("gui.filler")));
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "filler");
        item.setItemMeta(meta);
        return item;
    }

    private Component title(String key, Object... vars) {
        return Text.color(plugin.messages().get(key, vars));
    }

    private static Component noItalic(String text) {
        return Text.color(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private String typeName(SkinType type) {
        return plugin.messages().get(type == SkinType.TWO_D ? "gui.type-2d" : "gui.type-3d");
    }

    // ───────── 事件 ─────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (!(top.getHolder(false) instanceof ItemSkinHolder holder) || holder.plugin() != plugin) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (!inTop) {
            // 自己的背包：只允許一般的拿起/放下，擋掉所有可能把物品移進介面的操作
            if (holder.type() != ItemSkinHolder.Type.EDITOR) {
                event.setCancelled(true);
                return;
            }
            switch (event.getAction()) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                     PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, NOTHING -> {
                    if (event.getClick().isShiftClick() || event.getClick() == ClickType.NUMBER_KEY
                            || event.getClick() == ClickType.DOUBLE_CLICK) {
                        event.setCancelled(true);
                    }
                }
                default -> event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (s == null || event.getClick() != ClickType.LEFT) {
            return;
        }
        // 開著介面時權限被移除：直接關閉
        if (!player.hasPermission("itemskin.use")) {
            later(player, player::closeInventory);
            return;
        }
        if (holder.type() == ItemSkinHolder.Type.EDITOR) {
            if (top != s.editor) {
                return;
            }
            clickEditor(player, s, raw, view);
        } else {
            clickPicker(player, s, raw);
        }
    }

    private void clickEditor(Player player, Session s, int slot, InventoryView view) {
        switch (slot) {
            case SLOT_INPUT -> {
                moveInput(s.editor, view);
                s.editor.setItem(SLOT_PACK, packButton(player, s.editor.getItem(SLOT_INPUT)));
            }
            case SLOT_TAG -> {
                s.tag = !s.tag;
                renderEditor(player, s);
            }
            case SLOT_SELECT -> later(player, () -> openPicker(player, s));
            case SLOT_SAVE -> save(player, s);
            case SLOT_REMOVE -> remove(player, s);
            case SLOT_PACK -> {
                sendPack(player);
                renderEditor(player, s);
            }
            case SLOT_CLOSE -> later(player, player::closeInventory);
            default -> {
            }
        }
    }

    /** 放入格：游標與格子交換，同種物品則疊上去。 */
    private static void moveInput(Inventory inv, InventoryView view) {
        ItemStack cursor = view.getCursor();
        ItemStack slot = inv.getItem(SLOT_INPUT);
        boolean cursorEmpty = cursor == null || cursor.isEmpty();
        boolean slotEmpty = slot == null || slot.isEmpty();
        if (cursorEmpty && slotEmpty) {
            return;
        }
        if (!cursorEmpty && !slotEmpty && cursor.isSimilar(slot)) {
            int move = Math.min(slot.getMaxStackSize() - slot.getAmount(), cursor.getAmount());
            if (move <= 0) {
                return;
            }
            ItemStack newSlot = slot.clone();
            newSlot.setAmount(slot.getAmount() + move);
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(cursor.getAmount() - move);
            inv.setItem(SLOT_INPUT, newSlot);
            view.setCursor(newCursor.getAmount() <= 0 ? null : newCursor);
            return;
        }
        inv.setItem(SLOT_INPUT, cursorEmpty ? null : cursor.clone());
        view.setCursor(slotEmpty ? null : slot.clone());
    }

    private void save(Player player, Session s) {
        ItemStack item = s.editor.getItem(SLOT_INPUT);
        if (item == null || item.isEmpty()) {
            plugin.messages().send(player, "editor.no-item");
            return;
        }
        if (s.selected == null) {
            plugin.messages().send(player, "editor.no-skin");
            return;
        }
        Skin skin = plugin.packs().library().get(s.selected);
        if (skin == null) {
            s.selected = null;
            renderEditor(player, s);
            plugin.messages().send(player, "editor.skin-gone");
            return;
        }
        if (!canUse(player, skin)) {
            plugin.messages().send(player, "editor.no-skin-permission");
            return;
        }
        if (plugin.config().isBlocked(item.getType())) {
            plugin.messages().send(player, "editor.blocked-material");
            return;
        }
        if (!plugin.config().allowOverrideOtherModels() && plugin.items().hasForeignModel(item.getItemMeta())) {
            plugin.messages().send(player, "editor.other-model");
            return;
        }
        ItemStack result = item.clone();
        plugin.items().apply(result, skin, s.tag, plugin.config().handheldForTools());
        s.editor.setItem(SLOT_INPUT, result);
        renderEditor(player, s);
        plugin.messages().send(player, "editor.saved", "skin", skin.id(),
                "tag", plugin.messages().get(s.tag ? "editor.tag-yes" : "editor.tag-no"));
    }

    private void remove(Player player, Session s) {
        ItemStack item = s.editor.getItem(SLOT_INPUT);
        if (item == null || item.isEmpty()) {
            plugin.messages().send(player, "editor.no-item");
            return;
        }
        ItemStack result = item.clone();
        if (!plugin.items().remove(result)) {
            plugin.messages().send(player, "editor.nothing-to-remove");
            return;
        }
        s.editor.setItem(SLOT_INPUT, result);
        renderEditor(player, s);
        plugin.messages().send(player, "editor.removed");
    }

    private void clickPicker(Player player, Session s, int slot) {
        if (slot < PAGE_SIZE) {
            if (slot >= s.shown.size()) {
                return;
            }
            Skin skin = s.shown.get(slot);
            if (plugin.packs().library().get(skin.id()) == null) {
                plugin.messages().send(player, "editor.skin-gone");
                later(player, () -> openPicker(player, s));
                return;
            }
            s.selected = skin.id();
            later(player, () -> openEditor(player));
            return;
        }
        switch (slot) {
            case SLOT_PREV -> {
                s.page--;
                later(player, () -> openPicker(player, s));
            }
            case SLOT_NEXT -> {
                s.page++;
                later(player, () -> openPicker(player, s));
            }
            case SLOT_FILTER -> {
                s.filter = s.filter == null ? SkinType.TWO_D : s.filter == SkinType.TWO_D ? SkinType.THREE_D : null;
                s.page = 0;
                later(player, () -> openPicker(player, s));
            }
            case SLOT_BACK -> later(player, () -> openEditor(player));
            default -> {
            }
        }
    }

    /**
     * 下一個 tick 再切換介面（在點擊事件裡直接開關介面容易造成物品不同步）。
     * 執行前確認玩家還在線上、工作階段沒有結束。
     */
    private void later(Player player, Runnable action) {
        UUID id = player.getUniqueId();
        Session expected = sessions.get(id);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && sessions.get(id) == expected && expected != null) {
                action.run();
            }
        });
    }

    /** 發送材質包並回報結果（介面與指令共用）。 */
    public void sendPack(Player player) {
        long now = System.currentTimeMillis();
        Long until = packCooldown.get(player.getUniqueId());
        if (until != null && now < until) {
            plugin.messages().send(player, "pack.cooldown", "seconds", (until - now + 999) / 1000);
            return;
        }
        packCooldown.put(player.getUniqueId(), now + PACK_COOLDOWN_MS);
        PackService.SendResult r = plugin.packs().send(player);
        plugin.messages().send(player, switch (r) {
            case SENT -> "pack.sent";
            case NOT_READY -> "pack.not-ready";
            case NO_HOST -> "pack.no-httphub";
            case NO_URL -> "pack.no-url";
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof ItemSkinHolder holder) || holder.plugin() != plugin) {
            return;
        }
        int size = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < size) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ItemSkinHolder holder) || holder.plugin() != plugin) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.switching) {
            return;
        }
        end(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        packCooldown.remove(event.getPlayer().getUniqueId());
        end(event.getPlayer());
    }

    /** 結束工作階段並把放入的物品還給玩家（背包滿了就掉在腳下）。 */
    private void end(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        ItemStack item = s.editor.getItem(SLOT_INPUT);
        s.editor.clear();
        if (item == null || item.isEmpty()) {
            return;
        }
        if (player.isDead()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        if (!left.isEmpty()) {
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            plugin.messages().send(player, "editor.returned-drop");
        }
    }

    /** 插件停用：關閉所有介面並歸還物品。 */
    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                sessions.remove(id);
                continue;
            }
            end(p);
            Inventory open = p.getOpenInventory().getTopInventory();
            if (open.getHolder(false) instanceof ItemSkinHolder h && h.plugin() == plugin) {
                p.closeInventory();
            }
        }
        sessions.clear();
        packCooldown.clear();
    }

    /** 重新載入後刷新開著的編輯介面（外觀可能被刪除）。 */
    public void refreshOpen() {
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                renderEditor(p, e.getValue());
            }
        }
    }
}
