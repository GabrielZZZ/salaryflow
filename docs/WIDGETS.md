# 桌面小组件（今日已赚）

薪流 提供 2x2 / 4x2 / 4x3 三种桌面小组件，把 App 内的英雄卡片
（「今日已经赚了」实时金额）搬到了主屏幕，与 App 内显示保持同一套
视觉令牌与计算口径。

## 产品形态

| 尺寸 | 内容 |
| --- | --- |
| 2×2 | 标签 + 状态徽章 + 混合字号金额 + 倒计时/秒薪 + 人生时间换算行（☕/🎧）+ 进度条 |
| 4×2 | 2×2 的基础上增加班次时间行（09:00 — 17:30）与元信息行（工作进度 / 已计薪 / 今日预计） |
| 4×3 | 完整英雄卡：金额、秒薪、时薪数据条（时薪 / 分钟薪 / 今日预计）、人生时间换算行（☕ / 🎧 / 📱）或今日到手分解、班次进度、倒计时元信息 |

## 总包分解（五险一金 / 个税 / 到手）

设置页开启「拆分五险一金与个税」后（`profile.deductionsEnabled`）：

- App 仪表盘英雄卡元信息行新增：`五险一金 ¥xxx · 个税 ¥xxx`——工作日为当日
  实时累计，休息日为本周累计（与「本周已经赚了」同口径）
- 4×3 小组件的换算行切换为「五险一金 · 个税」（工作日当日/休息日本周）；
  4×2 元信息第三格切换为「五险一金」；2×2 保持精简
- 大数字始终为总包口径——公司为你的时间支付的价格；「五险一金」一项含
  社保个人 10.5% 与公积金，分解行回答「扣了多少、去了哪」

估算口径（设置中可调公积金比例与专项附加扣除）：

- 社保个人合计 10.5%（养老 8% + 医疗 2% + 失业 0.5%，按城市略有差异，固定估算）
- 住房公积金按用户配置比例（默认 12%）
- 个税：月度税率表速算（起征点 5000 + 专项附加扣除可配），未采用全年累计预扣口径
- 以上为估算值，实际以工资条为准；各条线随计薪秒数线性累计，休息日为 0

人生时间换算行与 App 仪表盘的快捷卡片同源（咖啡 ¥32 / AirPods
¥1,899 / 手机 ¥7,999），按当前秒薪折算为「需要工作多久」，以浅色注释
文字呈现（紧凑单位：分钟/小时/天），薪资配置变化时自动重算。

视觉令牌与 `DESIGN.md` 一一对应：藏青卡 `#14213A` + 多层光影（右上
radial 氛围光 + 斜向 sheen）、混合字号等宽数字（¥ 与小数弱化、整数
主体，2×2/4×2/4×3 分别 36/52/58sp）、进度冰蓝 `#B7D8F0`、轨道
`#33455F`、元信息 `#9EA89D / #E7EADF`。右上角以低透明度「钞票线稿」
水印（外框 + 内边线 + ¥ 圆章，`drawable/widget_watermark_banknote`）
呼应货币主题。资源定义在
`apps/web/android/app/src/main/res/`（`drawable/widget_*`、
`layout/widget_*`、`values/widget_colors.xml`、`xml/widget_*.xml`）。

### 状态模型（五种班次状态）

右上角徽章与内容跟随状态自动切换，秒级服务运行时全部逐秒刷新：

| 状态 | 徽章 | 大数字 | 秒薪行 | 附加信息 |
| --- | --- | --- | --- | --- |
| 工作中 | `● 实时中`（lime） | 今日已赚（每秒跳动） | `+ ¥0.02755 / 秒` | 距下班 HH:MM:SS |
| 免费午休 | `午休中`（orange） | 今日已赚（冻结） | 秒薪照常 | 距开工 HH:MM:SS |
| 上班前 | `上班前`（灰） | ¥0.00 | `距上班 HH:MM:SS`（逐秒） | — |
| 下班后 | `已收工`（灰） | 今日全额 | `距上班 X天 HH:MM:SS`（逐秒） | — |
| 休息日 | `休息日`（灰） | **本周已赚**（日薪×本周已完成工作日） | `距上班 X天 HH:MM:SS`（逐秒） | 本周 X天 · 本月 X天 · 时薪；数据条第三格切为「本月已入账」 |

工作日由设置页的「工作日」七格选择器配置（`profile.workdays`，周一~周日
任意勾选，旧数据默认双休）。休息日不计薪：`getWorkedPaidSeconds` 直接
返回 0，小组件与 App 仪表盘同步切换为休息日专属内容（本周/本月已入账
累计 + 逐秒「距上班」倒计时）。

点击小组件任意位置打开 App。

## 刷新架构（关键设计）

小组件金额是「时间的一元函数」：`今日已赚 = 已计薪秒数 × 秒薪`，
渲染时按当前时间现算，因此不需要 App 存活，也不需要存储中间状态。
刷新按「金额是否在变化」分级：

