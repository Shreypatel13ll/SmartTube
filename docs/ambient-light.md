# Ambient light implementation and verification

The native TV renderer uses playback-relevant values from `Blur + Ambient Light Effect in YouTube ( Logic Looper ).json`:

| Profile field | Value | Native behavior |
| --- | ---: | --- |
| `enabled` | `true` | New installs select a `TextureView` and turn on the effect. |
| `blur` | `25` | Neighboring edge samples are blurred before rendering. |
| `edge` | `20` | The video is inset by 10% to make room for the wash. |
| `spread` | `130` | The wash extends 130% of the inset. |
| `spreadFadeStart`, `spreadFadeCurve` | `20`, `20` | Fade starts 20% out with a quadratic falloff. |
| `framerateLimit`, `energySaver` | `60`, `true` | Sampling caps at 60 fps, slowing on unchanged frames. |
| `detectHorizontalBarSizeEnabled`, `detectVerticalBarSizeEnabled` | `true`, `false` | Black horizontal bars are skipped; vertical bars are retained. |

The browser extension's YouTube page styling, header, comments, scrollbars, and picture-in-picture options do not apply to the full-screen SmartTube TV player. The renderer is a native approximation of the video glow, not a browser-extension runtime.

The renderer does not inset video until it has captured a non-black frame. If a protected or device-specific surface rejects frame capture three times, it returns the video to full size and logs `AmbientLightView` rather than leaving an empty border. Existing user preferences are preserved; to use the new defaults on an upgrade, enable Ambient light in Settings or clear app data.

## Verification

Run `:smarttubetv:testStbetaDebugUnitTest --tests com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.AmbientLightSamplerTest` for edge, black-bar, dark-frame, and blur tests. The debug APK includes a synthetic video activity to verify the actual `TextureView` capture and glow on an emulator or TV without requiring YouTube connectivity:

```text
adb shell am start -n org.smarttube.beta/com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.AmbientLightDebugActivity
adb logcat -s AmbientLightView
```

The synthetic activity is excluded from release APKs. It paints moving, high-contrast video edges so a screenshot can verify that the native glow follows the frame. It does not replace playback testing with a real SmartTube video on target hardware.
