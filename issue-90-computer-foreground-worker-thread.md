# 調査書: Computer フォアグラウンド JS 実行を worker thread 化する（server-thread freeze の解消）

> ステータス: **deferred（CTF 本格化時に着手）**。ただし調査でサーバースレッドを
> 数秒フリーズさせられることを確認済み。#69 が掲げる containment 保証
> 「in-game target は殴られるが Minecraft サーバーは無傷 / no path to the real server's
> threads」が、フォアグラウンド経路で**現状破れている**。その根拠と修正方針を先に固定する。

---

## 1. 結論

Computer のフォアグラウンド JS 実行（`run()`）は **server メインスレッド上で同期実行**され、
その暴走ガードは**命令数カウントのみ**で**時間ベースの deadline が無い**。
`maxScriptInstructions = 200_000_000` は「いつか止まる」保証でしかなく、Rhino インタプリタで
2 億命令を回り切るまでに server tick（50ms/tick）が数秒間停止する = 体感フリーズ。

```js
for (var i = 0; i < 1e8; i++);   // アボートまでに必ず ~2 億命令ぶん回る → 数秒フリーズ
```

「lightweight / no server freeze」の看板および #69 の containment 保証と、実装が矛盾している。
Turtle は既に worker thread 方式（`TurtleBrain`）で解決済みなので、Computer も同じモデルに
寄せるのが正道。

---

## 2. 調査で確認した事実（コードレベル）

### 2.1 フォアグラウンド実行は server メインスレッド上・同期

- `MineSIerNet.java:84` — `context.server().execute(() -> handleRun(player, payload))`
  で **server メインスレッド**へ投入。
- `handleRun`（`MineSIerNet.java:309`, L326）→ `computer.runCommand(payload.command())`。
- `ComputerBlockEntity.runCommand`（L228-240）→ `computer.run(command)`（L236）を
  **同期呼び出し**。
- `JsComputer.run`（L406〜）は `cx.evaluateString(...)`（L502）をその呼び出しスレッド上で
  ブロッキング実行。→ **evaluateString が返るまで server tick はブロックされる**。

対して Turtle は `TurtleBrain.java:108` で `new Thread(this::runProgram, "minesier-turtle")`
の専用 worker に逃がしている（L107 `vm.setExecutor(this::enqueue)`）。**Computer だけが
逃がしていない。**

### 2.2 ガードは命令数のみ・時間 deadline 無し

`SafeContextFactory.observeInstructionCount`（L47-53）:

```java
long[] c = COUNT.get();
c[0] += instructionCount;
if (c[0] > LIMIT.get()) {
  throw new IllegalStateException("script exceeded instruction limit (runaway?)");
}
```

- `MineSIerConfig.maxScriptInstructions = 200_000_000`（フォアグラウンド）
- `MineSIerConfig.instructionObserveEvery = 100_000`（観測間隔）
- 時間を見ている箇所は**どこにも無い**。よってタイトループは必ず 2 億命令ぶん回ってから
  アボートする。best-effort-eventual であって、tick 予算（50ms）とは無関係。

### 2.3 命令数だけでは原理的に防げない穴（ネイティブコード）

命令オブザーバは **Rhino バイトコード実行時にしか呼ばれない**。Java 側で完結する処理は
命令数を刻まないので、命令数上限にも時間制限にも引っかからない:

- `"a".repeat(1e9)` / 巨大文字列連結
- 破滅的バックトラッキング正規表現
- 巨大 `Array` 確保 / `JSON.parse` の巨大入力

これらは現状**どの上限でも止められず**、単一のフォアグラウンド実行でサーバーを無限に
止められる。命令数カウントの延長線上（deadline 追加）では塞げず、
**「サーバースレッドが暴走 VM を見捨てて先に進める」= worker thread + watchdog** でしか
根治しない。

### 2.4 resident timer コールバック経路（参考・影響は小）

`ComputerBlockEntity.serverTick`（L285〜）→ `drainDueTimers()` → `fireTimer` → `invokeBounded`
（L321）も server スレッド上で JS を走らせるが、予算は `maxCallbackInstructions = 100_000`
（サブ ms）なのでバイトコードループの実害は小さい。ただし 2.3 のネイティブコード穴は
コールバック経路にも同様に存在する（severity は低）。

---

## 3. なぜ worker thread 方式が正道か（deadline では不十分）

### 暫定案（deadline 追加）とその限界