```text
有小组件 + 配置可计算 → WidgetLiveTickerService（前台服务，specialUse）常驻
├── 每秒对齐秒边界推送一次 RemoteViews：
│   计薪中金额跳动；上班前/下班后/周末显示逐秒「距上班」倒计时
├── 常驻通知同步刷新（每 5 秒节流）：工作中显示金额，其余显示倒计时
├── 无 wakelock：设备休眠时 Handler 自动停转，唤醒后首帧现算，无需追赶
└── 移除全部小组件 → 自动 stopSelf，零后台占用
兜底
├── updatePeriodMillis = 30 分钟（系统兜底刷新）
├── TIME_SET / TIMEZONE_CHANGED / DATE_CHANGED / BOOT_COMPLETED /
│   MY_PACKAGE_REPLACED → 立即重算 + 重排闹钟 + 对齐服务状态
└── 秒级服务被系统回收时，闹钟链自动重建服务（精确闹钟权限持有时可后台拉起）
```

资源结论：持续开销 = 屏幕点亮期间的每秒 RemoteViews 推送（每次亚毫秒级
CPU，无 wakelock）；熄屏后完全静止。夜间充电挂机零唤醒。

## App 被划掉 / 强停后小组件的表现

- **上滑划掉（进程被杀，未进入停止态）**：前台服务由系统按 START_STICKY
  自动重建，闹钟链继续触发；同时倒计时行使用 Chronometer 倒计时模式
  （`setChronometerCountDown`），由**桌面启动器进程**驱动逐秒走——与
  App 进程死活无关。实测 `am kill` 后倒计时仍精确递减。
- **设置里强制停止（Android 停止态）**：Android 15+ 上启动器会把
  停止态应用的小组件替换为系统占位符，重新打开一次 App 即恢复。
  这是平台级行为，所有小组件应用一致。
- Chronometer 显示为累计小时制（`41:28:09` = 1天17小时28分9秒）。

## 配置同步（Web → 原生）

App 是 Capacitor WebView 壳，薪资配置存在 WebView localStorage
（`salary-flow.profile.v1`）。原生小组件无法直接读取，因此通过
`WidgetSync` 插件单向同步：

- JS 侧：`apps/web/src/lib/widgetSync.ts`（使用注入的
  `window.Capacitor.nativePromise`，不引入 `@capacitor/core` 依赖，
  Web/PWA 下自动 no-op）
- 触发点：App 启动（`main.tsx`）与每次保存设置（`saveProfile`）
- 原生侧：`WidgetSyncPlugin.sync` 把 JSON 快照写入 SharedPreferences
  （`salaryflow_widget`），随后立即重算并刷新小组件

计算引擎在 `widget/SalaryMath.java`，是 `packages/core` 的 1:1 Java
移植（含跨零点班次、免费午休扣减、时薪/日薪/年薪折算、居住成本），
显示格式与 `Dashboard.tsx` 完全一致（¥ 千分位两位小数、秒薪 5 位小数、
`X小时X分钟` 时长）。

## 添加小组件

- 常规路径：桌面长按 → 小组件 → 薪流，三种尺寸任选。
- 调试路径（仅 debug 签名构建）：

```bash
adb shell am start -n com.salaryflow.app/.widget.WidgetPinActivity --es size 4x2
# size ∈ {2x2, 4x2, 4x3}，弹出系统确认框后点「添加到主屏幕」

# 调试构建还支持临时覆盖薪资配置（验证不同班次状态）：
adb push profile.json /data/local/tmp/salaryflow-widget-profile.json
adb shell am start -n com.salaryflow.app/.widget.WidgetPinActivity --ez syncFromFile true --es size skip
```

`WidgetPinActivity` 在 release 构建里只执行钉组件请求，不读写文件。
传 `--es size skip` 可只同步配置、不弹钉组件确认框。

> 注意：秒级跳动服务运行期间，`uiautomator dump` 会因系统无法进入
> idle 而失败（每秒一次 RemoteViews 更新持续产生事件）。自动化调试前
> 可先同步一个非计薪时段的配置让服务停止。

## 布局自适应

启动器实际分配的格子与元数据目标不一定一致，渲染时按
`AppWidgetOptions` 上报的宽高动态选择布局：

- 宽 < 220dp → 2×2 布局
- 宽 ≥ 220dp 且高 < 140dp → 4×2 布局
- 其余 → 4×3 布局
- 尺寸信息缺失时使用各 Provider 自身的默认布局

因此把小组件拖大拖小会自动切换内容形态，无需重新添加。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `RECEIVE_BOOT_COMPLETED` | 开机后重排刷新闹钟 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 计薪时段的秒级跳动前台服务 |
| `POST_NOTIFICATIONS` | 显示「今日已赚」实时通知；拒绝时服务照常工作，仅通知不显示 |
| `SCHEDULE_EXACT_ALARM`（运行时可选） | 闹钟精确对齐分钟/变化点 + 允许后台启动前台服务；未授予时自动退化为非精确闹钟，功能不受影响 |

## 已知边界

- 强制停止 App 后，Android 会暂停其全部闹钟与小组件刷新，直到用户再次打开 App（系统行为）。
- Doze 深度休眠中非唤醒闹钟会延迟到设备唤醒时补发；由于金额渲染时现算，补发瞬间即为正确值。
- 计薪时段内若用户停留在其他全屏应用，秒级推送仍在进行（不可见但开销极小）；如需进一步省电可后续按「启动器是否前台」做门控。
