# Android 霸屏、闹钟与振动技术说明

本文说明本项目提醒从定时到显示的实现方式、Android 系统权限要求，以及排查设备上提醒失效的方法。对应代码位于 `app/src/main/java/io/iftech/healthreminder/`。

## 1. 提醒链路

事项的规则和提醒开关保存在 `reminder_settings`。`ReminderScheduler` 根据事项的下次触发时间创建 `AlarmManager` 闹钟，触发目标是显式 `PendingIntent`，接收者为 `ReminderReceiver`。

```text
ReminderSettings / 用户开关
        |
        v
ReminderScheduler -- AlarmManager RTC_WAKEUP --> ReminderReceiver
                                                    |
                               +--------------------+-------------------+
                               |                                        |
                         屏幕亮且已解锁                         息屏/锁屏
                               |                                        |
                    TYPE_APPLICATION_OVERLAY                高优先级全屏通知
                    霸屏式提醒悬浮页                          ReminderActivity
```

每个事项使用自己的 request code 和 `PendingIntent`，避免不同事项互相覆盖。提醒触发后，接收者检查事项是否仍启用；有效时显示提醒并安排下一次提醒。

## 2. 定时闹钟

`ReminderScheduler.setAlarm()` 在 Android 12/API 31 及以上、且应用获准安排精确闹钟时，使用 `setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ...)`。这表示闹钟可以唤醒设备，并尽量在 Doze 期间准时触发。若精确闹钟权限未获准或调用被系统拒绝，代码退回 `setAndAllowWhileIdle()`，时间精度会降低。

清单声明 `SCHEDULE_EXACT_ALARM`。Android 12 及以上仍需检查系统的“闹钟和提醒”特殊应用权限；仅有 manifest 声明不代表运行时一定获准。用户关闭事项时，调度器应取消该事项的提醒 `PendingIntent` 并清理下次触发时间。修改事项时保留已有下次触发时间，只有明确需要重新计算时才重置，避免切换其他事项导致计时刷新。

设备重启、系统时间或时区变化后，由 `BootReceiver` 恢复调度。`reminder_schedule` 保存下次触发时间及息屏暂停期间剩余时间。

## 3. 息屏暂停与恢复

`ReminderApplication` 注册屏幕开关广播：屏幕熄灭时调用 `pauseForScreenOff()`，亮屏时调用 `resumeAfterScreenOn()`。用户关闭“统计息屏/息屏提醒”后，调度器取消正在等待的提醒，并保存距到期的剩余时间；亮屏后以该剩余时间继续计时，而不是从头计算。用户开启息屏提醒时，屏幕熄灭不会暂停闹钟。

`ScreenUnlockReceiver` 监听 `USER_PRESENT`，作为用户解锁后的恢复入口。`ReminderScheduler.update()` 也会检查暂停状态，避免广播与设置变化交错时调度停留在暂停状态。

注意：`ACTION_SCREEN_OFF`/`ACTION_SCREEN_ON` 是应用运行期间的动态广播，不是重启恢复机制。进程死亡或厂商强行冻结进程时，不能只依赖动态接收器；设备休眠时实际触发仍应由 AlarmManager 负责。

## 4. 霸屏和锁屏显示

### 解锁后的霸屏提醒

`ReminderOverlayWindow.show()` 使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 覆盖其他应用，并设置 `FLAG_LAYOUT_IN_SCREEN` 与 `FLAG_KEEP_SCREEN_ON`。需要 manifest 的 `SYSTEM_ALERT_WINDOW` 声明和用户在系统设置中授予“显示在其他应用上层”权限。该悬浮层由提醒操作按钮关闭，再进入 `ReminderActivity` 继续记录。

悬浮层只适用于屏幕亮且设备已解锁。系统通常不允许普通悬浮窗覆盖系统锁屏，因此息屏或锁屏时此方法返回失败，流程改用通知。

### 锁屏/息屏提醒

`ReminderReceiver.post()` 在屏幕熄灭且“息屏提醒”开启时，将通知标为 `CATEGORY_ALARM`，并附加 `setFullScreenIntent()`。`ReminderActivity` 在 manifest 中配置 `showWhenLocked`、`turnScreenOn`，用于系统允许全屏通知启动时显示在锁屏上并点亮屏幕。

这不是系统“屏幕固定/锁定任务”（Lock Task / kiosk）模式，也不能拦截 Home、返回或手势导航。普通应用不能在用户不同意的情况下可靠地禁止用户离开应用。Lock Task 通常要求设备所有者（Device Owner）配置白名单，适合企业专用设备，不应当用来替代普通应用的提醒页。

全屏通知能否点亮屏幕取决于 Android 版本、通知权限、全屏通知访问权限、通知渠道重要性、锁屏显示设置和厂商策略。清单声明 `USE_FULL_SCREEN_INTENT` 仍不等于系统一定允许启动。Android 14 及以上尤其要检查应用的“全屏通知”特殊访问状态。若系统拒绝全屏启动，通知仍可能显示在锁屏通知栏，但不会自动进入应用或点亮屏幕。

