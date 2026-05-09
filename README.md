# OptiPause — Interval Reminder App

> **Package:** `com.jptechgenius.optipause`  
> **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34 (Android 14)  
> **Architecture:** MVVM · **Language:** Java · **UI:** Material 3

---

## Project Structure

```
OptiPause/
├── app/
│   ├── build.gradle                          ← Dependencies & build config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml               ← Permissions + components
│       ├── java/com/jptechgenius/optipause/
│       │   ├── OptiPauseApplication.java     ← Dynamic color (Monet) setup
│       │   ├── alarm/
│       │   │   └── IntervalAlarmManager.java ← Exact alarm scheduling
│       │   ├── repository/
│       │   │   └── TimerRepository.java      ← SharedPreferences persistence
│       │   ├── viewmodel/
│       │   │   └── TimerViewModel.java       ← UI state, LiveData, commands
│       │   ├── service/
│       │   │   └── TimerForegroundService.java ← Keeps process alive
│       │   ├── receiver/
│       │   │   ├── AlarmReceiver.java        ← Handles alarm fires
│       │   │   └── BootReceiver.java         ← Reschedules after reboot
│       │   └── ui/
│       │       ├── MainActivity.java         ← Single Activity entry point
│       │       └── SettingsBottomSheet.java  ← Interval picker bottom sheet
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml         ← Main screen (Material 3)
│           │   └── bottom_sheet_settings.xml ← Settings bottom sheet
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml                ← M3 tokens (green seed)
│           │   └── themes.xml                ← Light theme
│           ├── values-night/
│           │   └── themes.xml                ← Dark theme
│           ├── menu/menu_main.xml
│           ├── drawable/                     ← Vector icons
│           └── xml/backup_rules.xml
├── build.gradle
└── settings.gradle
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                      MainActivity                        │
│  View Binding → observes LiveData from TimerViewModel   │
└────────────────────────┬────────────────────────────────┘
                         │ commands (start/stop/setInterval)
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    TimerViewModel                        │
│  LiveData: isRunning, remainingMillis, progressFraction  │
│  Owns: CountDownTimer (UI tick), AlarmManager control   │
└──────────┬──────────────────────────┬───────────────────┘
           │                          │
           ▼                          ▼
┌──────────────────┐      ┌───────────────────────────────┐
│  TimerRepository │      │     IntervalAlarmManager       │
│  SharedPrefs     │      │  scheduleNextAlarm()           │
│  - interval_ms   │      │  cancelAlarm()                 │
│  - is_running    │      │  canScheduleExactAlarms()      │
│  - next_alarm_t  │      └───────────────────────────────┘
│  - start_time    │
└──────────────────┘

AlarmManager fires → AlarmReceiver → plays sound + vibrate
                                   → notifies Service
                                   → schedules next alarm

BOOT_COMPLETED → BootReceiver → reads Repository
                              → reschedules alarm
                              → restarts ForegroundService
```

---

## Key Implementation Details

### Exact Alarms (Android 12+)

`IntervalAlarmManager` calls `alarmManager.canScheduleExactAlarms()` before scheduling.  
If the permission isn't granted, `TimerViewModel` posts to `exactAlarmGranted` LiveData,  
which triggers a `MaterialAlertDialog` in `MainActivity` directing the user to  
`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

```java
// From IntervalAlarmManager.java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    return alarmManager != null && alarmManager.canScheduleExactAlarms();
}
```

### Doze Mode Survival

`setExactAndAllowWhileIdle()` is used instead of `setExact()` so alarms fire even  
when the device enters Doze mode.

### Foreground Service Type (Android 14)

`TimerForegroundService` declares `foregroundServiceType="dataSync"` in the Manifest  
and passes `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` to `startForeground()` on  
API 29+, satisfying Android 14's strict foreground service type requirements.

### Boot Recovery

`BootReceiver` listens for both `BOOT_COMPLETED` and `QUICKBOOT_POWERON`. After reboot  
it reads `TimerRepository.isRunning()` and, if true, reschedules the next alarm  
and restarts `TimerForegroundService`.

---

## Setup Instructions

### 1. Clone / Copy the project

Place the folder in your Android Studio projects directory.

### 2. Add missing resources

**App Icon** — Replace the placeholder `mipmap/ic_launcher` resources  
with your own icon (use Android Studio's Image Asset Studio).

**Font** — The layout references `@font/roboto_mono` for the countdown display.  
Add it via **File → New → Android Resource Directory → font**, then download  
[Roboto Mono](https://fonts.google.com/specimen/Roboto+Mono) and place the `.ttf` there.  
Or remove the `android:fontFamily` attribute to use the default font.

### 3. Sync Gradle

Open in Android Studio → **File → Sync Project with Gradle Files**.

### 4. Run on a physical device

Exact alarms and foreground services behave differently in the emulator.  
Testing on a real device is strongly recommended.

---

## Permissions Required

| Permission | Purpose | When Requested |
|---|---|---|
| `SCHEDULE_EXACT_ALARM` | Fire alarms at precise times (API 31+) | Directed to System Settings if not granted |
| `USE_EXACT_ALARM` | Alternative exact alarm permission (API 33+) | Automatically granted |
| `FOREGROUND_SERVICE` | Run persistent service | Automatically granted |
| `FOREGROUND_SERVICE_DATA_SYNC` | Required service type (API 34) | Automatically granted |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after reboot | Automatically granted |
| `WAKE_LOCK` | Wake CPU for alarm processing | Automatically granted |
| `VIBRATE` | Haptic feedback on alarm | Automatically granted |
| `POST_NOTIFICATIONS` | Show foreground notification (API 33+) | Runtime prompt on first start |

---

## Customisation Points

| What to change | Where |
|---|---|
| Default interval (20 min) | `TimerRepository.DEFAULT_INTERVAL_MILLIS` |
| Alarm sound | `AlarmReceiver.playAlarmSound()` — swap `RingtoneManager.TYPE_ALARM` |
| Vibration pattern | `AlarmReceiver.VIBRATION_PATTERN` |
| Preset interval options | `SettingsBottomSheet.PRESETS` |
| Max custom interval | `SettingsBottomSheet.setupSeekBar()` — adjust `setMax()` |
| Brand color seed | `colors.xml` — regenerate tokens at [m3.material.io/theme-builder](https://m3.material.io/theme-builder) |
| Notification channel name | `strings.xml` → `notif_channel_name` |

---

## Dependencies

```gradle
// Material 3
com.google.android.material:material:1.11.0

// Lifecycle / ViewModel / LiveData
androidx.lifecycle:lifecycle-viewmodel:2.7.0
androidx.lifecycle:lifecycle-livedata:2.7.0

// Activity / Fragment KTX
androidx.activity:activity:1.8.2
androidx.fragment:fragment:1.6.2
```

---

*Built with ❤️ for reliable, battery-conscious interval reminders.*
