# 林肯飞行家 2020（SYNC+ 破解主板 / Android 8）首轮车测清单

对应分支 `feat/aviator-android8`。目标：一次上车验证全部改造假设。
测试包：`mobile/build/outputs/apk/debug/mobile-debug.apk`（assembleStandaloneDebug 产物，已含身份资产）。

## 准备（车库内，约 10 分钟）

1. 卸载或停用车上现有的 CarPlay 盒子方案（拔掉盒子，避免同用）。
2. `adb connect <车机IP>:<端口>` 安装 APK：`adb install -r mobile-debug.apk`。
3. 打开 DiPlay → 设置：
   - 诊断区打开「**在屏幕上显示会话日志**」；
   - 确认「导航投影」区的「仪表与 HUD 导航」开关为开；
   - 昼夜间外观保持「跟随车机」。
4. **抓高德真值**：打开高德车机版，发起一个导航，让它播报两三个转向。
   日志浮层/导出日志里应出现 `AUTONAVI_STANDARD_SEND_RECV: KEY_TYPE=…, NEW_ICON=…` 等行——
   这就是这块板子解码盒认的协议真值（我们桥的字段若有出入，回来照此修正）。
5. **仪表媒体区探测**（为"仪表歌词"立项定案）：用任意安卓音乐 App（如网易云车机版）放歌，
   观察原车仪表的媒体区是否显示歌名/歌手。显示 = 解码盒映射了 Android MediaSession 元数据到仪表，
   "逐句歌词塞标题字段"方案可行；不显示 = 仪表歌词只能走高德通道魔改或不做。
6. 结束高德导航，退出高德。

## 测试瀑布（按顺序，每步看日志浮层）

| # | 操作 | 预期 | 验证的假设 |
|---|---|---|---|
| 1 | 打开 DiPlay，iPhone 解锁插线（原车 USB 口） | 弹出"信任此配件"→ CarPlay 界面出现 | **R1：MFi 身份 × 你的 iOS**（一票否决项） |
| 2 | 播放音乐；打开歌词页看逐句显示/滚动；苹果地图发起导航让它说话；（如有条件）打个电话 | 歌词随播放逐句高亮；三类声音分别正常、互不打断 | 音频解码 + 板子多路混音（歌词为 iPhone 渲染的视频流，随画面自证） |
| 3 | 方向盘媒体键：切歌/暂停/播放；长按语音键 | CarPlay 内响应 | 按键映射（盒子已实证，应直接过） |
| 4 | 开/关大灯（或遮挡光线传感器位置）各等 3 秒 | 日志出现 `Head-unit night mode ON/OFF`；CarPlay 深浅色跟随 | **R2：板子是否映射 uiMode** |
| 5 | 保持苹果地图导航中 | 原车仪表 + HUD 出现原生风格箭头/距离/路名 | **R3：高德桥注入链路** |
| 6 | 结束导航 | 仪表/HUD 指引消失（我们发 10019 结束事件） | 结束事件被解码盒接受 |

## 第 4 步不跟随时的手动验证

- DiPlay → 显示与性能 → 昼夜间外观 → 选「始终夜间」→ 返回 CarPlay：CarPlay 应立即变深色。
- 这证明手动覆盖链路 OK；uiMode 映射缺失留到第二轮用传感器方案补。

## 第 5 歗失败时的排查顺序

1. 日志里搜 `manifest receivers for AUTONAVI_STANDARD_SEND_RECV`——`none declared` 说明监听方是动态注册（正常，嗅探器能看到它是否在工作）；
2. 对比步骤"抓真值"的报文与 `DiPlay-AmapAuto: guidance sent` 行的字段差异（action、KEY_TYPE、extra 名）；
3. 若真值 action 不同（如板厂私有 action），只需改 `AmapAutoNavigationBridge.ACTION` 一个常量。

## 收工采集（带回家）

```bash
adb shell dumpsys uimode                      # 大灯开、关各一次
adb shell dumpsys display
adb shell cat /vendor/etc/audio_policy_configuration.xml > audio_policy.xml
```
加上 DiPlay 设置里「保存诊断报告」导出的日志一起带回。

## 已知预期内（非缺陷）

- 通话/Siri 语音上行：对方听不到你（Android 8 无 Opus 编码器，第二轮 libopus 补）；
- 无线 CarPlay 未移植（本轮仅有线）；
- 仪表 CarPlay 官方地图视频流未启用（等 Display 探测结果）。
