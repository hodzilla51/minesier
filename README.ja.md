# MineSIer

[English](./README.md) · [日本語](./README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-green.svg)
![Loader: Fabric](https://img.shields.io/badge/Loader-Fabric-blueviolet.svg)

**Minecraftの中に、プログラム可能な業務システムを作る。** MineSIerは、ワールド内で
JavaScriptを書き、ディスクでコードとデータを持ち運び、コンピュータをネットワーク化し、
レッドストーンやモニターを制御し、用途に合わせたタートルを組むためのシステム統合
サンドボックスです。

[CC:Tweaked](https://tweaked.cc/) に着想を得ていますが、単なる「ComputerCraftの
JavaScript版」ではありません。プログラム可能な機械、可搬ストレージ、信号、ネットワーク、
ロボット装備を組み合わせて、運用できる仕組みを作ることを目指しています。

> ⚠️ **実験的・開発初期段階。** 対象は Minecraft 26.2（Fabric）。仕様は変わり得ます。
> フィードバック・コントリビューション歓迎。

## 特徴

- **コンピュータブロック** — 設置して右クリックすると複数行エディタ付きの端末が開く。
  JavaScriptを書いて **Run**（または `Ctrl`/`Cmd`+`Enter`）で実行、結果を表示。
- **`print(...)`** とスクロールバック表示。値だけでなく好きなだけ出力できる。
- **ブロックごとに独立したサンドボックスVM** — 各コンピュータが隔離されたJSエンジンを持つ
  （ホストのファイル/ネット/リフレクションには触れない。暴走ループも打ち切られる）。
- **プログラム可能なタートル** — コードで動かすロボット。
  移動、採掘、設置、検査、燃料、インベントリ、ネットワーク、近接スキャンを扱える。
  **1マスずつなめらかに移動**し、実行中に世界の状態へ反応する。
- **タートル装備** — 足・腕・天面の専用スロット。足パーツで移動特性と燃費が変わり、
  腕には通常のツールを持たせて採掘でき、天面モジュールで近接センサーなどの機能を追加する。
- **インベントリ** — タートルは掘った物を回収し、選択スロットから設置する。
- **可搬ディスク** — プログラム、ライブラリ、JSON、設定、ログをテキストファイルとして
  ディスクアイテムに保存。データはディスク側に乗るので、抜いて別の機械へ持ち運べる。
- **有線ネットワーク** — コンピュータとタートルの各面がNICを持つ。ブリッジ、スイッチ、
  ルータ、VPN、アプリケーションプロトコルを普通のJSで作れる。
- **レッドストーンとモニター** — バニラ信号を読み書きし、ワールド内の表示器へ文字を出せる。
- **永続化** — コンピュータ/タートルの状態はワールドに保存される。

## クイックスタート

ブロックを入手（クリエイティブ or コマンド）:

```
/give @s minesier:computer
/give @s minesier:turtle
/give @s minesier:disk
/give @s minesier:wheel_foot_part
/give @s minesier:crawler_foot_part
/give @s minesier:hover_foot_part
/give @s minesier:proximity_sensor_module
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

`forward()` `back()` `up()` `down()` `turnLeft()` `turnRight()` `dig()` `place()`（選択スロットから設置）
`place(id)`（例: `"minecraft:stone"`） `detect()` `inspect()` `getFuelLevel()`
`refuel(n)` `wait(ticks)` `select(n)` `getSelectedSlot()` `getItemCount(n)` `scan()`

タートルには別画面の装備スロットがあります:

- **足** — 未装着、タイヤ、無限軌道、ホバー。未装着は遅い。タイヤは石やレンガなど
  pickaxeで掘れる硬いブロック上で速く、無限軌道は悪路向け、ホバーは地形カテゴリを無視し
  空中移動にも強いかわりに燃費が重い。
- **腕** — バニラ/Modのツール。`dig()` はそのツールをプレイヤーが使った時に近い採掘挙動、
  採掘速度、耐久値、エンチャントを使う。
- **天面** — ユーティリティモジュール。Proximity Sensorを付けると `turtle.scan()` が使え、
  タートル中心の7x3x7範囲にある非空気ブロックを返す。

グローバル: `print(...)`。単発評価用の `/js <式>` コマンドもあります。

### JavaScript APIバージョン

スクリプトから `minesier.apiVersion` と `minesier.apiVersionString` を確認できます。
互換性と非推奨化の方針は [JavaScript API versioning](docs/javascript-api-versioning.md) を参照。

### モジュール（`require`）

CommonJS スタイルの `require` で別プログラムを読み込めます。`lib/` にコードをまとめてどこからでも使えます。

```js
// "lib/mathx" として保存
exports.double = function (n) { return n * 2; };
module.exports.PI = 3.14159;
```

```js
// メインプログラム
var mathx = require("lib/mathx");
print(mathx.double(21)); // 42
```

`require(name)` はディスク上のそのファイルを一度実行して `module.exports` を返します。結果は実行中キャッシュされ、ライブラリを編集したあとプログラムを再実行すれば反映されます。

### 有線ネットワーク

コンピュータとタートルには各面にNICがあります。Cableをつなげた面同士で、同じケーブル連結成分に
あるデバイスへフレームを送れます。アドレスはワールド保存中は変わらず、タートルが移動しても維持されます。

```js
print(net.address());                 // このコンピュータのMACアドレス
net.send("02:12:34:56:78:9a", "hello");

var frame = net.receive();            // フレームがなければ null
if (frame) print(frame.source, frame.data);
```

現在は文字列ペイロード、4 KiBまで、NICごとに64フレームまでです。

### ブロードキャストとアドレス解決（ARP）

`net.broadcast()` はL2ブロードキャストアドレスを返します。同じセグメントの全NICが受理するので、
ARP（「このアドレス誰？」を全員に問い合わせ → 所有者がunicastで返答）を自分で実装できます。
ARPはあえて組み込みにしていません——**自分で作るのが目的**です。

```js
// レスポンダ: 自分の論理名への "who-has" ブロードキャストに返答する
net.nic("back").onReceive(function (frame) {
  if (frame.destination === net.broadcast() && frame.data === "who-has:node-a") {
    net.send(frame.source, "is-at:node-a");
  }
});

// リクエスタ: node-a の MAC を探してから直接通信する
net.send(net.broadcast(), "who-has:node-a");
var reply = net.receive();
if (reply) net.send(reply.source, "hello node-a");
```

### 複数NICとpromiscuous受信

コンピュータの各面が独立したNICです。`front`（画面面）、`back`、`left`、`right`、`up`、`down`。
`forward` は `front` の別名です。

```js
var front = net.nic("front");
var back  = net.nic("back");

front.setPromiscuous(true);       // この面を通る全フレームを受信

var frame = front.receive();
if (frame) back.forward(frame);   // 送信元・宛先を保持したまま転送 → ブリッジの基礎
```

`send(dst, data)` はそのNICのアドレスを送信元として新しいフレームを送ります。
`forward(frame)` はフレームをそのまま送り、プレイヤー実装スイッチの基礎になります。
`onReceive(handler)` はフレームが届いたときだけ呼ばれるコールバックを登録します。

### マネージドスイッチ

Switchブロックは6ポートのラーニングスイッチで、初心者向けの手軽なL2ネットワークを作れます。
入力ポートごとに送信元アドレスを学習し、既知の宛先にはunicast、未知の宛先には他の全ポートへfloodします。
スパニングツリーは持たないので、物理的なループは避けてください。

### IPv4スタイルのパケット

`ip` はIPv4に着想を得たL3パケットエンベロープを提供します。送信元・宛先のドット表記アドレス、TTL、
IPプロトコル番号（TCPは `6`、UDPは `17`）、文字列ペイロードを持ち、通常の `net` フレーム内で運びます。

```js
var packet = ip.create("10.0.1.10", "10.0.2.20", 17, "hello");
net.send("gateway-mac", ip.encode(packet));
```

`ip.forward(packet)` はTTLをデクリメントし、0になると `null` を返します。

### 暗号

`crypto` はプレイヤー実装のVPNや認証プロトコル向けの標準プリミティブを提供します。
バイナリ値はBase64文字列です。X25519で共有シークレットを確立し、HKDF-SHA-256でAES鍵を導出し、
AES-GCMで暗号化・認証します。

```js
var keys   = crypto.x25519KeyPair();
// keys.publicKey を相手と交換してから:
var shared = crypto.x25519SharedSecret(keys.privateKey, peerPublicKey);
var key    = crypto.hkdfSha256(shared, "", "minesier-vpn-v1", 32);
var enc    = crypto.aesGcmEncrypt(key, "hello");
```

その他: `randomBytes(count)`、`sha256(data)`、`hmacSha256(key, data)`。

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

- ✅ JSエンジン、コンピュータ＋端末/エディタ、プログラム可能タートル、可搬ディスク
- ✅ 有線ネットワーク基盤、複数NIC、プレイヤー実装のブリッジ/ルータ、マネージドスイッチ
- ✅ レッドストーンI/O
- ✅ モニター
- ✅ 常駐実行（端末を閉じても動くtimer、ワールド再読み込み、startupディスク）
- ✅ タートル装備（足の移動特性、腕ツール、天面モジュール；装備パーツをモデルに反映）
- ✅ ケーブルの方向付きレンダリング — 接続先に向かって伸びる細いワイヤー表示
- ✅ コンピュータのIDEレイアウト — ファイルツリーペイン＋3ペイン構成
- ⏳ ブロック・アイテムテクスチャ（一部でプレースホルダーを使用中）
- ⏳ エディタ改善（複数ファイルタブ、シンタックスハイライト、実行/停止コントロール）
- 🌟 タートルのネットワーク制御プレーンと常駐デーモン
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