`observeInstructionCount` にスレッドローカルの実行開始 `nanoTime` を持たせ、
`now - start > deadline` でも throw する、という小改修は **バイトコードループの数秒フリーズは
消せる**（1 tick 分 ~50ms のスタッターに落ちる）。実装コストは小さく既存の同期構造を崩さない。

**限界**: deadline チェックは「オブザーバが呼ばれること」に依存するので §2.3 の
ネイティブコード穴は塞げない。CTF の悪意ある相手を想定するなら不十分。

### 本命（worker thread 化）

Turtle が既に持つモデル（`TurtleBrain` 専用スレッド + `JsExecutor` SPI で world を触るジョブを
server スレッドへ hop、`TurtleNetworkState.onServer` の `isSameThread()` ホップ）を Computer にも
適用する。これにより:

- server tick は暴走 VM を待たず先に進める → §2.3 のネイティブコード穴も watchdog +
  thread 放棄で対処可能。
- #69 の containment 保証（server threads への到達路なし）を構造的に満たせる。

---

## 4. 実装方針（worker thread 化）

`JsComputer` は **Computer と Turtle が共有する VM クラス**であり、Turtle 側の
shared runner 設計（#81）で `JsExecutor` SPI・`beginForegroundJob`/`endForegroundJob`・
`collectDueTimers`/`fireTimer` 分割が既に入っている。Computer 版はその Computer 向け適用になる。

概略:

1. **runner thread**: Computer BE ごとに（あるいは #81 の shared runner を computer 向けに一般化して）
   専用スレッドを持ち、`run()` をそこで実行。foreground 実行中も server tick を止めない。
2. **出力**: 既存の `outputListener`（ストリーミング）で端末へ流し、最終結果は server スレッドへ
   ポストバックして transcript / NBT に反映。
3. **world を触る API の hop**: `redstone` / `monitor` / `net` など block/world 状態に触る API は
   `TurtleNetworkState.onServer` と同じく server スレッドへ hop させる（既存パターン流用）。
   ここが作業量の中心。
4. **watchdog / abort**: deadline 超過（および §2.3 ネイティブコード無反応）時に runner を
   interrupt / 放棄し、VM を隔離。Rhino の interrupt 可否と、放棄後の scope 再利用可否を要検証。
5. **単一 JS スレッド不変条件**: #81 の INV1〜INV5（1 VM の JS は runner 1 本、server は JS 非実行、
   liveness read は registry 自前 lock で閉じる、ロック順序 `VM monitor → brain.lock` 片方向）を
   Computer 経路にも適用。timer fire / net 受信も同 runner 上で直列化。

段階を切るなら:
- **段階 1（暫定・小）**: `observeInstructionCount` に wall-clock deadline（例: 50ms）を追加。
  バイトコードループの数秒フリーズを即座に潰す。看板と実装の矛盾を最小コストで縮める。
- **段階 2（本命・大）**: 上記 worker thread 化。CTF 本格化時に実施。

---

## 5. スコープ

### In scope（本 issue）
- Computer フォアグラウンド `run()` の worker thread 化（#81 の shared runner を computer に適用/一般化）
- world 触り API（redstone/monitor/net）の server-thread hop 化
- deadline + watchdog による暴走/ネイティブコード無反応 VM の隔離
- 段階 1（deadline 追加）を暫定緩和として先行投入するかは着手時判断

### Out of scope
- ネットワーク層の per-sender レート制限 / inbox backpressure → #68
- in-game DoS を観測可能なメカニクスとして見せる設計 → #69
- Turtle 側の shared runner 実装そのもの → #81（本 issue の前提テンプレート）

### 要決定（着手時）
- Rhino 実行スレッドを安全に interrupt / 放棄できるか（できなければ「隔離して自然死を待つ +
  新規 run は別 VM」等の代替）。
- 段階 1 の deadline を暫定で入れるか、段階 2 まで一気にやるか。

---

## 6. See also

- #69 — in-game DoS を realistic かつ contained に。**本 issue はその containment 保証
  （server threads への到達路なし）を破っている具体的欠陥。**
- #68 — 敵対的 CTF 向け Tier 1 DoS 上限のハードニング（本 issue と同じ CTF トラック）。
- #81 — Turtle shared runner 設計。`JsExecutor` SPI / `collectDueTimers`/`fireTimer` 分割 /
  INV1〜INV5 を提供する前提テンプレート。
