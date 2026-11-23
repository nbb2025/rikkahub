# iAgent Android 开发与构建

## 环境

使用 JDK 21、Android SDK Platform 37.2、Build Tools 37.0.0、Node.js 22 和 pnpm 11。Gradle 使用仓库自带的 wrapper。Android Studio 也可以通过 SDK Manager 安装对应平台。

```sh
sdkmanager "platforms;android-37.2" "build-tools;37.0.0"
pnpm --dir web-ui install --frozen-lockfile
./gradlew :app:assembleGithubDebug
```

调试包使用 `dev.iagent.app.debug`，正式构建使用 `dev.iagent.app`。调试包和正式构建可以同时安装。更新同一包名时，设备要求 APK 使用同一签名。

## 工作台和接口

| 功能 | Android 实现 |
| --- | --- |
| 访客模式 | 进入聊天和创作首页，浏览线上模型目录和 Skills.sh。点击需要账号的操作后打开登录页，登录后回到目标页面。 |
| iAgent 登录 | 使用 `/api/v1/auth` 接口，支持二步验证。会话由 Android Keystore 加密保存，退出后访客页面无法打开账号的本地会话。 |
| 模型与分组 | 公开目录读取 `/site-api/models?include_group_pricing=1`。登录后再读取 `/api/v1/groups/available`，选择器仅使用账号有权限的 iAgent 渠道。 |
| 排序与过滤 | 延用目录返回的 `group_order` 和 `sort_rules`。同优先级或缺少规则时使用名称的自然降序。聊天模型列表会排除 Auto Review。 |
| 创作 | 根据模型协议加载图片、视频、音频和首尾帧等参考选项。任务和本地作品由创作仓库管理。 |
| 充值 | 使用站点支付配置和 `/api/v1/payment` 接口。客户端显示估价，实际金额和到账状态以 iAgent 订单为准。 |
| 扩展 | 助手、会话分支、MCP、Skills 和工作区沿用 RikkaHub 的引擎。 |

模型广场中的 Token 单价为线上公布的基准价，按每百万 Token 显示。账号分组倍率单列，套餐、阶梯或模型专属价格以 iAgent 服务端计费为准。

## 测试

```sh
./gradlew :app:testGithubDebugUnitTest :mediagen:testDebugUnitTest
./gradlew :app:assembleGithubDebug :app:assembleGithubDebugAndroidTest
adb install -r app/build/outputs/apk/github/debug/app-github-universal-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
adb shell am instrument -w \
  -e class me.rerere.rikkahub.ui.IagentWorkbenchTest \
  dev.iagent.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

工作台测试会使用模拟账号和充值数据。请在单独的测试模拟器运行，它会写入测试会话和草稿。增加 `-e publicSkills true` 可以额外验证 Skills.sh 搜索及 GitHub 导入。

## 打包和签名

GitHub Actions 在 `master` 有新提交时构建，也可在 Actions 里手动运行 **iAgent Android**。测试通过后，构建启用 R8 和资源裁剪，输出分架构 APK 和 SHA-256 校验文件。下载入口见 [Actions](https://github.com/nbb2025/rikkahub/actions/workflows/android.yml)。

| Actions Secret | 内容 |
| --- | --- |
| `IAGENT_SIGNING_KEY_BASE64` | PKCS#12 格式签名库的 Base64，密钥别名为 `iagent` |
| `IAGENT_SIGNING_PASSWORD` | 签名库和密钥的密码 |

本地可以设置 `IAGENT_SIGNING_STORE_FILE` 和 `IAGENT_SIGNING_PASSWORD` 后执行以下命令。签名库应保存在仓库之外。

```sh
./gradlew :app:assembleGithubRelease -PiagentSplitOnly --no-configuration-cache
```
