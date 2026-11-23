# iAgent 安卓版

iAgent 是 [ai.iagent.dev](https://ai.iagent.dev/) 的安卓客户端。打开 App 可以先浏览工作台、模型和公开技能。聊天、生成图片或视频、充值时使用 iAgent 账号登录。

[English](README.md) · [繁體中文](README_ZH_TW.md) · [下载构建](https://github.com/nbb2025/rikkahub/actions/workflows/android.yml)

## 安装

打开一次成功的 **iAgent Android** 构建，下载 **iAgent-Android-APKs**，解压并安装对应 APK。多数安卓手机和 Apple Silicon 模拟器使用 `arm64-v8a`，Intel 模拟器通常使用 `x86_64`。系统需为 Android 8.0 或更新版本。

## 工作台

- **聊天**会按账号同步可用的 iAgent 分组和模型。支持助手、会话历史、文件附件、工具调用、Skill 和 MCP。
- **创作**提供 GPT Image 等图片模型，以及 Wan、Video-LT 等视频模型。图片和视频分别保留草稿，参考素材和参数随模型变化。
- **模型广场**展示线上模型、分组倍率和已公布的基准 Token 价格。模型选择器优先使用 iAgent 线上排序规则，缺少规则时按名称自然降序排列。
- **余额充值**使用 iAgent 的支付渠道和订单接口。付款时打开支付页或二维码，iAgent 确认到账后更新余额。
- **Agent 技能**支持发现 [Skills.sh](https://skills.sh/) 上的公开技能，也可从 GitHub、ZIP 或 `SKILL.md` 文件导入。

在「账号 → 应用偏好 → 关于」可以找到官网和开源仓库。iAgent QQ 群号为 **116098473**，点群号即可复制。

## 开发与构建

[Android 开发和构建文档](docs/android.md)包含环境、接口、测试和签名流程。每次推送到 `master`，GitHub Actions 会执行测试并上传分架构的精简 APK。

## 开源许可

iAgent 基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 开发，源码遵循 [GNU AGPL-3.0](LICENSE)。各模块和依赖的版权信息见其声明。
