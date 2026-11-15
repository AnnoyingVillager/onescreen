# Changelog

本项目遵循语义化版本。发布日期使用本地日期 `YYYY-MM-DD`。

## [0.2.1] - 2026-11-15

### 修复

- 屏幕画框不再要求完整、坚固的承托方块，可放置在楼梯、栅栏等不完整方块的可点击表面。
- 画框不再保存或校验承托方块；原本贴附的方块被破坏后，画框会保持悬空而不会自毁或掉落。
- 修复本地 HTTP 服务离线时 WATERMeDIA 按 URL 缓存失败 MRL 的问题；服务恢复后点击右键菜单中的 `↻` 会重新生成可解析资源并恢复播放。
- 实机验证失败缓存恢复后，4K 视频可重新进入 `PLAYING`，并以 `3840x2160`、`format=GPU`、Vulkan 后端显示。


### Documentation

- Split human documentation into `docs/zh-cn/` and `docs/en/`.
- Added complete English counterparts for all user and secondary-development documents.
- Updated Chinese and English README, Agent documentation, and internal links to the language-specific document trees.
- Added `CHANGELOG.en.md` for English readers.

## [0.2.0] - 2026-11-15

高分辨率媒体与 GPU 纹理更新。

### 新增与改进

- OneScreen 不再固定将视频输出限制为 `1920x1080`；近距离 `LOD=MAX` 时由 WATERMeDIA 根据源、编解码器和设备能力选择尺寸。
- OpenGL 接入 WATERMeDIA GPU 纹理，Vulkan 接入 `VKEngine`、Minecraft Vulkan device 和外部纹理视图。
- 保留 AWT CPU 纹理回退，并优化视频像素到 `NativeImage` 的批量复制。
- 新增 `VideoLod`，按距离降档并使用阈值迟滞。
- WATERMeDIA 构建基线更新为 `3.0.0`，Binaries 更新为 `3.0.0`；新增 `3.0.0.x < 3.0.0 < 3.0.x` 的特殊版本排序。
- 修复 Vulkan 共享 graphics queue 的提交/等待/呈现同步。此前独立 compute/transfer queue 会导致 `VK_ERROR_DEVICE_LOST`。
- 沙箱实测 1080p 使用硬件解码；4K AV1 10-bit HLG 由 WATERMeDIA 选择软解但 GPU 纹理上传正常。
- Agent 文档统一迁移到 `agents/`，README 与 `docs/` 重新整理为人类使用说明和二次开发实现原理。

## [0.1.2] - 2026-11-14

播放控制与图标修正。

### 修复

- 放大播放控件左侧的手动刷新图标，提升小尺寸界面下的辨识度。
- 模组图标改为基于屏幕方块 `screen_frame` 材质的高对比度 `128×128` 版本，使用最近邻缩放并填充深色屏幕区域，避免在深色界面中与背景融为一体。
- 停止播放现在会释放媒体纹理并清除屏幕上的视频或图片画面；再次点击播放可重新加载画面。
- 在编辑器中点击暂停后，关闭编辑器仍保持暂停，不再因游戏菜单恢复而自动继续播放。

### 兼容说明

- 保持 WATERMeDIA `3.0.0.23` + Binaries `3.0.0.6` 与 WATERMeDIA `3.0.1` + Binaries `3.0.1` 的运行兼容。
- 对 WATERMeDIA `3.0.0.23` 仍仅提供初版兼容，未来可能移除。

[0.2.1]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.2.1

[0.2.0]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.2.0

[0.1.2]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.2

## [0.1.1] - 2026-11-14

屏幕控制与展示能力更新。

### 新增

- 播放控件左侧新增手动刷新按钮，可在停止播放或播放器异常后重新创建媒体会话。
- 屏幕可在自身平面内按 `-180°` 到 `180°` 的连续角度旋转，画面、九宫格相对位置和声音中心共用同一旋转几何。
- 新增“隐藏画框材质”勾选项，默认关闭；开启后隐藏静态屏幕画框模型，但保留动态媒体、点击区域和右键编辑能力。
- 新增 `/screen rotation <pos> <degrees>` 与 `/screen hideframe <pos> <true|false>` 管理命令，`/screen info` 会显示旋转角度和隐藏状态。
- 设置 payload、方块实体 NBT 和旧存档回退逻辑支持旋转角度与隐藏画框状态。

### 验证

- WATERMeDIA `3.0.1` + Binaries `3.0.1`：OpenGL 3.3 与 Vulkan 1.4.329 实机通过。
- 已验证停止、手动刷新、重新播放、任意角度旋转、隐藏画框、仍可右键编辑及退出重进后的配置持久化。
- WATERMeDIA `3.0.0.23` + Binaries `3.0.0.6`：Vulkan 1.4.329 下完成视频、硬件解码、停止、手动刷新与重新播放回归；该旧组合仅提供初版兼容，未来可能移除。

[0.1.1]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.1

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
