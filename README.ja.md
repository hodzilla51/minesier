# MineSIer

[English](./README.md) · [日本語](./README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-green.svg)
![Loader: Fabric](https://img.shields.io/badge/Loader-Fabric-blueviolet.svg)

**Minecraftの中でJavaScriptを書く。** ワールド内でコードを書いて動かせる、プログラム可能な
コンピュータとタートル。[CC:Tweaked](https://tweaked.cc/) の思想を、Luaではなく
JavaScriptで。

> ⚠️ **実験的・開発初期段階。** 対象は Minecraft 26.2（Fabric）。仕様は変わり得ます。
> フィードバック・コントリビューション歓迎。

## 特徴

- **コンピュータブロック** — 設置して右クリックすると複数行エディタ付きの端末が開く。
  JavaScriptを書いて **Run**（または `Ctrl`/`Cmd`+`Enter`）で実行、結果を表示。
- **`print(...)`** とスクロールバック表示。値だけでなく好きなだけ出力できる。
- **ブロックごとに独立したサンドボックスVM** — 各コンピュータが隔離されたJSエンジンを持つ
  （ホストのファイル/ネット/リフレクションには触れない。暴走ループも打ち切られる）。
- **プログラム可能なタートル** — コードで動かすロボット。
  `forward` / `back` / `turnLeft` / `turnRight`、`dig` / `place`、`detect` /
  `inspect`、燃料。**1マスずつなめらかに移動**し、実行中に世界の状態へ反応する。
- **インベントリ** — タートルは掘った物を回収し、選択スロットから設置する。
- **ディスク** — プログラムをディスクアイテムに保存。データはディスク側に乗るので、
  抜いて別のコンピュータへ持ち運べる。
- **永続化** — コンピュータ/タートルの状態はワールドに保存される。

## クイックスタート

ブロックを入手（クリエイティブ or コマンド）:

```
/give @s minesier:computer
/give @s minesier:turtle
/give @s minesier:disk
```

**コンピュータ** — 設置→右クリック→入力してRun:

```js
1 + 1                       // => 2
for (var i = 0; i < 3; i++) print("hello " + i);
```

**タートル** — 設置すると（あなたが向いている方向を向く）、右クリックしてプログラムを実行。
これは前方5マスを、途中のブロックを掘りながら進む:

```js
for (var i = 0; i < 5; i++) {
  if (turtle.detect()) turtle.dig();
  turtle.forward();
}
print("done, fuel = " + turtle.getFuelLevel());
```

**ディスク** — ディスクを手に持ってコンピュータ/タートルを右クリックで挿入。
プログラムやテキストデータを書いてファイルパスを入れ **Save**。**Eject** で取り出し、
別のコンピュータに挿して **Load** すれば、ファイルが付いてくる。

パスは `/startup.js` や `data/scans.json` のように `/` でフォルダ分けできます
（内部では先頭の `/` なしで保存）。コンピュータ/タートルのプログラムからは
ディスク上のテキストファイルを `fs` API で読み書きできます:

```js
fs.write("/data/scans.json", "[]");
print(fs.exists("/data/scans.json")); // true
print(fs.read("/data/scans.json"));   // []
print(fs.list("/data"));              // scans.json
fs.remove("/data/scans.json");
```

### タートルAPI

`forward()` `back()` `turnLeft()` `turnRight()` `dig()` `place()`（選択スロットから設置）
`place(id)`（例: `"minecraft:stone"`） `detect()` `inspect()` `getFuelLevel()`
`refuel(n)` `select(n)` `getSelectedSlot()` `getItemCount(n)`

グローバル: `print(...)`。単発評価用の `/js <式>` コマンドもあります。

### JavaScript APIバージョン

スクリプトから `minesier.apiVersion` と `minesier.apiVersionString` を確認できます。
互換性と非推奨化の方針は [JavaScript API versioning](docs/javascript-api-versioning.md) を参照。

### 有線ネットワーク（開発中）

コンピュータには、画面と反対側の面に1個のNICがあります。Cableをその面へつなげると、同じ
ケーブル連結成分にあるコンピュータへフレームを送れます。アドレスはワールド保存中は変わりません。

```js
print(net.address());                 // this computer's MAC-like address
net.send("02:12:34:56:78:9a", "hello");

var frame = net.receive();            // null when no frame is queued
if (frame) print(frame.source, frame.data);
```

現在は文字列ペイロード、4 KiBまで、NICごとに64フレームまでです。複数NIC・promiscuous受信・
スイッチ/ルータのプレイヤー実装は対応済みです（詳細は英語READMEを参照）。

`net.broadcast()` はL2ブロードキャストアドレスを返します。同じセグメントの全NICが受理するので、
これを使ってARP（「このアドレス誰？」を全員に問い合わせ→所有者がunicastで返答）を自分で実装できます。
ARPはあえて組み込みにしていません——自分で作るのが目的です。

## ソースからのビルド

必要環境: **JDK 25**、Fabric（MC 26.2 用の Loader + API）。

```
./gradlew build        # build/libs/minesier-*.jar が生成される
./gradlew runClient    # 開発用クライアントを起動
```

## 仕組み

- JavaScriptは **Mozilla Rhino**（インタプリタモード）で実行。セーフスコープ＋全拒否の
  ClassShutter＋命令数上限でサンドボックス化。
- 実行は **サーバ権威** — クライアントは端末表示とコマンド送信のみ。
- タートルのプログラムはワーカースレッドで **tickペース** 実行され、各アクションをサーバの
  tickに渡す。だからアクションは実時間をかけ、結果に反応できる。移動はCC流のブロック
  「ホップ」で、専用レンダラがスライドを描く。
- 保存プログラムはディスクアイテムの **データコンポーネント** に格納。データは座標ではなく
  媒体（アイテム）に属し、一緒に持ち運べる。

## ロードマップ

- ✅ JSエンジン、コンピュータ＋端末/エディタ、プログラム可能タートル、ディスク
- ⏳ 有線コンピュータネットワーク（プレイヤー実装のスイッチ/ルータ/VPN）
- ⏳ 周辺機器（モニター、レッドストーンI/O）
- 🌟 型補完付きのゲーム内コードエディタ
- 🔭 NeoForge対応

## コントリビュート

Issue・Pull Request歓迎。公用語は英語です。

## ライセンス

MIT — [LICENSE](./LICENSE) を参照。

本modは [Mozilla Rhino](https://github.com/mozilla/rhino)（MPL-2.0）を同梱しています。
[THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md) を参照。

## 謝辞

[CC:Tweaked](https://tweaked.cc/) に着想を得ています。
[Mozilla Rhino](https://github.com/mozilla/rhino) と [Fabric](https://fabricmc.net/) で構築。
