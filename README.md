# 好好歇会 Android MVP

绿色主题的本地健康提醒 App。当前版本支持：

- 多条独立提醒事项；事项名称、每日开始/结束时间、5-90 分钟间隔可分别设置，并支持新增、编辑、暂停和删除。
- 默认提供喝水、护眼、走动、放松颈椎，也支持自定义事项。
- 保活壁纸：可选用软件内置壁纸，或从相册选择自己的图片作为动态壁纸；设为壁纸后系统更不容易回收本应用。
- 系统通知、自绘表情动画、完成和 5 分钟稍后提醒。
- 可选 Usage Access：显示今日应用前台使用时长，并可设置“最近有使用才提醒”。
- 应用白名单：从启动器应用中选择不计入统计、也不触发提醒的应用。
- 指定 App 软拦截：可选择限制应用，并在对应提醒时段显示休息页；白名单优先放行。
- 开机、改时间或时区后恢复下一次提醒。

构建 Debug APK（需安装 Android SDK 并配置 `local.properties`）：

```powershell
gradle :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。首次启用时请允许通知；要显示屏幕使用统计，需要从 App 内进入系统“使用情况访问”页面授予权限。要启用软拦截，还需在系统无障碍设置中手动启用“好好歇会应用休息提醒”。该服务只接收前台窗口切换事件，不读取页面文本或控件内容。

### 应用内更新

启动时会自动检查更新（每天最多一次）；设置页的“检查应用更新”可手动检查。更新清单和 APK 下载地址必须使用 HTTPS。配置清单 URL：

```powershell
gradle :app:assembleDebug -PupdateManifestUrl=https://example.com/app-update.json
```

也可通过 `UPDATE_MANIFEST_URL` 环境变量配置。清单格式如下，`sha256` 必须是 APK 文件的 SHA-256 十六进制值。安装前还会验证包名及 APK 签名证书与当前安装版本一致，然后由 Android 系统确认安装。

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://example.com/app.apk",
  "sha256": "APK 文件的 64 位 SHA-256 十六进制值",
  "notes": "更新说明"
}
```

Android 普通应用不能拦截系统 Home 键或导航手势，也不能保证用户留在休息页。无障碍权限只能观察前台应用切换并尽力再次显示休息页，用户仍可退出、关闭服务或卸载应用。只有受管设备上由设备管理员配置的锁定任务模式（Lock Task/Kiosk）才能限制 Home 导航；这需要设备所有者部署，不是用户在普通权限页多开几个权限就能实现。

当前使用 `AlarmManager.setAndAllowWhileIdle()`，提醒会受 Doze 和厂商省电策略影响，可能晚于设置时间。无障碍用途在应用商店分发前需按商店政策申报审核。表情和水滴动画由本项目的 Android Canvas 代码绘制。
