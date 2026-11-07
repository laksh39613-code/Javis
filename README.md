# AURA – Android Natural-Language Phone Agent

Natural-language commands ko Gemini function calling se samajhkar Android par permitted actions chalata hai.

## Structure

```
app/src/main/java/com/aura/agent/
├── MainActivity.kt / AuraViewModel.kt   UI (Compose), state, settings
├── core/
│   ├── Models.kt              Risk levels, ToolCall, ToolResult, AuraTool, helpers
│   ├── GeminiPlanner.kt       Gemini REST + function declarations (no shell, no free code)
│   ├── ToolRegistry.kt        The only tools Gemini can reach
│   ├── AgentOrchestrator.kt   plan -> classify -> confirm -> execute -> observe -> report
│   ├── RiskPolicy.kt          Safe / Reversible / Sensitive / Irreversible + blocked taps
│   ├── ConfirmationGate.kt    Suspends until user approves (60s timeout = reject)
│   └── ExecutionLogger.kt     In-app execution log
├── tools/
│   ├── AppControlTool.kt      open_app, open_gallery
│   ├── DeviceControlTool.kt   brightness, volume, flashlight, DND, settings, alarm, timer, calendar
│   ├── MediaControlTool.kt    play/pause/next/previous
│   ├── FileManagerTool.kt     latest download by extension (MediaStore)
│   ├── ContactLookupTool.kt   contacts (off until user consents)
│   ├── InstagramTool.kt       official deep links to profile / inbox
│   ├── YouTubeTool.kt         search in official YouTube app
│   ├── WhatsAppTool.kt        open chat / prefilled draft (user taps Send)
│   ├── CallLauncherTool.kt    dialer, call (call = IRREVERSIBLE, confirmed)
│   ├── ScreenObservationTool.kt  read_screen (login/loading/error detection)
│   └── AccessibilityTool.kt   tap_text, scroll_screen, go_back, go_home
└── accessibility/AuraAccessibilityService.kt
```

## Safety rules implemented

- Gemini sirf registered tools call kar sakta hai; shell/arbitrary code nahi.
- SENSITIVE / IRREVERSIBLE steps (call, tap "send", "delete", "pay", "follow", "like"...) pe confirmation dialog.
- Blocked taps: password, OTP, UPI, transfer, factory reset, delete account – AURA ye nahi karega.
- Password fields accessibility se skip hote hain.
- Login screen detect hone par AURA login nahi karta; user ko khud karna hota hai.
- Contacts sirf user consent ke baad.
- `verified` flag: sirf jab result read-back ya screen se confirm ho. Prompt Gemini ko bolta hai verified=false par success claim na kare.
- Limits: 6 model rounds, 12 steps, 20s per tool, retries max 3 (tap).

## Setup (Termux)

Termux me full APK build karna practical nahi hai (Android SDK/Gradle heavy), isliye:

1. Termux me code edit/test karein:
   ```bash
   pkg install git unzip
   unzip aura-android.zip -d ~/aura && cd ~/aura
   ```
2. GitHub par push karein:
   ```bash
   git init && git add . && git commit -m "AURA init"
   git branch -M main
   git remote add origin https://github.com/<you>/aura-android.git
   git push -u origin main
   ```
3. GitHub Actions `Build AURA APK` run hoga aur `aura-debug-apk` artifact milega. Use phone par install karein (Unknown sources allow karke).
4. App me **Settings** kholein:
   - Gemini API key dalein (https://aistudio.google.com se).
   - Accessibility service on karein (AURA).
   - Brightness aur DND permission dein (optional).
   - Contacts access on karein (optional).

## Honest limitations

- Ye code compile-tested nahi hai; pehli build par chhote compile errors aa sakte hain – unhe GitHub Actions log se fix karna hoga.
- WhatsApp video call start karne ka koi official intent nahi hai: AURA chat khol deta hai, video button user dabata hai.
- Instagram me post dekhna sirf accessibility/screen reading se possible hai, aur Instagram UI badalne par ye toot sakta hai.
- Screenshot + Gemini vision abhi implement nahi hai (explicit per-use consent flow chahiye).
- Voice input Android ke speech recognizer se hai (Mic button); wake word nahi hai.
- Gemini API key abhi SharedPreferences me hai; production me Android Keystore/EncryptedSharedPreferences use karein.
- `QUERY_ALL_PACKAGES` aur `CALL_PHONE` permissions Play Store par review me justify karni padengi.
- Bypass nahi: login, OTP, private-account, banking/payment flows AURA nahi chhedta.

## Next steps

- Screen-vision module (consent-based screenshot).
- Contact-based WhatsApp draft ke liye pehle ek bar contacts import UX.
- Unit tests for RiskPolicy and GeminiPlanner parsing.
