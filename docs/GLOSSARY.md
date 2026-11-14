# 术语表

## Block

世界中的方块。第一版只有一种屏幕方块。

## BlockEntity

附着于方块的世界数据容器。保存 URL、尺寸和音量；不保存客户端媒体播放器。

## BlockEntityRenderer

为方块实体提交动态几何的客户端渲染器。26.2 使用 render state 与 `submit`。

## RenderState

某一帧渲染所需数据的快照。避免渲染器直接读取可变对象。

## SubmitNodeCollector

26.2 中用于提交文本、几何和特效的渲染队列接口。

## Client Source Set

`src/client`，只进入客户端 JAR/运行环境。渲染和媒体代码应放在这里。

## Server Authoritative

服务器决定 URL 和配置，客户端不能自行成为权威数据源。

## MediaSession

一个屏幕在某个客户端上的媒体播放生命周期。

## MRL

WATERMeDIA 3 的媒体资源解析入口，用于把 URL 解析为可播放媒体。

## MediaPlayer

WATERMeDIA 3 的图片/视频/声音播放对象。

## Texture Handle

媒体解码器向后端提供的动态纹理标识。必须通过 WATERMeDIA 的后端抽象使用。

## Identifier

26.x 中替代 `ResourceLocation` 的资源标识符。

## ValueInput / ValueOutput

26.x 方块实体读写接口，替代旧的 `CompoundTag + HolderLookup.Provider` 存档方法。

## Update Tag

玩家进入区块时用于同步 BlockEntity 数据的 NBT 快照。

## Update Packet

BlockEntity 数据变化时发送给跟踪客户端的更新包。

## Payload

自定义网络消息的数据对象。第一版只有在需要客户端请求时使用。

## Source Set Separation

把 common、client 和 server 代码分到不同编译集，防止专用服务器加载客户端类。

## Session Leak

已经不需要的媒体会话没有关闭，导致线程、内存、纹理或音频持续占用。