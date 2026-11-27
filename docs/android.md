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
| 提供商 | 提供商页有 iAgent 和 DeepSeek 两项。iAgent 使用账号登录，DeepSeek 使用用户在官方平台创建的 API Key，在提供商详情页添加模型。 |
| 模型与分组 | 公开目录读取 `/site-api/models?include_group_pricing=1`。登录后再读取 `/api/v1/groups/available`，iAgent 模型按账号的分组权限显示。 |
| 排序与过滤 | 延用目录返回的 `group_order` 和 `sort_rules`。同优先级或缺少规则时使用名称的自然降序。聊天模型列表会排除 Auto Review。 |
| 创作 | 根据模型协议加载图片、视频、音频和首尾帧等参考选项。任务和本地作品由创作仓库管理。 |
| 充值 | 使用站点支付配置和 `/api/v1/payment` 接口。客户端显示估价，实际金额和到账状态以 iAgent 订单为准。 |
| 扩展 | 助手、会话分支、MCP、Skills 和工作区沿用 RikkaHub 的引擎。 |

模型广场默认显示计入分组倍率的美元价格，可切换到基础价。登录后从 `/api/v1/groups/rates` 读取账号专属倍率，优先使用 `/api/v1/model-plaza` 的有效价格。该接口关闭或暂时不可用时，使用 `/site-api/channels` 的渠道价、分组媒体单价及官方基础价作为参考。Token 价按每百万 Token 显示，媒体按模型对应的张、秒、视频或请求计费。计费结果以实际账单为准。

登录后，模型广场从 `/api/v1/usage/model-request-metrics` 加载分组和模型近 24 小时的请求成功率及逐小时状态。模型条目还显示平均延迟，文本模型显示流式输出速度。页面活跃时约每两分钟刷新。

## 分组与专用令牌

登录后，App 每次进入前台都会刷新可用分组，停留在前台时大约每分钟检查一次。新分组在账号有权限且模型目录已经发布后出现在选择器。分组撤下或账号失去权限时，从本地选择器移除。分组查询失败时保留上次成功状态。如果只加载失败的是模型目录，App 仍会移除已确认下线的分组。

同步渠道不会修改远端令牌。聊天和创作按账号、分组和用途独立绑定，使用与网页相同的「聊天」和「创意工坊」专用令牌名称。实际发起请求时先检查本机绑定的令牌 ID，再遍历远端同分组库存。已有可用令牌就复用，发现停用、过期或额度用尽的专用令牌时提示检查。已知令牌被删除或移走后，App 会在发现可用替代令牌时重新绑定。

只有第一次使用且确认没有专用令牌时才发起创建。新 Android 客户端对同一账号、分组和用途使用相同的 `Idempotency-Key`。创建结果不明时保留本地记录，下一次先读取远端库存确认结果。没有已知结果前暂停重复创建。历史媒体任务可用原令牌继续查询和下载，结果仍受远端权限和任务有效期限制。

## 测试

```sh
./gradlew :app:testGithubDebugUnitTest :mediagen:testDebugUnitTest
./gradlew :app:assembleGithubDebug :app:assembleGithubDebugAndroidTest
adb install -r app/build/outputs/apk/github/debug/app-github-universal-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
adb shell am instrument -w \
  -e class me.rerere.rikkahub.ui.IagentWorkbenchTest,me.rerere.rikkahub.ui.IagentWorkspaceLifecycleTest \
  dev.iagent.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

工作台测试使用模拟账号、分组定价、请求监控和充值数据。请在单独的测试模拟器运行，它会写入测试会话和草稿。增加 `-e publicSkills true` 可以额外验证 Skills.sh 搜索及 GitHub 导入。
分组生命周期测试模拟上架、下线、恢复、重复请求和创建响应丢失。

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
