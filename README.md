# VoiceControl - Android Voice Assistant (Step 1)

A modern, production-ready Android application built in Kotlin implementing **Step 1: Voice-to-Text** functionality, inspired by iPhone Voice Control.

---

## 📱 Features

- **Runtime Audio Permissions**: Seamlessly requests and handles `android.permission.RECORD_AUDIO` with standard Material 3 rationale dialogs.
- **Android SpeechRecognizer Integration**: Leverages native Android `SpeechRecognizer` API for real-time speech-to-text conversion.
- **Dynamic State Machine**: Tracks and visually represents state transitions across:
  - **Idle**: System ready for microphone input.
  - **Listening**: Active microphone audio capture with real-time sound level decibel (RMS dB) animation.
  - **Processing**: Engine converting captured audio to text.
  - **Success**: Transcribed text output displayed in card container.
  - **Error**: User-friendly error message handling (permission denial, network timeouts, no speech detected).
- **Material 3 Design**: Modern aesthetics with responsive animations, dynamic theme colors, rounded surfaces, and clear user controls (Copy & Clear).
- **MVVM Architecture**: `VoiceViewModel` handles state via `StateFlow` and survives configuration changes (device rotation, light/dark mode switches).

---

## 🛠️ Technical Specifications

- **Language**: Kotlin 2.0+
- **Minimum SDK**: Android 12 (API level 31)
- **Target SDK**: Android 15 / 16 (API level 35)
- **Supported Versions**: Android 12, 13, 14, 15, and 16
- **Architecture**: Model-View-ViewModel (MVVM)
- **UI Framework**: Jetpack Compose with Material 3 (`androidx.compose.material3`)

---

## 📂 Project Structure

```
iphone feature clone/
├── build.gradle.kts (Project build script)
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml (Version Catalog)
└── app/
    ├── build.gradle.kts (App build script)
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/example/voicecontrol/
            │   ├── MainActivity.kt                  # Activity Entry Point
            │   ├── state/
            │   │   └── VoiceUiState.kt              # Sealed Interface for UI states
            │   ├── manager/
            │   │   └── SpeechRecognizerManager.kt   # SpeechRecognizer API wrapper
            │   ├── ui/
            │   │   ├── VoiceViewModel.kt            # MVVM ViewModel retaining StateFlow state
            │   │   ├── VoiceControlScreen.kt        # Main Screen & Permission Launcher
            │   │   ├── components/
            │   │   │   ├── MicButton.kt             # Animated Center Microphone Button
            │   │   │   ├── StatusIndicator.kt       # State Status Badge
            │   │   │   └── RecognizedTextDisplay.kt # Speech Card & Copy/Clear actions
            │   │   └── theme/
            │   │       ├── Color.kt                 # Material 3 Color Palette
            │   │       ├── Theme.kt                 # Material 3 Theme setup
            │   │       └── Type.kt                  # M3 Typography
            └── res/
                ├── values/
                │   ├── strings.xml
                │   ├── colors.xml
                │   └── themes.xml
                └── xml/
                    └── data_extraction_rules.xml
```

---

## 🚀 How to Build and Run in Android Studio

1. **Open Project**:
   - Open **Android Studio** (Ladybug / Hedgehog or newer).
   - Select **Open** and navigate to `c:\Users\LENOVO\OneDrive\Desktop\iphone feature clone`.

2. **Gradle Sync**:
   - Android Studio will automatically import the project and sync dependencies using `gradle/libs.versions.toml`.

3. **Run on Device or Emulator**:
   - Ensure an Android 12+ (API 31+) device or emulator is selected.
   - Click **Run 'app'** (`Shift + F10` or the green play icon).

---

## 🧪 Testing Voice Control (Step 1)

1. Launch the app on your device.
2. Tap the large blue microphone button in the center.
3. When prompted, tap **Allow** for microphone permission.
4. Speak a command clearly into your device microphone (e.g., *"Open WhatsApp"* or *"Call Alex"*).
5. Watch the status indicator change to **Listening...** (with pulsing microphone animation), then **Processing...**, and finally view the recognized speech output displayed in the card above!
