# SprintMark

部活動向けの陸上競技タイム計測アプリの土台です。

## 構成

- `app/` Android アプリ
- `workers/` Cloudflare Workers 中継

Android アプリは 1 つで、起動後にスターター機 / 判定機を切り替える方式です。

## できること

- スターター機と判定機のモード切り替え
- Worker への HTTP/WebSocket 接続
- NTP ライクな時刻同期の土台
- 号砲時刻の生成と送信
- 判定機側の Camera2 ベース実装の土台
- ラインスキャン保存の土台
- ラインスキャン画像のタップ判定

## まだ手を入れるべき点

このリポジトリは「実装の出発点」として組んであります。特に次は追加調整が必要です。

- Android Gradle Wrapper の生成
- 実機での Camera2 動作確認
- 120 FPS が必要な場合の端末別調整
- スターター音・コール音の実音素材の差し込み
- 判定機の WebSocket 常時接続を実機で仕上げる部分

## Android アプリ

### 必要環境

- Android Studio
- JDK 17
- Android SDK 35

### ビルド

1. Android Studio でこのフォルダを開きます。
2. Gradle sync を実行します。
3. `local.properties` の `sdk.dir` が正しいことを確認します。
4. CLI でビルドする場合は Android Studio 同梱の JBR 17 と Gradle 8.8 を使って `:app:assembleDebug` を実行します。
5. `app` を実機へインストールします。
6. アプリ画面の Worker URL を実運用先に合わせます。

### 権限

- `CAMERA`
- `INTERNET`

## 必要権限一覧

- `CAMERA`: 判定機の撮影に使用します。
- `INTERNET`: Worker への接続に使用します。
- `RECORD_AUDIO`: 不要です。音声は端末内再生だけで足ります。

## Cloudflare Workers

### 必要環境

- Node.js 18 以上
- Cloudflare アカウント
- Wrangler

### ローカル実行

```bash
cd workers
npm install
npm run dev
```

### デプロイ

```bash
cd workers
npm run deploy
```

## 使い方

1. 判定機側を起動して Worker に接続します。
2. スターター機側で時計同期を実行します。
3. スターター機で号砲時刻を作成し、判定機へ送信します。
4. 判定機は受信した時刻に合わせて撮影を開始します。
5. ラインスキャン画像をタップしてフレームを選ぶと、タイムが表示されます。

## 実装メモ

- スターター機は軽量優先です。
- 判定機は Camera2 の高 FPS を優先しますが、端末制約で 60 FPS へ落とせる設計です。
- Worker はデータベースを使わず、必要最低限の中継だけを行います。
