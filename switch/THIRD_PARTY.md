# Switch 依赖说明

本模块原创代码沿用 SunnyTV 的 GPL-3.0-only。接口与业务规则参考本仓库 Android v0.2.8，未复制 NXMP/pPlay 的界面或播放器实现。

构建使用 devkitPro 的 libnx、libmpv、FFmpeg、SDL2、SDL2_ttf、SDL2_image、curl 及其传递依赖。具体包版本由构建产物 `TOOLCHAIN_PACKAGES.txt` 记录。分发二进制前需保存该次构建所有链接依赖的相应许可证、补丁与对应源码，不以本页代替完整声明。

依赖来源与本轮选型核对：

- devkitPro/pacman-packages `switch/libmpv`：mpv 0.39.0 + Horizon 补丁，GPL 构建。
- `switch/ffmpeg`：FFmpeg 7.1 + libnx/nvtegra 补丁，GPL 构建。
- libnx：ISC；curl：curl license；SDL2 系列：以对应包源代码的实际许可证为准。
- UI 使用设备系统共享字体；不将其放入仓库或 NRO 重新分发。

本轮新增代码未复制 Moonfin、NXMP 或 pPlay 源码。阅读公开构建配方用于确定可用依赖；另参考 Switchfin（Apache-2.0）的 libmpv 图形接入设计，未复制其代码、界面或二进制，参考路径及链接见 README。
