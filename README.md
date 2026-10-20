# FUTO Voice Input, custom build: Parakeet + Whistle + Whisper

This fork adds two newer on-device speech engines next to the original Whisper, and a floating mic that works with Gboard.

| Engine | Best for | Download on phone |
|---|---|---|
| **Parakeet TDT 0.6B v3** (NVIDIA, via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)) | Best accuracy and speed; English + 24 European languages | ~640 MB |
| **Whistle** ([Cactus](https://github.com/cactus-compute/needle)) | Smallest and fastest; en, de, fr, es, it, nl, pl; uses the personal dictionary as keywords | 17 MB |
| **Whisper** (OpenAI, via whisper.cpp) | 99 languages | 44-250 MB |

Pick the engine in the app under **Model**. No model is bundled in the APK (about 45 MB, mostly the engines themselves). Each model downloads the first time you use it, and everything runs offline after that. Under **Model**, **Check for updates** compares your copy with the server and downloads a newer version if there is one. **Delete model** frees the space.

**Using it with your keyboard** (in the app under **Keyboard integration**):
- **Gboard**: Gboard always sends its own mic key to Google. Turn on the **floating mic** (an accessibility service). A mic bubble then appears above the keyboard, and the transcript is typed at your cursor. You can also switch to the "FUTO Voice Input" keyboard from Gboard's language key.
- **FUTO Keyboard** (closest to Gboard: swipe typing, good predictions): turn off its built-in voice input and its mic key will use this app.
- **HeliBoard** and other open-source keyboards: their mic key opens this app's voice keyboard.

**Make a release**: on GitHub, open **Actions → Build APK → Run workflow**, pick the branch, type a version such as `1.2.1` (and optional notes), and run it. The workflow builds the APK with that version and publishes release `v1.2.1` with `voice-input-custom-1.2.1.apk`, install steps and the APK's SHA-256. Each release gets a higher version code, so it installs as an update. Leave the version empty for a test build.

**Test builds**: every push builds `voice-input-custom.apk` and attaches it to a pre-release named `build-<branch>`.

The app id is `org.futo.voiceinput.custom`, so it installs next to the official app. To keep updates installing over each other, add your own signing key as the `KEYSTORE_FILE` (base64), `KEYSTORE_PASSWORD`, `KEY_PASSWORD` and `KEYSTORE_ALIAS` repository secrets. Otherwise a cached debug key is used.

**Build locally**: `git submodule update --init libs && ./scripts/fetch-native-deps.sh && ./gradlew assembleCustomRelease`. `./gradlew testCustomDebugUnitTest --tests org.futo.voiceinput.ScreenshotTest` renders the screens to `app/screenshots/` without a phone; the latest ones are in [docs/screenshots](docs/screenshots).

---

# FUTO Voice Input

FUTO Voice Input is an application that lets you do speech-to-text on Android, integrating with third party keyboards or apps that use the generic speech-to-text APIs.

To download the application, visit the [FUTO Voice Input page](https://voiceinput.futo.org/). You can also find the contact there to report issues or suggestions.

If you have any feedback, issues are welcomed on the [public issue tracker](https://github.com/futo-org/voice-input/issues). Private inquiries are welcomed at the support email listed on the [website](https://voiceinput.futo.org/), or via the Send Feedback button in-app.

## Status

Development has largely shifted focus to the [FUTO Keyboard app](https://keyboard.futo.org/), which has voice input built-in. However, FUTO Voice Input will remain available if you prefer to use it with another keyboard.

## API support

The following APIs are supported:
* `android.speech.action.RECOGNIZE_SPEECH` implicit intent, for apps and some keyboards - this opens the floating window in the center of the screen
* IME with `voice` subtype mode, for keyboards - this opens on the bottom half of the screen in place of the keyboard

Currently this does not support the SpeechRecognizer API, which few apps seem to use. Support for this is planned in the future.

## Keyboard support

Keyboard support is touched on in the Help section of the app. In short, the following keyboards are supported:
* [**FUTO Keyboard**](https://keyboard.futo.org/) has FUTO Voice Input built-in; if you want to force it to use the external app you have to disable built-in voice input in its settings
* [**HeliBoard**](https://github.com/Helium314/HeliBoard)
* [**FlorisBoard**](https://github.com/florisboard/florisboard) supports it on newer releases
* [**AnySoftKeyboard**](https://github.com/AnySoftKeyboard/AnySoftKeyboard)
* [**Unexpected Keyboard**](https://github.com/Julow/Unexpected-Keyboard) (v1.23+)
* **AOSP Keyboard** available in LineageOS and others

If you're okay with using proprietary keyboards, the following are supported:
* **Grammarly Keyboard**, which uses the IME
* **Microsoft SwiftKey**, which uses the implicit intent

Incompatible keyboards:
* **Gboard** - hardcoded to use Google's voice input, does not support third-party options
* **Samsung Keyboard** - hardcoded to only allow either Samsung Voice Input, or Google Voice Input
* **Simple Keyboard** by Raimondas Rimkus - [no voice button](https://github.com/rkkr/simple-keyboard/issues/133)
* **Simple Keyboard** by Simple Mobile Tools - [no voice button](https://github.com/SimpleMobileTools/Simple-Keyboard/issues/201)
* **TypeWise** - no voice button [but suggestion filed in 2019](https://suggestions.typewise.app/suggestions/65517/voice-to-text-dictation)

## Language support

FUTO Voice Input is currently based on the OpenAI Whisper model, and could theoretically support all of the languages that OpenAI Whisper supports. However, in practice, the smaller models tend to not perform too good with languages that had fewer training hours. To avoid presenting something worse than nothing, only languages with more than 1,000 training hours are included as options in the UI:
* English
* Chinese (currently has some weird behavior between traditional/simplified)
* German
* Spanish
* Russian
* French
* Portuguese
* Korean
* Japanese
* Turkish
* Polish
* Italian
* Swedish
* Dutch
* Catalan
* Finnish
* Indonesian

Language support and accuracy may expand in the future with better optimization and fine-tuned models. Feedback is welcomed about language-related issues or general language accuracy.

## Development

You can develop this app by opening it in Android Studio. Otherwise, you can use Gradle to build the app like so:
```bash
./gradlew assembleStandaloneRelease
```

There are four build flavors:
* `dev` - for development, includes Play Store billing and all payment methods, auto-update, etc
* `playStore` - Play Store build, does not include auto-update and only includes Play Store billing
* `standalone` - does not include Play Store billing library, includes auto-update
* `fDroid` - does not include Play Store billing nor auto-update

Some prebuilt binaries are included in the `libs` directory to make the build faster, there are also instructions to build them yourself.

## License

This code is currently licensed under the [FUTO Source First License 1.0](LICENSE.md)

## Credits

The microphone icon was taken from [Feather Icons](https://feathericons.com/), an open-source icon pack authored by Cole Bemis.

Thanks to the following projects for making this possible:
* OpenAI - [OpenAI Whisper](https://github.com/openai/whisper/)
* Georgi Gerganov - [whisper.cpp](https://github.com/ggerganov/whisper.cpp)
* TensorFlow Authors - [TensorFlow Lite](https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite) (tflite was used in the past, it's no longer used)
* Max-Planck-Society - [PocketFFT](https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md)
* The WebRTC project authors - [WebRTC VAD](https://github.com/abb128/android-vad/blob/main/vad/src/main/jni/webrtc_vad/LICENSE)
* Georgiy Konovalov - [android-vad](https://github.com/abb128/android-vad)
* Other app dependencies, listed in app/build.gradle