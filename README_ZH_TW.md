<div align="center">
  <img src="docs/icon.png" alt="App 圖示" width="100" />
  <h1>PadkaHub(RikkaHub)</h1>

基於 RikkaHub 改進的版本，核心是一顆懸浮球 —— 一個常駐在所有應用程式之上的 AI 助手。

[English](README.md) | [简体中文](README_ZH_CN.md) | 繁體中文
</div>

> [!NOTE]
> 這是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的**非官方分支**，目前同步上游 **3.3.0**。
> 上游功能全部保留，並在其上圍繞**懸浮球**做了一組功能：球常駐在最上層，懸浮對話窗與截圖提問都能一鍵喚起。
> 本專案與 RikkaHub 作者無關；若原版已足夠使用，建議直接使用原版。

## 🚀 下載

- 最新 APK：見 [**Releases**](https://github.com/N1H1-4/rikkahub-floatingball/releases)（`arm64-v8a`，Android 8.0+）
- 套件名是 `me.rerere.rikkahub.fb`，所以本分支**可以和官方 RikkaHub 並存安裝**，互不影響資料
- 系統需求 Android 8.0+；應用程式內更新檢查的是本儲存庫的 Releases

## ✨ 本分支的改動

### 🐰 懸浮球

原本的桌面寵物與「生成中」氣泡合併成一顆常駐最上層的球。

- 形象可自訂（相簿圖片 / Emoji / 圖片連結 / 內建圖案），支援大小、不透明度、貼邊吸附、貼邊半隱
- AI 生成時球會變成變形自轉的動畫形象，一眼能看出正在思考
- **單擊**：有正在追蹤的對話就展開懸浮對話窗，否則直接進入選區截圖（可在設定裡改成任意動作）
- **長按**：朝螢幕內側展開半圓選單，最多 5 個快捷動作；點中間的球即關閉懸浮球
- 歷史對話、切換助手改成球旁邊的小浮層清單，不再跳回應用程式內

可用動作：懸浮窗新對話 · 歷史對話 · 開啟 PadkaHub · 截全螢幕並提問 · 選區截圖 · 關閉懸浮球 · 開啟設定 · 統計頁面 · 切換助手。

### 💬 懸浮對話窗

- 可拖曳；拖右下角把手能調整尺寸，寬高都可拉到接近整螢幕
- 顯示目前對話的歷史訊息，支援 Markdown 與 LaTeX 公式，不必切回應用程式就能繼續閱讀
- 輸入區保留快捷訊息、模型切換、思考深度
- 聊天頁頂欄新增「開啟懸浮窗」按鈕，一鍵把目前對話彈成浮窗

### ✂️ 選區截圖提問

- 框選一塊區域、標註，然後直接送進對話
- 支援畫幅比例預設、畫筆與顏色、復原與重做
- 四個出口：複製到剪貼簿 · 儲存到相簿 · 傳送到新對話 · 傳送到既有對話
- 全螢幕截圖會跳過編輯器，直接帶著整張圖開啟對話

### 🖊️ 手寫筆畫板

- 預設「僅手寫筆」模式，手掌或手指壓螢幕不會留下痕跡；可切換成手指也能畫
- 筆模式下單指拖曳畫紙、雙指捏合縮放（1×–4×）
- 6 種筆刷，含壓感筆與毛筆

### 📤 分享到新對話

- 從任何應用程式分享圖片、文件、PDF、Office 檔案，選一個助手即可進入新對話，附件已經帶好

## 📚 繼承自上游

Material 3 Expressive 設計 · 多供應商（OpenAI / Google / Anthropic 相容的自訂 API / URL / 模型）·
MCP 與 Skills · 工作區（基於 proot 的 Linux 智能體環境）· Phone Use · 媒體生成 · Web 多端存取 ·
Markdown 渲染（程式碼高亮、數學公式、表格、Mermaid）· 搜尋服務 · 供應商 QR 碼匯入匯出 · 智能體自訂 ·
基於檔案的記憶 · AI 翻譯 · 自訂 HTTP 請求標頭與請求內容 · SillyTavern 角色卡匯入。

## ⚠️ 已知限制

- 不支援筆桿按鍵：HyperOS 不向應用程式回報 `buttonState`，因此無法實作筆桿鍵快捷操作
- 畫紙只在放大後才能平移，1× 時正好鋪滿螢幕
- Release APK 只出 `arm64-v8a`；其他 ABI 可自行從原始碼建置 debug 版
- 應用程式內更新檢查的是本儲存庫的 Releases，不再走官方更新伺服器

## 🛠️ 建置

需要 JDK 21+、Android SDK Platform 37（含 minor API level 2）、Node 22+ 與 pnpm 11。

```bash
# web 模組內嵌 React 前端，先安裝一次相依套件
cd web-ui && pnpm install --frozen-lockfile && cd ..

# google-services 外掛需要 google-services.json
# （本機建置放一份佔位檔案即可）

./gradlew assembleGithubDebug   # debug 包，套件名 me.rerere.rikkahub.debug
./gradlew assembleGithubRelease # release 包（簽章從 local.properties 讀取）
```

Release 簽章讀取 `local.properties` 裡的 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`。

### 同步上游

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git merge upstream/master
```

## 🙏 致謝

- 原始專案：[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)，作者 [re-ovo](https://github.com/re-ovo)
- 懸浮窗基於 [FloatingX](https://github.com/Petterpx/FloatingX)
- 圖示來自 [HugeIcons](https://hugeicons.com/)，手寫渲染基於 [androidx.ink](https://developer.android.com/jetpack/androidx/releases/ink)

## 📄 授權

基於 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 開源，與原專案一致。本分支的修改程式碼
同樣公開在本儲存庫中。
