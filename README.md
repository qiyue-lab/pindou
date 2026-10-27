# 拼豆图纸

一款本地离线运行的 Android 拼豆转图应用。将拍照或相册图片转换为带网格、符号、色号和用量统计的拼豆设计图，并导出高清 PNG。

## 功能

- 拍照或从系统照片选择器导入图片
- 自动处理大图降采样和 EXIF 照片方向
- 支持 16～80 列图纸，按原图比例计算行数
- 内置 72 色通用色卡（P01～P72）
- 使用 CIE Lab / CIEDE2000 匹配感知色差
- 通过适度误差扩散保留毛发等纹理细节
- 清理孤立近似色噪点，保留高对比轮廓和小细节
- 可移除与图片边缘相连的均匀深色背景
- 显示网格、行列坐标、格内符号和底部色卡图例
- 统计每种色号用量、总豆数和颜色数量
- 支持双指缩放、双击还原、网格与符号开关
- 通过系统文档选择器导出高清 PNG，无需存储权限
- 固定浅色界面，兼容系统深色模式设置

## 环境与构建

- Android Studio 配套 AGP 9.1.1
- JDK 21
- Android SDK 36.1
- 最低运行版本：Android 7.0（API 24）
- targetSdk：36

Windows：

```bat
gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

macOS / Linux：

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开项目，运行 `app` 到真机或模拟器。

## 使用方式

1. 点击“拍照”或“相册”导入图片。
2. 调整图纸列数；列数越大，细节越多，所需拼豆也越多。
3. 根据图片背景决定是否开启“去背景”。
4. 点击“生成”，查看颜色、符号和用量统计。
5. 双指缩放检查细节，按需要切换“网格”和“符号”。
6. 点击“保存高清 PNG 图纸”，选择保存位置。

## 转换流程

1. 读取图片并修正方向，限制超大图片的解码尺寸。
2. 按目标行列数划分图片，使用区域平均颜色表示每颗拼豆。
3. 将透明或识别为背景的区域留空。
4. 将 sRGB 转换到 CIE Lab，使用 CIEDE2000 选择最接近的色卡颜色。
5. 使用蛇形扫描和较低强度的误差扩散保留颜色过渡。
6. 将被足够多的同色邻居包围、且颜色接近的孤立杂色替换为周围颜色；高对比细节不参与该清理。
7. 统计用量，使用同一套绘制逻辑生成屏幕预览和高清 PNG。

## 项目结构

```text
app/src/main/java/com/xlabs/pindou/
├── MainActivity.kt                 # 导入、生成、导出和界面状态
├── ImageLoader.kt                  # 图片解码、降采样和方向修正
├── PatternView.kt                  # 图纸预览与缩放手势
└── pattern/
    ├── BeadColor.kt                # 通用色卡
    ├── BeadPattern.kt              # 图纸及用量数据模型
    ├── ColorMatcher.kt             # Lab 转换和 CIEDE2000 匹配
    ├── PatternGenerator.kt         # 像素化、去背景、扩散和噪点清理
    └── PatternRenderer.kt          # 网格、符号、坐标和图例绘制
```

单元测试位于 `app/src/test/java/com/xlabs/pindou/PatternGeneratorTest.kt`，覆盖颜色匹配、宽高比、透明像素、背景识别、用量统计、细节增强和噪点清理等情况。

## 色卡与适用范围

当前 P01～P72 是应用内置的**通用色号**，不是任何拼豆品牌的官方色号。屏幕颜色、导出图片和实际拼豆之间可能存在色差，实际制作前应对照实物色卡确认或建立品牌色卡映射。

“去背景”适合背景较均匀、颜色较深且与图片边缘相连的图片，不等同于通用 AI 抠图。复杂背景建议关闭该开关，或先使用其他工具完成主体抠图。
