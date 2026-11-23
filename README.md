# iAgent for Android

iAgent brings [ai.iagent.dev](https://ai.iagent.dev/) to Android. Open the workbench as a guest, browse models and public skills, and sign in with your iAgent account when you want to chat, create media, or top up your balance.

[简体中文](README_ZH_CN.md) · [繁體中文](README_ZH_TW.md) · [Download builds](https://github.com/nbb2025/rikkahub/actions/workflows/android.yml)

## Install

Open a successful **iAgent Android** run and download **iAgent-Android-APKs**. Unzip the artifact and install the APK for your device. Most phones and Apple Silicon emulators use `arm64-v8a`. Intel Android emulators typically use `x86_64`. Android 8.0 or later is required.

## Workbench

- **Chat** uses the models and groups available to your iAgent account. Create assistants, manage conversations, attach files, and enable Skills or MCP tools per assistant.
- **Studio** supports GPT Image and other image models, and video models such as Wan and Video-LT. Image and video drafts are independent. Each model shows the reference inputs and parameters it accepts.
- **Model catalog** shows the live iAgent model list, group multipliers and published base token prices. Model pickers follow the iAgent catalog order with a natural descending fallback.
- **Balance** supports iAgent checkout and order status. Payments open the provider's hosted checkout or QR code. The app updates your balance when iAgent confirms that the order was credited.
- **Agent Skills** can browse [Skills.sh](https://skills.sh/) and import from GitHub, a ZIP package, or a `SKILL.md` file.

Visit **Account → Preferences → About** for the official site, source and QQ community. The QQ group number is **116098473**.

## Development

See [Android development and builds](docs/android.md) for the toolchain, architecture, tests and signing. GitHub Actions tests each push to `master` and uploads separate, optimized APKs.

## License

iAgent is based on [RikkaHub](https://github.com/rikkahub/rikkahub). The source is available under [GNU AGPL-3.0](LICENSE). See individual modules and dependencies for their notices.
