# iAgent Android 版

iAgent 是 [ai.iagent.dev](https://ai.iagent.dev/) 的 Android 用戶端。開啟 App 後可先瀏覽工作台、模型和公開技能。聊天、生成圖片或影片、儲值時使用 iAgent 帳號登入。

[English](README.md) · [简体中文](README_ZH_CN.md) · [下載組建](https://github.com/nbb2025/rikkahub/actions/workflows/android.yml)

## 安裝

開啟已成功的 **iAgent Android** 組建，下載 **iAgent-Android-APKs**，解壓縮後安裝適用的 APK。大多數 Android 手機和 Apple Silicon 模擬器使用 `arm64-v8a`，Intel 模擬器通常使用 `x86_64`。系統需為 Android 8.0 或更新版本。

## 工作台

- **聊天**依帳號同步可用的 iAgent 分組和模型，支援助理、對話紀錄、附件、工具呼叫、Skill 和 MCP。
- **創作**提供 GPT Image 等圖片模型，以及 Wan、Video-LT 等影片模型。圖片和影片各自保留草稿，參考素材和參數依模型調整。
- **模型廣場**可查看線上模型的分組價和基準價。登入後可查看各分組和模型近 24 小時的請求監控。模型選擇器優先使用 iAgent 的線上排序，缺少規則時依名稱自然降冪排列。
- **餘額儲值**使用 iAgent 的付款管道及訂單介面。付款時開啟付款頁面或 QR Code，iAgent 確認入帳後更新餘額。
- **Agent 技能**支援在 [Skills.sh](https://skills.sh/) 尋找公開技能，也能從 GitHub、ZIP 或 `SKILL.md` 檔案匯入。

「帳號 → 應用程式偏好設定 → 關於」提供官網及開放原始碼儲存庫。iAgent QQ 群組號碼為 **116098473**，點選號碼即可複製。

## 開發與組建

請參閱 [Android 開發與組建文件](docs/android.md)。每次推送至 `master`，GitHub Actions 會執行測試並上傳依架構拆分的精簡 APK。

## 開放原始碼授權

iAgent 改編自 [RikkaHub](https://github.com/rikkahub/rikkahub)。原始碼採用 [GNU AGPL-3.0](LICENSE) 授權，各模組及依賴套件另有各自的聲明。
