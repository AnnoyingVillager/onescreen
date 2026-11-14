# 待确认问题

这些问题不阻塞 M0/M1，但应在对应阶段开始前决定。

## 媒体

- [x] 支持 HLS/M3U8；OneScreen 直接接受 HTTP/HTTPS 播放列表。多频道 IPTV 列表暂只播放第一个源。
- [ ] 是否需要单独区分“有视频无声音”和“音频纯播放”界面？
- [x] 活动会话上限确定为 8。
- [ ] 视频最大分辨率是否限制为 1080p？

## 尺寸与几何

- [ ] 屏幕默认尺寸固定为 4×2.25，还是允许玩家首次放置时就选择尺寸？
- [ ] 屏幕是否严格要求 16:9，还是保留 URL 原始宽高比？
- [ ] 超宽屏幕是否允许视觉上穿出相邻方块？

## URL

- [ ] 是否允许 `water://local/`？如果允许，根目录如何限制？
- [ ] 是否需要域名 allowlist/blocklist？
- [ ] 是否需要密码或多段命令避免 URL 出现在命令历史？

## 音频

- [ ] 最小和最大可听距离最终值。
- [ ] 视频离开可听距离时，画面继续、声音归零，还是整个媒体暂停？
- [ ] 多个屏幕同时播放时，是否限制总音频 source 数？

## 发布

- [x] 最终显示名称为 OneScreen。
- [x] Mod ID 为 `onescreen`，包名为 `dev.onescreen`，作者为 AnnoyingVillager 与 OneScreen contributors，许可证为 MIT。
- [x] 0.1.x 先通过 GitHub Releases 发布；CurseForge 与 Modrinth 在完成更完整的直播流和多人验收后再评估。
- [x] README 明确要求 WATERMeDIA 与 Binaries 使用同系列版本，并在 Release 说明中重复列出。
