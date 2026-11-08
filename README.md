# 搜狗皮肤解锁 (SogouSkin)

LSPosed 模块：解锁搜狗输入法的付费皮肤。

适用于 **搜狗输入法 20.17.0**（versionCode 2620），基于 LSPosed API 102。

## 功能

- 付费皮肤按已购买处理，商店直接下发皮肤包
- 修复失效的皮肤下载域名

## 原理

付费皮肤是否可用由皮肤模型上的本地字段决定。模块在皮肤详情页与预览页的绑定方法中，把
`payment` 置为 1、`vipOnly` 置为 `"0"`、`newVipType` 置为 0，商店即按已购下发皮肤包。

另外，20.17.0 内置的下载域名 `download.sginput.qq.com` 已经停止服务（返回 404），
模块把请求重写到仍在提供同一接口的 `srv.android.shouji.sogou.com`。

Hook 点：

| 类 | 方法 | 作用 |
| --- | --- | --- |
| `com.sogou.theme.SmartThemeSkinDetailActivity` | `T2` / `s2` / `E2` | 皮肤详情页支付态 |
| `com.sogou.theme.ThemePreviewActivity` | `L2` | 皮肤预览页支付态 |
| `com.sogou.theme.network.b` | `e` | 下载域名重写 |

## 安装

1. 安装 APK
2. 在 LSPosed 中启用模块
3. 作用域勾选「搜狗输入法」（`com.sohu.inputmethod.sogou`）
4. 强行停止搜狗输入法后重新打开

## 兼容性

选择器基于类名和方法名，版本不完全匹配时也能安装，最多是 Hook 不上，不会导致崩溃。
日志标签为 `SogouSkin`，可在 LSPosed 日志中查看是否生效。

## 构建

需要 JDK 21、Android SDK build-tools 37、LSPosed API 102 的 `classes.jar`。

```powershell
.\build.ps1
```

产物输出到 `build-direct/sogouskin-<version>.apk`。

## 说明

仅供学习与个人研究使用，请在符合当地法律法规的前提下使用。
