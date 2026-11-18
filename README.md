# ItemSkin

ItemSkin 是 Paper 26.x 的物品外觀插件。把物品放進箱子介面、挑一個 2D 或 3D 外觀、按下儲存，物品就會換成自訂外觀；材質包由插件自動產生，並透過 [HttpHub](https://github.com/clre20/HttpHub) 發送給玩家，不需要自己架網站或上傳材質包。

## 功能特性

- **箱子介面操作**：
  * `/itemskin` 開啟編輯介面，左鍵把物品放進中間的空格
  * 外觀清單可分頁、篩選全部 / 2D / 3D，圖示直接顯示外觀預覽
  * 全部左鍵操作；關閉介面、離線、關服時物品自動歸還
- **2D 與 3D 外觀**：
  * 2D：一張 PNG，支援動畫貼圖（`.png.mcmeta`）
  * 3D：Blockbench 匯出的 Java 物品模型，貼圖路徑自動對應
  * 2D 外觀套在劍、斧、鎬等工具上時自動改用手持工具的拿法
- **隱藏標籤（可選）**：
  * 儲存時可選擇是否加上看不到的 ItemSkin 標籤
  * 不影響 [Item-lock](https://modrinth.com/plugin/item-lock-clre20) 的物品比對（需 Item-lock 2.4.1 以上）
- **材質包自動發送**：
  * 啟動與重新載入時在背景產生材質包，交給 HttpHub 提供下載
  * 玩家登入自動接收；外觀有變動時自動重新發送給線上玩家
  * 內容沒變時雜湊不變，玩家不會重複下載
- **套用狀態查詢**：
  * 介面內的「材質包狀態」顯示你是否已載入最新材質包，以及放入物品的外觀是否在材質包中
  * 手持物品輸入 `/itemskin check` 查詢外觀、標籤與材質包狀態
  * 看不到外觀時 `/itemskin pack` 重新接收
- **安全與效能**：
  * 讀檔、產生材質包都在背景單一執行緒進行，只在啟動與重新載入時讀取磁碟
  * 檔名、檔案大小、圖片尺寸、外觀數量、材質包大小都有上限
  * 預設不覆蓋其他插件設定的物品外觀，可設定禁止套用的物品
- **設定與訊息**：
  * 所有文字都在 `messages.yml`，支援 & 顏色碼與 `&#RRGGBB`
  * `config.yml`、`messages.yml` 啟動與重載時自動補齊缺少的項目

## 需求

- Paper 26.1 以上（Java 25）
- [HttpHub](https://github.com/clre20/HttpHub)（負責發送材質包；沒有安裝時外觀仍可套用，但玩家看不到）
- 玩家用戶端 1.21.4 以上才看得到外觀

## 指令與權限

主指令為 `/itemskin`，別名 `/items`。

| 指令 | 說明 | 執行者 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/itemskin` | 玩家開啟編輯介面，後台顯示說明 | 遊戲內／後台 | `itemskin.use` | OP |
| `/itemskin help` | 列出有權限的指令 | 遊戲內／後台 | `itemskin.help` | 所有人 |
| `/itemskin open` | 開啟外觀編輯介面 | 玩家 | `itemskin.use` | OP |
| `/itemskin check` | 查詢手上物品的外觀與材質包狀態 | 玩家 | `itemskin.check` | 所有人 |
| `/itemskin pack` | 重新接收材質包（冷卻 5 秒） | 玩家 | `itemskin.pack` | 所有人 |
| `/itemskin reload` | 重新載入設定、訊息與外觀，重建材質包 | 遊戲內／後台 | `itemskin.reload` | OP |
| `/itemskin version` | 檢查 GitHub 新版本 | 遊戲內／後台 | `itemskin.version` | OP |

> - `itemskin.*` 包含以上全部權限
> - `apply.per-skin-permission` 開啟時，每個外觀需要 `itemskin.skin.<2d|3d>.<名稱>`（或 `itemskin.skin.*`）
> - 有 `itemskin.version` 的玩家登入 3 秒後，若有新版本會收到提醒

## 使用範例

### 1. 新增 2D 外觀
1. 把 `dragon_sword.png` 放到 `plugins/ItemSkin/skins/2d/`
2. 輸入：
```bash
/itemskin reload
```
3. 線上玩家會自動收到新的材質包。

### 2. 新增 3D 外觀
1. 在 Blockbench 以「Java 物品模型」匯出，得到 `.json` 與貼圖 `.png`
2. 建立資料夾 `plugins/ItemSkin/skins/3d/magic_staff/`，把 `.json` 與 `.png` 放進去（`.json` 建議命名為 `model.json`）
3. 輸入 `/itemskin reload`

### 3. 替物品換外觀
1. 輸入 `/itemskin` 開啟介面
2. 左鍵把物品放進中間空格，選擇是否加上隱藏標籤
3. 點「選擇外觀」挑一個外觀，再點「儲存」
4. 關閉介面，物品會回到背包

## 設定檔

`config.yml` 主要項目：

| 項目 | 說明 |
| :--- | :--- |
| `skins.*` | 貼圖與模型大小、尺寸、數量上限，工具是否用手持拿法 |
| `pack.max-size-mb` | 材質包大小上限 |
| `pack.min-format`、`pack.max-format` | 材質包支援的格式範圍 |
| `pack.send-on-join`、`pack.join-delay-ticks` | 登入時自動發送與延遲 |
| `pack.required` | 是否強制接受材質包 |
| `pack.resend-on-update` | 外觀變動後自動重新發送 |
| `apply.per-skin-permission` | 每個外觀是否需要個別權限 |
| `apply.allow-override-other-models` | 是否允許覆蓋其他插件設定的外觀 |
| `apply.blocked-materials` | 不能套用外觀的物品 |
| `apply.tag-default` | 隱藏標籤預設是否開啟 |
| `update-checker.check-on-startup`、`update-checker.notify-on-join` | 版本檢查 |

所有訊息都在 `messages.yml`，變數格式為 `{名稱}`。

## 給其他插件

- 外觀：物品的 `item_model` 組件，命名空間 `itemskin`（例如 `itemskin:2d/dragon_sword`、`itemskin:3d/magic_staff`）
- 隱藏標籤：PDC `itemskin:skin`，值為外觀 id（例如 `2d/dragon_sword`）
- 介面識別：所有箱子介面的 InventoryHolder 為 `clre20.itemSkin.gui.ItemSkinHolder`，按鈕帶有 PDC `itemskin:gui`

---

# ItemSkin

ItemSkin is an item skin plugin for Paper 26.x. Put an item into a chest GUI, pick a 2D or 3D skin, click save, and the item gets a custom look. The resource pack is generated automatically and delivered through [HttpHub](https://github.com/clre20/HttpHub) — no web hosting or manual uploads.

## Features

- **Chest GUI**: `/itemskin` opens the editor; paged skin list with All / 2D / 3D filter and live previews; left-click only; items are returned on close, quit or shutdown
- **2D & 3D skins**: 2D from a PNG (animated `.png.mcmeta` supported); 3D from Blockbench Java item models with automatic texture remapping; tools use the handheld pose
- **Optional hidden tag**: an invisible ItemSkin tag that does not affect [Item-lock](https://modrinth.com/plugin/item-lock-clre20) matching (Item-lock 2.4.1+)
- **Automatic pack delivery**: built in the background, served by HttpHub, sent on join and re-sent when skins change; unchanged content keeps the same hash
- **Status check**: pack status button in the GUI, `/itemskin check` for the held item, `/itemskin pack` to re-download
- **Safe & light**: background single-thread pack builder, disk read only on start and reload, limits on names, sizes, dimensions, counts and pack size; other plugins' item models are not overwritten by default
- **Config & messages**: all text in `messages.yml` (& codes and `&#RRGGBB`); missing keys auto-filled on start and reload

## Requirements

- Paper 26.1+ (Java 25)
- [HttpHub](https://github.com/clre20/HttpHub) for pack delivery
- Clients 1.21.4+ to see skins

## Commands & Permissions

Main command: `/itemskin`, alias `/items`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/itemskin` | Open the editor (console shows help) | Player / Console | `itemskin.use` | OP |
| `/itemskin help` | List available commands | Player / Console | `itemskin.help` | Everyone |
| `/itemskin open` | Open the skin editor | Player | `itemskin.use` | OP |
| `/itemskin check` | Show the held item's skin and your pack status | Player | `itemskin.check` | Everyone |
| `/itemskin pack` | Re-download the resource pack (5 s cooldown) | Player | `itemskin.pack` | Everyone |
| `/itemskin reload` | Reload config, messages and skins; rebuild the pack | Player / Console | `itemskin.reload` | OP |
| `/itemskin version` | Check GitHub for updates | Player / Console | `itemskin.version` | OP |

> - `itemskin.*` grants all of the above
> - With `apply.per-skin-permission`, each skin needs `itemskin.skin.<2d|3d>.<name>` (or `itemskin.skin.*`)

## Usage Examples

### 1. Add a 2D skin
Put `dragon_sword.png` into `plugins/ItemSkin/skins/2d/`, then run `/itemskin reload`.

### 2. Add a 3D skin
Export a Java item model from Blockbench, put the `.json` (preferably `model.json`) and `.png` files into `plugins/ItemSkin/skins/3d/magic_staff/`, then run `/itemskin reload`.

### 3. Skin an item
Run `/itemskin`, left-click the item into the center slot, choose the hidden tag option, pick a skin and click Save.

## Configurable Message Keys

All text lives in `messages.yml`; variables use `{name}`.
