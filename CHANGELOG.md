# Changelog

本项目遵循语义化版本。发布日期使用本地日期 `YYYY-MM-DD`。

## [0.1.0] - 2026-11-14

首个公开版本。

### 新增

- Minecraft 26.2 / Fabric 的 1×1×1/16 六向屏幕画框。
- PNG、JPEG、WebP、MP4、HLS/M3U8 等 WATERMeDIA 媒体播放。
- 服务端权威的 URL、尺寸、距离、音量、静音、循环和九宫格位置同步。
- 适应、拉伸、平铺、填充、居中五种画面比例模式。
- 编辑器中的进度、循环、直播追帧、播放、暂停和停止控件。
- OpenGL 与 Vulkan 渲染路径。
- WATERMeDIA 3.0.0.23 初版兼容与 3.0.1 运行兼容。
- 独立 OneScreen 创意模式栏目。
- `/screen` 管理命令与 `config/onescreen.json` 距离上限配置。

### 修复

- 修复图片首帧尚未就绪时 `format() == null` 引发的渲染崩溃。
- 修复停止媒体后立即重新播放时的播放器生命周期空指针。
- 修复小窗口和较高 GUI 缩放下编辑器控件越界、字段错位。
- 修复六向薄画框在上下朝向的模型贴图和媒体面位置。

### 已知限制

- 多频道 IPTV 列表目前只播放第一个源。
- 公网直播流的长时间播放和重连仍需更多实机验证。
- 4K/8K 高帧率播放不是默认性能承诺。
- WATERMeDIA 3.0.0.23 仅提供初版兼容，未来可能移除。

[0.1.0]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.0
