<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>PadkaHub(RikkaHub)</h1>

基于 RikkaHub 改进的平板向版本 —— 悬浮球、常驻悬浮对话窗、选区截屏标注，以及适配手写笔的画板。

[English](README.md) | [繁體中文](README_ZH_TW.md) | 简体中文
</div>

> [!NOTE]
> 这是 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 的**非官方分支**，当前同步上游 **3.3.0**。
> 上游的功能全部保留，在此基础上增加了一组面向大屏 + 手写笔的功能（在小米平板 6 + 灵感触控笔上开发测试）。
> 本项目与 RikkaHub 作者无关；如果原版已经够用，建议直接使用原版。

## 🚀 下载

- 最新 APK：见 [**Releases**](https://github.com/N1H1-4/rikkahub-floatingball/releases)（`arm64-v8a`，Android 8.0+）
- 包名是 `me.rerere.rikkahub.fb`，所以本分支**可以和官方 RikkaHub 并存安装**，互不影响数据
- 系统要求 Android 8.0+；应用内更新检查的是本仓库的 Releases

## ✨ 本分支的改动

### 🐰 悬浮球

原来的桌面宠物和“生成中”气泡合并成一个常驻最上层的球。

- 形象可自定义（相册图片 / Emoji / 图片链接 / 内置图案），支持大小、不透明度、贴边吸附、贴边半隐
- AI 生成时球会变成变形自转的动画形象，一眼能看出正在思考
- **单击**：有正在跟的会话就展开悬浮对话窗，否则直接进入选区截屏（可在设置里改成任意动作）
- **长按**：朝屏幕内侧展开半圆菜单，最多 5 个快捷动作；点中间的球即关闭悬浮球
- 历史会话、切换助手改成球旁边的小浮层列表，不再跳回应用里

可用动作：悬浮窗新对话 · 历史会话 · 打开 PadkaHub · 截全屏并提问 · 选区截图 · 关闭悬浮球 · 打开设置 · 统计页面 · 切换助手。

### 💬 悬浮对话窗

- 可拖动；拖右下角把手能改尺寸，宽高都可以拉到接近整屏
- 显示当前会话的历史消息，支持 Markdown 与 LaTeX 公式，不用切回应用就能继续看
- 输入区保留快捷消息、模型切换、思考深度
- 聊天页顶栏新增「打开悬浮窗」按钮，一键把当前会话弹成浮窗

### ✂️ 选区截屏提问

- 框选一块区域、标注，然后直接发给对话
- 支持画幅比例预设、画笔与颜色、撤销重做
- 四个出口：复制到剪贴板 · 保存到相册 · 发送到新对话 · 发送到已有对话
- 全屏截取会跳过编辑器，直接带着整张图打开对话

### 🖊️ 手写笔画板

- 默认「仅手写笔」模式，手掌或手指压屏不会留痕；可切成手指也能画
- 笔模式下单指拖动画纸、双指捏合缩放（1×–4×）
- 6 种笔刷，含压感笔与毛笔

### 📤 分享到新对话

- 从任何应用分享图片、文档、PDF、Office 文件，选一个助手即可进入新对话，附件已经带好

## 📚 继承自上游

Material 3 Expressive 设计 · 多供应商（OpenAI / Google / Anthropic 兼容的自定义 API / URL / 模型）·
MCP 与 Skills · 工作区（基于 proot 的 Linux 智能体环境）· Phone Use · 媒体生成 · Web 多端访问 ·
Markdown 渲染（代码高亮、数学公式、表格、Mermaid）· 搜索服务 · 供应商二维码导入导出 · 智能体自定义 ·
基于文件的记忆 · AI 翻译 · 自定义 HTTP 请求头与请求体 · SillyTavern 角色卡导入。

## ⚠️ 已知限制

- 不支持笔杆按键：HyperOS 不向应用上报 `buttonState`，所以无法实现笔杆键快捷操作
- 画纸只在放大后才能平移，1× 时正好铺满屏幕
- Release APK 只出 `arm64-v8a`；其他 ABI 可以自行从源码构建 debug 版
- 应用内更新检查的是本仓库的 Releases，不再走官方更新服务器

## 🛠️ 构建

需要 JDK 21+、Android SDK Platform 37（含 minor API level 2）、Node 22+ 与 pnpm 11。

```bash
# web 模块内嵌 React 前端，先装一次依赖
cd web-ui && pnpm install --frozen-lockfile && cd ..

# google-services 插件需要 google-services.json
# （本地构建放一份占位文件即可）

./gradlew assembleGithubDebug   # debug 包，包名 me.rerere.rikkahub.debug
./gradlew assembleGithubRelease # release 包（签名从 local.properties 读取）
```

Release 签名读取 `local.properties` 里的 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`。

### 同步上游

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git merge upstream/master
```

## 🙏 致谢

- 原始项目：[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)，作者 [re-ovo](https://github.com/re-ovo)
- 悬浮窗基于 [FloatingX](https://github.com/Petterpx/FloatingX)
- 图标来自 [HugeIcons](https://hugeicons.com/)，手写渲染基于 [androidx.ink](https://developer.android.com/jetpack/androidx/releases/ink)

## 📄 许可证

基于 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 开源，与原项目一致。本分支的修改代码
同样公开在本仓库中。
