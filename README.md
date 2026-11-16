# 静话 · QuietCalls

以本地规则为核心的 Android 来电识别与防骚扰应用，基于 [Pixel Telo](https://github.com/Pixel-Tailor-CN/PixelTelo) 修改。感谢原作者及贡献者，本仓库保留其 Apache License 2.0 许可证与署名。

当前版本 **0.8.0**；最低 Android 10，compile/target SDK 37，面向 Android 17 / AOSP。应用包名 `wang.lucian.quietcalls`，debug 包名 `wang.lucian.quietcalls.debug`。

## 拦截与通行

- 通讯录联系人默认优先放行；号码与前缀白名单直接放行。
- 支持境外、港澳台、推销、陌生来电，以及虚拟运营商、400、95、96 号段的独立规则。
- 支持号码、前缀和地域黑名单；地域通行仅豁免陌生号码拦截，仍受其他规则影响。
- 集团内部号码可放行 6 开头的 3～5 位短号。
- 陌生号码与地域通行条目逐条管理；地域支持搜索与多选。
- 实际拒接后发送单条静默聚合通知，不响铃、不振动。
- 设置中提供三大运营商防骚扰服务的官方办理渠道，是否开通及费用以运营商为准。

## 号码识别

默认在线识别直接访问 **360**，不经过 Pixel Telo 实时查询中转。开启联网后，待查询号码会发送给 360；可在设置中关闭联网查询。

“暂无标记”不表示号码安全。未知标签只展示；查询失败、超时或格式不兼容时放行。360 使用网页查询接口，没有可依赖的稳定性承诺，返回结构或访问策略变化可能导致识别不可用。

本地归属地来自固定版本 phone.dat，仅表示号码归属地区，不是实时位置，也不是骚扰名单。离线骚扰号码库初始为空，需要用户主动初始化；离线库更新仍使用原项目的同步服务。支持保留的自建实时查询配置，自建失败不回退默认服务。

## 构建

[Verify Android package](https://github.com/Lucian-Wang/QuietCalls/actions/workflows/verify-android.yml) 会在 `main` 提交或手动运行时编译并核验调试 APK；成功运行的 Artifacts 中可下载 `verified-debug-apk`。当前 CI 没有正式版新签名发布流程。

需要 **JDK 21**、Android SDK Platform **37.0**（SDK 包名 `platforms;android-37.0`，API 级别 37）；Gradle Wrapper 与插件版本固定在仓库中。设置 `ANDROID_HOME`，或在 `local.properties` 配置 `sdk.dir`。

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux/macOS：`chmod +x gradlew && ./gradlew :app:assembleDebug :app:lintDebug`。

APK 位于 `app/build/outputs/apk/debug/`。Debug 使用本机构建工具的签名。Release 签名通过 `SIGN_KEY_STORE_FILE`、`SIGN_KEY_STORE_PASSWORD`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PASSWORD` 环境变量配置；仓库不含密钥。不同签名的 APK 不能直接覆盖安装。

公开源码采用固定 versionCode **306**，与当前 0.8 开发版本保持一致，后续发布需递增。构建不依赖原项目 Git 提交数量，下载源码 ZIP 也能构建。

## 权限与验证

需由用户将本应用设为系统来电筛选应用，并按需授予联系人、通知等权限。联系人权限用于优先放行；原生来电显示在不同拨号器及 Android 版本上存在差异。

历史 0.8 开发记录包含编译、lint、模拟器启动和查询解析验证；2026-11-16 新包名 CI 已通过调试版编译和 APK 包名校验，该 CI 不执行 lint 或真机测试。真实来电、双卡、Google Pixel Directory 显示与 Android 17 真机仍需验证。`app/src/androidTest/` 是独立设备验证入口，不属于默认构建任务。

详见 [隐私说明](PRIVACY.md)、[上游与修改记录](UPSTREAM.md)、[版本记录](README-QUIET-CALLS.md)。另一个独立项目：[平桌面](https://github.com/Lucian-Wang/PingLauncher)。

## 许可证

应用源码采用 [Apache License 2.0](LICENSE)。第三方数据的来源、固定版本与许可声明保存在 `third_party/` 及 APK assets 中；查询结果属于对应数据提供方，不作为本项目可自由再分发的数据集。


## 应用标识

应用 ID：`wang.lucian.quietcalls`。本次包名变更后的 APK 会作为独立应用安装，不能覆盖升级旧包名版本；旧应用的数据和授权不会自动迁移，请先导出需要保留的数据，再安装并重新授权。

## 维护与反馈

维护者：[Lucian](https://lucian.wang/) · 邮箱：im@lucian.wang · [问题反馈](https://github.com/Lucian-Wang/QuietCalls/issues)