## 5. 闹钟铃声和振动

`AlarmManager` 本身只负责按时唤醒并发送广播，不播放声音。息屏铃声 URI 存储在 `reminder_settings` 的 `screen_off_ringtone`。息屏声音开关开启时，`ReminderReceiver` 启动 `AlarmRingtoneService` 前台服务，由 `Ringtone` 使用 `USAGE_ALARM` 音频属性循环播放选中的铃声。该服务有独立的低重要性前台通知渠道，以满足 Android 前台服务要求；系统拒绝启动服务时，提醒通知渠道保留铃声回退。

应用处理提醒、延后提醒或清除提醒通知时，会同时停止振动和 `AlarmRingtoneService`。不要在服务启动成功时再让提醒通知渠道播放同一铃声，否则会造成重叠播放。通知渠道在 Android 8/API 26 及以上创建后，其声音、振动等设置由系统持有，后续同 ID 调用 `createNotificationChannel()` 通常不能覆盖用户或已有渠道设置。调整渠道行为时需要新渠道 ID，并允许用户在系统通知设置中检查该渠道。

振动通过两层配置实现：

- 应用直接调用 `Vibrator`（Android 12/API 31 及以上从 `VibratorManager` 获取默认振子），使用 `VibrationEffect.createWaveform()` 循环波形。
- 息屏通知渠道依据用户的振动开关启用振动并设置重复波形。

清单声明 `VIBRATE`。提醒页开始执行、稍后提醒或用户结束提醒时调用 `stopAlarmVibration()` 取消应用直接启动的波形。通知被系统清除时通过 delete intent 发出停止动作。通知渠道本身的振动由系统通知管理器控制，不受 `Vibrator.cancel()` 控制。

静音模式、勿扰模式、系统振动强度、通知渠道被用户静音、应用通知总开关、厂商省电策略及设备硬件都会影响最终效果。应用侧请求播放并不保证设备一定发声或振动。

## 6. 权限和设置检查

在设备上逐项核对：

1. 应用通知总开关和“健康提醒/息屏闹钟”对应的通知渠道处于允许状态；渠道重要性为高，声音/振动配置正确。
2. Android 12 及以上，“闹钟和提醒”特殊权限已允许；全屏通知访问在系统版本要求时已允许。
3. 锁屏通知允许显示；需要亮屏时，系统未限制该应用的全屏提醒。
4. “显示在其他应用上层”已允许。此权限影响亮屏解锁后的覆盖提醒，不负责锁屏显示。
5. 息屏提醒、息屏闹钟、息屏振动开关与用户预期一致；设备未处于勿扰或全局静音状态。
6. Vivo 等设备的后台高耗电/自启动/睡眠策略没有冻结应用。系统具体菜单名称因厂商和版本不同。

## 7. ADB 排查

以下命令用于读取状态，不会修改用户设置：

```powershell
adb shell dumpsys alarm | Select-String -Pattern 'io.iftech.healthreminder|ReminderReceiver' -Context 0,3
adb shell dumpsys notification --noredact | Select-String -Pattern 'health_alarm|health_breaks|AppSettings: io.iftech.healthreminder' -Context 0,5
adb shell dumpsys package io.iftech.healthreminder
adb shell dumpsys power
adb logcat -d -s ReminderReceiver ReminderOverlay AlarmManager ActivityTaskManager
```

检查 `dumpsys alarm` 中是否存在应用的 `RTC_WAKEUP`，其触发时间是否符合 `reminder_schedule`。检查通知输出中实际使用的 channel ID、importance、sound、vibration 和应用通知总开关。检查 package 输出中的 `SCHEDULE_EXACT_ALARM`、`USE_FULL_SCREEN_INTENT`、`POST_NOTIFICATIONS`、`VIBRATE` 及悬浮窗访问状态。

不要为了测试临时覆写用户的提醒规则或偏好后忘记恢复。建议新增独立的短周期测试事项，屏幕关闭并锁定设备，等待一次完整周期，再分别验证通知出现、屏幕点亮、铃声持续和振动持续；每项结果都记录设备型号、Android 版本、通知渠道 ID 与权限状态。

## 8. 相关代码

- `ReminderScheduler.java`：AlarmManager 调度、取消、息屏暂停与恢复。
- `ReminderReceiver.java`：提醒接收、通知渠道、全屏通知、铃声配置和振动。
- `AlarmRingtoneService.java`：息屏闹钟铃声的前台循环播放与停止。
- `ReminderOverlayWindow.java`：解锁后悬浮霸屏页。
- `ReminderActivity.java`：锁屏提醒/执行页面及停止振动动作。
- `ReminderApplication.java`：运行期屏幕开关监听。
- `ScreenUnlockReceiver.java`、`BootReceiver.java`：解锁与重启后的调度恢复。
- `ScreenOffSettingsActivity.java`、`ReminderSettings.java`：息屏设置界面和偏好读写。
- `AndroidManifest.xml`：权限、Activity 窗口属性和广播接收器声明。
