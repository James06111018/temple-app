# 同步設計說明

本文說明目前專案的雲端同步策略。這套設計是以「本機 SQLite + Neon PostgreSQL」為前提，讓桌面程式可以在多台主機上使用，但不需要指定其中一台當主資料庫。

## 1. 設計目標

- 保留本機可離線操作的能力
- 讓多台主機都能同步到同一份雲端資料
- 避免每次啟動都把整包資料重送到雲端
- 降低 SQLite 在同步時與畫面同時存取造成的鎖定衝突

## 2. 資料來源

- 本機資料庫：SQLite
- 雲端資料庫：Neon PostgreSQL
- 同步主要針對以下表：
  - `light_members`
  - `donations`

## 3. 同步模式

### 3.1 初始化上雲

這是一次性的人工流程，不由 app 自動執行。

用途：
- 把既有舊資料先補到 Neon
- 讓雲端先成為共同基準

建議時機：
- 正式部署前
- 或第一次準備讓多台主機共用資料前

說明：
- 這一步不屬於日常同步
- 完成後，之後的 app 只需要做增量同步

### 3.2 日常增量同步

這是 app 平常運作時的主要同步方式。

同步內容：
- 新增資料
- 修改資料
- 刪除資料

同步原則：
- 只送出有變更的資料
- 不再自動全量上傳整個 SQLite

## 4. 同步時機

### 4.1 登入後同步

使用者登入後，app 會在主畫面載入完成後延遲一小段時間，再背景執行同步。

目的：
- 避免畫面初始化與同步同時碰到 SQLite
- 降低 `database is locked` 的機率

### 4.2 關閉前同步

使用者關閉程式前，app 會再做一次同步。

目的：
- 把剛剛操作完、但尚未送出的變更補到 Neon
- 降低資料停留在本機尚未上雲的風險

## 5. 鎖定與穩定性

目前已對 SQLite 做以下保護：

- 開啟 `WAL`
- 設定 `busy_timeout = 5000`

效果：
- SQLite 短時間遇到鎖定時會先等待
- 比直接立刻報錯更穩定

## 6. 雲端寫入型別

Neon 的 `updated_at` 與 `deleted_at` 欄位是 `timestamptz`。

因此同步寫入時：
- 不直接把字串當成一般文字送入
- 會先轉成正確的時間型別，再寫入 PostgreSQL

## 7. 現有同步流程摘要

1. 使用者登入
2. 主畫面載入
3. 延遲後開始背景同步
4. 同步只送出本機變更
5. 關閉程式時再做一次同步

## 8. 目前不做的事

- 不在啟動時自動全量推送所有資料
- 不把其中一台主機當作主資料庫
- 不要求使用者在執行時手動輸入雲端連線資訊

## 9. 相關檔案

- [`/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/sync/SyncService.java`](/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/sync/SyncService.java)
- [`/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/sync/PostgresRemoteSyncGateway.java`](/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/sync/PostgresRemoteSyncGateway.java)
- [`/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/TempleApplication.java`](/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/TempleApplication.java)
- [`/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/repository/sqlite/SQLiteDatabaseManager.java`](/Users/james/temple-app/src/main/java/tw/org/il/dongsheng/templeapp/repository/sqlite/SQLiteDatabaseManager.java)

