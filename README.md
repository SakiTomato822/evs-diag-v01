# EVS Diag V0.1

目标：只做诊断闭环  
`抓帧 -> SHA-256 hash -> UYVY 转 PNG -> 复现性判断`

## 当前实现
- 读取目标进程（默认 `android.hardware.automotive.evs@1.0-service`）PID
- 扫描 `/proc/<pid>/fd` 中的 `/dmabuf:*` FD
- 用 `su -c dd if=/proc/<pid>/fd/<fd>` 抓取固定字节数 raw（默认 `1920x896x2`）
- 每帧计算 SHA-256
- 每帧转 PNG（按 YUYV/UYVY 逻辑）
- 输出 `summary.txt`，包含 hash、nonZeroRatio、唯一 hash 数
- App 内可直接预览本次会话 PNG（Prev/Next）

## 输出目录
- App 内输出：
  - `Android/data/com.lynk.evsdiag/files/evs_diag_v01/<timestamp>/raw`
  - `Android/data/com.lynk.evsdiag/files/evs_diag_v01/<timestamp>/png`
  - `Android/data/com.lynk.evsdiag/files/evs_diag_v01/<timestamp>/summary.txt`

## 上车使用建议
1. 先打开 AVM 画面。  
2. 启动 App，保持默认参数：
   - process: `android.hardware.automotive.evs@1.0-service`
   - width: `1920`
   - height: `896`
   - frameCount: `10`
   - intervalMs: `500`
3. 点“开始诊断”，等待日志显示完成。  
4. 看 `unique hash`：
   - `>1`：说明帧有变化，路线成立。
   - `=1`：说明抓到的可能是静态/重复缓冲，需要拉长时长或制造明显运动。

## 电脑离线复核
```powershell
python .\tools\verify_uyvy_session.py "D:\path\to\session" --width 1920 --height 896 --png
```

如果缺 Pillow：
```powershell
pip install pillow
```

## 直接出 APK（GitHub 一键）
1. 把本目录完整上传到你的 GitHub 仓库。  
2. 打开 `Actions` -> `Build Debug APK` -> `Run workflow`。  
3. 构建完成后下载 Artifact：`evs-diag-v01-debug-apk`。  
4. 解压后得到 `app-debug.apk`，安装到车机即可。
