# ArcaeaLocalB50

基于 Arcaea 本地 `st3` 数据库的 **Android 原生 Best 50 查分与高清长图生成工具**。

本项目由原网页端工具改造而来，采用 **纯 Java（无 Kotlin）** 与 **Android 原生 Material 3 UI** 开发，针对骁龙 845 等性能平台进行了深度优化，具备毫秒级离线图库生成、Root 自动提权提取、零外链纯离线出图等特性。

---

## 📱 核心功能特性

### 1. 🚀 Root 提权一键提取 st3
- 支持通过 **Magisk / KernelSU / APatch** 授权 Root 权限；
- 采用多路径动态探测（适配 Android 10+ 多用户空间与双开路径）与标准输出流管道传输，自动穿透 Magisk 命名空间隔离（`--mount-master`），免去手动寻找并复制 `/data/data/moe.low.arc/files/st3` 的繁琐操作。

### 2. 📁 多渠道文件选取支持
- 兼容 Android 系统 SAF (Storage Access Framework)；
- 全面兼容 **MT 管理器**、**ZArchiver** 等各类第三方文件管理器，支持无 Root 设备手动选取本地 `.st3` 数据库或导出的 `.csv` 成绩表。

### 3. 💾 查分数据离线持久化缓存
- 成功解析成绩后自动将结果安全存储于应用私有沙盒中；
- 下次进入应用**毫秒级自动还原历史查分数据**，无需重复读取或提取；
- 菜单栏配备「清空成绩缓存」选项，便于随时重置数据。

### 4. 🎨 1:1 对齐 SmartRTE 原版的 5 列经典高清长图
- **Java 原生 Canvas 渲染引擎**：仅需 ~300ms 即可生成 1700px 高清超长图，无需加载耗能高、卡顿严重的 WebView；
- **5 列网格经典排版**：
  - **默认 50 + 10（Best 50 + Overflow 10，共 60 首）**；
  - 支持出图数量调节（最低 50 首起步，且以 5 为步长递增）；
  - **Top 10 金橙色发光边框**（2px solid `#FF8C00` 与外发光光晕）；
  - **8 位规范化分数展示**：未满千万分时前导补 0（如 `09'995'961`，PM 成绩青色辉光）；
  - **标题防溢出截断**：长歌名自适应计算并在放不下时以 ASCII `...` 优雅省略；
  - 规范底部信息：`Generated At YYYY/MM/DD HH:mm:ss`；
  - 支持通过 Android 10+ **MediaStore API** 一键无感保存长图至系统相册（`Pictures/ArcaeaB50`）。

### 5. 🖼️ 全画廊可视化图库选择器
- **角色头像**：提供 4 列网格全画廊弹窗，直观滚动预览并选择全部 **128 款角色头像**；
- **长图背景**：提供 2 列宽屏卡片画廊弹窗，直接预览全部 **40 款背景立绘大图**；
- **段位挑战框**：覆盖 `Course Dan 1` 至 `Course Dan 40` 全 40 档段位横幅预览，并支持 `【0】不显示段位框 (None)`；
- **9 位好友码规范**：输入框自动每 3 位插入空格（如 `100 000 001`），保存时严格校验 9 位纯数字。

### 6. 🌐 资源离线化与在线热更新
- **零外链、无网秒出图**：曲绘封图（`Processed_Illustration/`）、评级图标（EX+、PM 等）、段位条、潜力值框、字体均打包内置于 APK 离线 Assets 中；
- **在线更新定数表**：支持随时从 GitHub 异步下载最新的 `constants.json` 与 `songlist` 并热更新至本地缓存。

---

## 🧮 潜力值计算公式 (Arcaea 7.0 规范)

### 1. 单曲 Rating 计算
单曲分数结算后，依据达成评级折算：
- 分数 $\ge 10,000,000$ (PM)：$\text{Rating} = \text{定数} + 2.0$
- 分数 $\ge 9,800,000$ (EX)：$\text{Rating} = \text{定数} + 1.0 + \frac{\text{分数} - 9,800,000}{200,000}$
- 分数 $\ge 7,000,000$ (Clear)：$\text{Rating} = \text{定数} + \frac{\text{分数} - 7,000,000}{2,800,000}$（包含 +0.2 Clear Bonus）
- 分数 $< 7,000,000$ (Track Lost)：$\text{Rating} = \max\left(0, \text{定数} + \frac{\text{分数} - 7,000,000}{2,800,000}\right)$

### 2. B50 总评潜力值
$$\text{MaxPTT} = \frac{\sum \text{Best 50} + \sum \text{Best 10}}{60}$$

---

## 🛠️ 构建与编译说明

- **开发语言**：纯 Java（JDK 17 / JDK 21）
- **构建工具**：Gradle 8.8 + Android Gradle Plugin 8.4
- **目标平台**：
  - `minSdkVersion`: 29 (Android 10)
  - `targetSdkVersion`: 34 (Android 14)
  - `ndk.abiFilters`: `['arm64-v8a']`

### 本地编译 APK
```bash
./gradlew assembleDebug
```
编译产物位于 `app/build/outputs/apk/debug/app-debug.apk`，项目根目录下亦提供最新的 `Arcaea_B50.apk`。

---

## 📜 开源协议与鸣谢
- 定数表与算法参考：[SmartRTE.github.io](https://github.com/SmartRTE/SmartRTE.github.io)
- Arcaea 游戏版权归 **lowiro** 所有，本应用仅供个人成绩离线分析与本地记录使用。
