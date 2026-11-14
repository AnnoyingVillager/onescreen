# 风险清单

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---:|---:|---|
| 26.2 render state API 记忆错误 | 高 | 高 | 查依赖源码；先做占位纹理；AI 必须引用真实签名 |
| WATERMeDIA 3 纹理提交与 Vulkan/OpenGL 抽象不明确 | 中 | 高 | 先确认官方示例和 jar 源码；渲染层集中在少数类 |
| URL 下载阻塞渲染线程 | 中 | 高 | 所有解析和解码异步；submit 只读快照 |
| 快速修改 URL 导致资源泄漏 | 高 | 高 | 严格状态机；旧会话先 close；会话 key 去重 |
| 区块卸载后继续播放声音 | 中 | 高 | 使用 BlockEntity load/unload 事件和全局断线清理 |
| 多个屏幕同时解码造成卡顿 | 高 | 中 | 限制活动会话数量；输出尺寸与距离 LOD 双重受限；提供排队或拒绝策略 |
| 把“可尝试硬解”误认为“必然硬解或零拷贝” | 高 | 中 | 检查 WATERMeDIA 日志；AWT 路径明确按 CPU 回读设计；4K/8K 不默认承诺 |
| 视频 CPU 转换与 NativeImage 拷贝开销 | 高 | 高 | 默认输出上限 1280x720；视频像素复制改为 native-order `IntBuffer`；1080p/4K 逐设备压测 |
| 大屏幕被错误剔除 | 中 | 中 | 正确扩展 getRenderBoundingBox；四向测试 |
| src/main 引用客户端类导致专用服务器崩溃 | 中 | 高 | split source sets；CI/AI 规则；runServer 验证 |
| URL 被当作恶意输入 | 中 | 高 | 管理员权限、协议白名单、长度限制、日志脱敏 |
| WATERMeDIA 3.0.0.23 旧布局维护成本 | 高 | 中 | 仅保留初版兼容；所有差异集中在 WaterMediaCompat；未来可按维护成本移除 |
| 旧 WATERMeDIA API 被 AI 再次写入 | 中 | 高 | AGENTS 明确禁用；编译检查 grep |
| Loom SNAPSHOT 导致构建漂移 | 中 | 中 | 记录解析版本；发布前固定或缓存 |
| HLS 直播流与 IPTV 列表行为差异 | 中 | 中 | 区分直播/VOD；多频道 IPTV 当前只取第一个源；暂停、拖动和重连需实机验证 |
| 媒体服务不稳定 | 高 | 中 | 超时、错误占位、有限重试、可恢复会话 |
| 纹理和音频释放顺序错误 | 中 | 高 | close 幂等；管理器集中关闭 |
| 第一版范围膨胀 | 高 | 中 | SCOPE 和 MILESTONES 作为门禁 |

## 高风险 API 区域

### 渲染

- `BlockEntityRenderer<BE, RenderState>`
- `extractRenderState`
- `SubmitNodeCollector`
- 动态纹理句柄
- `getRenderBoundingBox`

### 媒体

- WATERMeDIA 3 MRL 解析。
- MediaPlayer 创建和关闭。
- 图片/视频纹理读取。
- 音频后端和音量。
- 异步回调和主线程切换。

### 客户端生命周期

- BlockEntity unload 与 world disconnect 的先后顺序。
- 维度切换时是否仍能访问旧 `ClientLevel`。
- 同一位置的旧回调晚于新会话完成。

## 风险处理原则

遇到阻塞时按以下顺序：

1. 缩小到最小可复现问题。
2. 查询依赖源码和官方文档。
3. 保留占位渲染路径。
4. 减少同时存在的媒体功能。
5. 记录到 DECISIONS 和 RISKS。

不要通过增加 Mixin 或复制私有实现来绕过公开 API 问题。