# 风险清单

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---:|---:|---|
| 26.2 render state API 记忆错误 | 高 | 高 | 查依赖源码；先做占位纹理；维护者必须引用真实签名 |
| WATERMeDIA 3 纹理提交与 Vulkan/OpenGL 抽象不明确 | 中 | 高 | 先确认官方示例和 jar 源码；渲染层集中在少数类 |
| URL 下载阻塞渲染线程 | 中 | 高 | 所有解析和解码异步；submit 只读快照 |
| 快速修改 URL 导致资源泄漏 | 高 | 高 | 严格状态机；旧会话先 close；会话 key 去重 |
| 区块卸载后继续播放声音 | 中 | 高 | 使用 BlockEntity load/unload 事件和全局断线清理 |
| 多个屏幕同时解码造成卡顿 | 高 | 中 | 限制活动会话数量；输出尺寸与距离 LOD 双重受限；提供排队或拒绝策略 |
| 把“可尝试硬解”误认为“必然硬解或零拷贝” | 高 | 中 | 检查 WATERMeDIA 日志；AWT 路径明确按 CPU 回读设计；4K/8K 不默认承诺 |
| 视频 CPU 转换与 NativeImage 拷贝开销 | 高 | 高 | OneScreen 已取消固定分辨率上限并保留距离 LOD；GPU 纹理路径优先；CPU 回退仍需按设备压测 |
| 大屏幕被错误剔除 | 中 | 中 | 校验 `ScreenRenderer.shouldRender`、显示半径与 26.2 renderer 契约；六向测试 |
| src/main 引用客户端类导致专用服务器崩溃 | 中 | 高 | split source sets；CI/代码审查；runServer 验证 |
| URL 被当作恶意输入 | 中 | 高 | 管理员权限、协议白名单、长度限制、日志脱敏 |
| WATERMeDIA 旧测试版布局维护成本 | 中 | 中 | `3.0.0` 为正式基线；`3.0.0.x` 仅 best-effort；所有差异集中在 WaterMediaCompat |
| 旧 WATERMeDIA API 被重新引入 | 中 | 高 | `agents/` 规则明确禁用；代码审查与编译检查 |
| Loom SNAPSHOT 导致构建漂移 | 中 | 中 | 记录解析版本；发布前固定或缓存 |
| HLS 直播流与 IPTV 列表行为差异 | 中 | 中 | 区分直播/VOD；多频道 IPTV 当前只取第一个源；暂停、拖动和重连需实机验证 |
| 媒体服务不稳定 | 高 | 中 | 超时、错误占位、有限重试、可恢复会话 |
| 纹理和音频释放顺序错误 | 中 | 高 | close 幂等；管理器集中关闭 |
| 功能范围膨胀 | 高 | 中 | 用 `SCOPE.md`、`TESTING.md` 和发布门槛作为门禁 |

## 高风险 API 区域

### 渲染

- `BlockEntityRenderer<BE, RenderState>`
- `extractRenderState`
- `SubmitNodeCollector`
- 动态纹理句柄
- `ScreenRenderer.shouldRender` 与距离剔除

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

优先使用公开 API。只有在底层同步或生命周期契约无法由公开 API 表达时，才允许增加范围极小、可验证且有文档记录的 Mixin；不得通过复制整套私有实现来绕过 API。

## 2026-11-15 新增风险：Vulkan 队列与纹理生命周期

- WATERMeDIA `VKEngine` 假定媒体写入和 Minecraft 采样发生在同一队列，并通过 `VKContext.queueLock()` 串联双方提交。
- 若使用独立队列但不提供跨队列 semaphore，或只给 WATERMeDIA 私有锁而 Minecraft 提交不受该锁保护，会造成未定义行为和 `VK_ERROR_DEVICE_LOST`。
- 当前缓解：共享 Minecraft graphics queue；`VulkanQueueSynchronization` 按 `VkQueue` 句柄提供统一锁；Mixin 覆盖提交、`vkQueueWaitIdle` 与 `vkQueuePresentKHR`。
- 修改 Minecraft/Vulkan、WATERMeDIA 或驱动版本后，必须重新检查注入目标与 4K 连续播放。
- `ExternalMediaTexture` 仍依赖 WATERMeDIA 返回稳定的当前 `VkImageView`；释放顺序、窗口重建和图形后端切换仍需扩展测试。
