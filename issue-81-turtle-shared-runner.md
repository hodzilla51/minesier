# #81 設計書: Turtle を Runtime Handle Registry に接続する（shared runner 化）

> 前提: #82（`LivenessRegistry` + handle 述語化, PR #83）がマージ済み。本書はその上で
> turtle を resident 実行に対応させる。**実装方針は threading 制約により一意に決まる**ので、
> その「なぜ一意か」を §2 で先に固定してから具体化する。

---

## 1. #82 が turtle に残した状態（出発点）

#82 で turtle 側に入っているもの:

- `JsComputer` は turtle VM も共有するクラスなので、**registry は turtle VM にも既に在る**。
  `every`/`after`/`onReceive` のグローバルも turtle VM で動く（`net` は `TurtleManager.run` が
  `vm.setNetwork(...)` するため有効）。
- `LivenessRegistry.Kind.ACTION_RUNNER` は **enum 予約済み**（resident-source=false、busy のみ）。
- `TurtleNetworkState` に lock-free compare-and-clear `clearReceiveListener(iface, expected)` は
  **実装済み**。

まだ無い / 壊れているもの（#81 の対象）:

| 症状 | 原因（実コード） |
|---|---|
| turtle で `every`/`after` を張っても誰も発火しない | `TurtleManager.tickAll`（L151-165）は `brain.tick()` を呼ぶだけで `vm.drainDueTimers()` を一切呼ばない |
| turtle で `onReceive` を張ると VM がリーク | foreground program 終了で `TurtleBrain.runProgram`（L109-124）が `finished=true` → `TurtleManager` が ACTIVE から除去するが、`TurtleNetworkState.NicState.listener` に VM をキャプチャした lambda が残留。`stopAllHandles()` は turtle 経路で一度も呼ばれない |
| turtle daemon は「動作中」表示も Stop も無い | turtle liveness は `TurtleManager.isRunning`（L147-149, ACTIVE membership）で判定され、registry を見ていない |

---

## 2. 核心的制約 — なぜ実装方針が一意か（最重要）

turtle の JS 実行モデル（`TurtleBrain` L11-22, L93-124, L336-360）から、resident callback の
実行場所は **論理的に一つしか選べない**。順に潰す。

### 制約 A: turtle の JS は専用 worker thread でしか走らない

`TurtleBrain.start()`（L93-99）が `"minesier-turtle"` worker thread を起こし、
`vm.run(program)` をそこで実行する。`turtle.*` 呼び出しは `call()`（L337-360）で
**worker をブロック**し、`tick()`（server thread, L141-192）が動作を実行して起こす、という
ハンドオフ。つまり foreground program 実行中、worker は `vm.run()`（`synchronized(this)`）の
中に居続け、**VM monitor を実行終了まで保持する**。

### 制約 B: Rhino scope は単一スレッド

1 VM の scope 上で 2 スレッドが同時に JS を実行してはならない。よって timer/listener callback
（これも JS 実行）は、foreground program とも、互いとも、**絶対に時間的に重ねられない**。
→ **「callback を server thread で走らせる」案は即死**（worker が foreground 実行中なら scope
が二重実行になる）。

### 制約 C: turtle callback は blocking な `turtle.*` を呼びうる

computer の timer callback は同期的に一瞬で終わる。turtle の callback は
`turtle.forward()` 等を呼べ、それは `TurtleBrain` の tick-pacing（PACE_TICKS=14 等）で
**複数 tick にまたがってブロックする**。→ callback も brain のハンドオフを通す必要がある。
computer の `drainDueTimers`（server thread で同期発火）をそのまま turtle に使えない。

### 結論（一意な道）

> **resident callback（timer fire / frame 配送）は、foreground program と同じ単一 runner
> thread 上で、brain のハンドオフを介して直列に実行するしかない。**

これを満たす最小構造が「**shared runner**: foreground 終了後も worker を生かし、
job キュー（foreground → 以後は timer fire / frame 配送）を直列処理する」。#82 が
ACTION_RUNNER を「busy だけに効く handle」として予約したのは、まさにこの runner を
registry に表現するため。

---

## 3. 設計: per-turtle shared runner

### 3.1 全体像

```
TurtleManager.run()
  └─ ACTIVE に Running 追加, brain.start()
        └─ runner thread 起動
              1) ACTION_RUNNER handle add → vm.run(foreground program)   [busy]
                 ACTION_RUNNER handle remove
              2) while (vm.isResident() && !aborted):           [resident, idle]
                   job = queue.take()           // timer-fire / frame-deliver
                   ACTION_RUNNER add; job.run(); ACTION_RUNNER remove   [busy, 一瞬]
              3) finished = true

TurtleManager.tickAll()  [server thread, 毎 tick]
  └─ brain.tick()                       // 実行中 job の turtle.* を 1 tick 進める
     for due in vm.collectDueTimers():  // 純 Java: countdown 減算 + 期限到来を収集
        brain.enqueue(timerFireJob(due))
     if brain.isFinished(): finish()    // = registry not alive or aborted
```

### 3.2 JsComputer 側: 実行スレッドの抽象化（computer 無変更で turtle 差し替え）

callback を「VM を所有するスレッド」で実行するための SPI を `JsComputer` に足す。

```java
// JsComputer
public interface JsExecutor { void execute(Runnable job); }

// default: 呼び出しスレッドで即実行（= computer。server thread, 同期）
private JsExecutor executor = Runnable::run;
public void setExecutor(JsExecutor executor) { this.executor = executor; }
```

- **listener**: `registerReceiveListener` が作る `NetworkListener` を
  `frame -> executor.execute(() -> invokeReceiveHandler(callback, frame))` にする。
  - computer: `executor` は inline → 今と同じ（server thread で同期発火）。
  - turtle: `executor` は runner キューへ post → worker thread で発火。
- **timer**: §3.4 の collect/fire 分割で、fire を `executor.execute(...)` 経由にする。

これで「callback はどのスレッドで走るか」が VM ごとに 1 箇所（executor）で決まり、
制約 B/C を構造的に満たす。computer の挙動は不変。

### 3.3 liveness メソッドの lock を registry 自前 lock だけに閉じる（制約 A 対策）

#82 で `isAlive/isResident/hasTickDrivenWork` は `synchronized(JsComputer.this)` にしてある。
turtle では foreground 実行中に worker が VM monitor を保持し続ける（制約 A）ため、
**server thread の `tickAll` がこれらを read しようとすると foreground 終了までブロック**する。

→ これら 3 つの **read 述語から `synchronized(this)` を外す**。registry が自前 lock を持つので
スレッド安全性は registry 側で閉じている。`stopAllHandles` も registry 委譲なので外せるが、
呼ぶのは runner / server の限られた点なので `this` を取らない形にする（registry lock のみ）。

> 一方 `drainDueTimers`（→ §3.4 で `collectDueTimers`+fire に分割）と
> `registerReceiveListener` / `invokeReceiveHandler` は **scope を触る**ので、引き続き runner
> thread でのみ呼ぶ。これらは `synchronized(this)` を維持してよい（呼ぶのが runner だけなら
> 競合しないが、二重防御として残す）。

### 3.4 timer の countdown と fire を分離

制約 C より「減算（毎 tick・純 Java・どのスレッドでも可）」と「発火（JS・runner のみ・
idle 時のみ）」を分ける。#82 の `drainDueTimers` を 2 段に割る:

```java
// JsComputer
// server thread 可。registry lock のみ。JS を実行しない。
public List<TimerHandle> collectDueTimers();   // 各 TIMER の tickDown()、due を返す
// runner thread のみ。JS 実行。完了後 one-shot は registry.remove（§8 の finally 規律を踏襲）。
public List<String> fireTimer(TimerHandle handle);
```

- **computer**: `serverTick` は `collectDueTimers()` の各 due を即 `fireTimer(...)`（同一 server
  thread で背中合わせ＝従来と同値）。挙動不変。
- **turtle**: `tickAll` が `collectDueTimers()` を server thread で実行（countdown は実時間で
  正確に進む）、due を **runner キューへ enqueue**。runner が idle になり次第 `fireTimer` を
  worker thread で実行。長い callback 実行中に貯まった due はキューで直列化される。

> `TimerHandle` は #82 で JsComputer の private static class。`collectDueTimers`/`fireTimer` を
> 公開するため、`TimerHandle` を package-private に上げる（同 `js` パッケージの runner 補助型から
> 参照する場合）。turtle runner も `js` パッケージに置けば private のままでよい — 配置は §5 で確定。

### 3.5 ACTION_RUNNER handle のライフサイクル

- runner が **1 つの JS job を実行している間だけ** registry に ACTION_RUNNER handle を入れる
  （foreground program 全体、および各 timer-fire / frame-deliver job の実行中）。
- job が JS を抜けたら（turtle.* の途中ではなく、callback 関数から return したら）remove。
- 効果:
  - `isAlive()` = ACTION_RUNNER ∨ TIMER ∨ LISTENER。
  - **busy（新規 run 拒否）** = ACTION_RUNNER の有無。
  - **resident** = TIMER ∨ LISTENER（#82 のまま）。

---

## 4. lifecycle / 述語マッピング（TurtleManager）

### 4.1 ACTIVE membership ⟺ `registry.isAlive()`

Running を ACTIVE に保つ条件を `brain.isFinished()` の否定にするが、`brain`（runner）は
`while (vm.isResident() && !aborted)` でループするので、**`brain.isFinished()` は
「registry がもう alive でない（or aborted）」と等価**になる。よって `tickAll` の終了判定
（L160）は構造的にそのまま使える。

### 4.2 述語置換表

| 用途 | 旧 | 新 |
|---|---|---|
| 新規 run を拒否する「busy」（`run` L42-50） | ACTIVE に同 pos あり | `vm` に **ACTION_RUNNER がある**（= JS job 実行中）。resident-idle は拒否しない |
| インベントリ所有 / `isRunning(level,pos)`（L147） | ACTIVE membership | 不変（ACTIVE = `registry.isAlive()` = busy ∨ resident。daemon もインベントリを所有する） |
| Running を畳む（`tickAll` L160） | `brain.isFinished() \|\| isAborted()` | 不変（runner が resident の間ループするので意味が拡張される） |
| 「動作中」表示 / プロセス名 | （無し） | `vm.isResident()` ベースで computer と同じ UI を turtle にも |

### 4.3 新規 run が resident daemon に当たった場合

computer の `runCommand` と同じ意味にする: **busy（ACTION_RUNNER あり）なら拒否**、
busy でなく resident（idle daemon）なら **`vm.stopAllHandles()` で daemon を畳んでから**
foreground を起動。`TurtleManager.run` の先頭ガードをこの 2 段に変更する。

---

## 5. スレッド / ロック不変条件（厳守）

- **INV1（単一 JS スレッド）**: 1 VM の JS は runner thread 1 本でのみ実行。server thread は
  JS を一切実行しない（computer は server thread が runner を兼ねる＝矛盾しない）。
- **INV2（server が触れる VM メソッド）**: server thread が running な turtle VM に対して呼べるのは
  **registry 自前 lock だけで閉じる純 Java メソッド**のみ —
  `isAlive/isResident/hasTickDrivenWork/collectDueTimers/stopAllHandles`。`synchronized(this)` を
  これらから外す（§3.3）。scope を触るメソッドは server から呼ばない。
- **INV3（frame は runner へ）**: `TurtleNetworkState` の listener は frame を runner キューへ
  post するだけ（`executor` 経由）。`invokeReceiveHandler`（JS）は runner thread で走る。
  server thread では走らせない。
- **INV4（ロック順序）**: runner は `vm.run()`/`fireTimer` 中に VM monitor を保持し、`turtle.*`
  で `brain.lock` を取りにいく（`call()`）。server tick は `brain.lock` のみ（`tick()`）。
  **server は VM monitor を取らない（INV2）** → `VM monitor → brain.lock` の一方向のみ →
  デッドロック・サイクル無し。
- **INV5（onServer の維持）**: `TurtleNetworkState.onServer`（L294-318）の worker→server hop は
  そのまま。runner（worker）が VM monitor を保持したまま server を待つが、server は VM monitor を
  要求しない（INV2）ので #82 で論じたデッドロックは生じない。これが INV2 を必須にする理由。

---

## 6. 具体的な変更（ファイル単位・これで全部）

### 6.1 `JsComputer`（js）
1. `JsExecutor` SPI + `setExecutor` + default inline（§3.2）。
2. `registerReceiveListener` の listener を `executor.execute(...)` でラップ（§3.2）。
3. `drainDueTimers` を `collectDueTimers()` + `fireTimer(TimerHandle)` に分割（§3.4）。
   computer の `ComputerBlockEntity.serverTick` は collect→fire 背中合わせに直す（同値）。
4. `isAlive/isResident/hasTickDrivenWork/collectDueTimers/stopAllHandles` から
   `synchronized(this)` を外し、registry 自前 lock に委譲（§3.3）。`fireTimer` /
   `registerReceiveListener` / `invokeReceiveHandler` は `synchronized(this)` 維持。
5. `TimerHandle` の可視性調整（§3.4 の注記。runner 配置次第で private のまま可）。

### 6.2 `TurtleBrain`（turtle）
1. runner thread を foreground 終了で殺さず、resident ループに入る（§3.1 の 2）。
   `runProgram` を「foreground run → `while(vm.isResident() && !aborted){ job=queue.take(); run job }`」へ。
2. job キュー（`BlockingQueue<Runnable>` 相当）と `enqueue(Runnable)` を追加。
   timer-fire job（`tickAll` から）と frame-deliver job（`executor` から）を受ける。
3. `vm.setExecutor(this::enqueue)` を `start()` で設定。
4. ACTION_RUNNER handle の add/remove を job 実行の前後で行う（§3.5）。registry へ handle を
   足すための小さな口（`JsComputer` に `addActionRunner()/removeActionRunner()` か、
   `LivenessRegistry.RuntimeHandle` を runner 側で生成して `JsComputer` 経由で add/remove）。
   → **`JsComputer.beginForegroundJob()/endForegroundJob()` を公開**し、内部で ACTION_RUNNER
   handle を出し入れするのが最小。runner はこれを各 job の前後で呼ぶ。
5. `abort()` は resident ループも抜ける（`aborted` を見てループ脱出 → `finished=true`）。
6. `tick()` は不変（実行中 job の `turtle.*` を 1 tick 進めるだけ。job が foreground でも
   callback でも同じハンドオフ）。

### 6.3 `TurtleManager`（turtle）
1. `run` 先頭ガードを §4.3 に変更（busy=ACTION_RUNNER で拒否、resident-idle は
   `vm.stopAllHandles()` してから起動）。`run` 内で `vm.setExecutor` は brain 側がやるので不要。
2. `tickAll` に **timer countdown 駆動**を追加: `brain.tick()` の後に
   `for (due : vm.collectDueTimers()) brain.enqueue(() -> appendOutput(vm.fireTimer(due)))`。
   `fireTimer` 自体は runner で走るので、ここで渡すのは「runner 上で fire して出力を回収する
   job」。出力回収は既存 `flushOutput`/`drainOutput` 経路に合流させる。
3. 終了判定（L160）は不変（§4.1）。`finish()`（writeback）も不変だが、**resident 中は呼ばれない**
   （runner が resident ループ中は `brain.isFinished()` が false）。daemon が自走で移動・採掘した
   結果も、最終的に daemon 終了時の 1 回の `finish()` で writeback される。

### 6.4 `TurtleNetworkState`（turtle）
- 変更なし。listener の発火スレッドは `JsComputer.executor`（runner）が決めるので、
  `TurtleNetworkState` 側は #82 のままでよい。`offerFrame`（L69-92）の `NetworkManager.schedule`
  → listener.onFrame が `executor.execute(post-to-runner)` を呼ぶだけになる。
  - 注意: `offerFrame` の既存 stale-guard（`if (nic.listener == listener)`, L82）は維持。
    frame が runner キューに入ってから fire されるまでの間に listener が張り替わったら、
    `invokeReceiveHandler` 側でも #82 の identity 一本化が効く。

### 6.5 `ComputerBlockEntity`（block）
- `serverTick` の `drainDueTimers()` を `collectDueTimers()`→`fireTimer()` 背中合わせに置換
  （同値・同 server thread）。それ以外は #82 のまま。

---

## 7. エッジケース（実装時に必ず潰す）

1. **resident callback 中の Stop**: GUI Stop は computer の `stopResident` 相当を turtle にも
   用意 → `vm.stopAllHandles()` ＋ `brain.abort()`。runner ループを抜け、`finish()` で writeback。
   実行中 turtle.* は `call()` の `aborted` 判定で `IllegalStateException` 巻き戻し（既存）。
2. **callback がタートルを動かす**: `TurtleAccess`（`world`）の pos は move で更新され、
   `finish()` は最終 pos に writeback（既存ロジックそのまま）。daemon が長距離移動しても
   writeback は 1 回・最終位置で正しい。
3. **frame 到着の wakeup と空 tick**: resident-idle の runner は `queue.take()` でブロック。
   frame が来れば executor が enqueue して起こす。timer が無く listener だけなら
   `collectDueTimers` は空でも `isResident()`=true なので ACTIVE に残る（正しい）。
4. **実行予算**: 現 `maxTurtleProgramTicks`（L157）は foreground program の暴走対策。resident
   daemon に同じ上限を当てると正規の常駐が殺される。→ **予算は foreground job 単位でリセット**
   （`r.ticks` を job 開始でゼロ）。または timer/listener callback は「1 発火あたりの tick 上限」
   に別予算を当てる。**これは要決定**（§9 の唯一の open 判断）。
5. **二重起動**: busy（ACTION_RUNNER）中の `run` は従来どおり「busy — wait」。resident-idle 中の
   `run` は §4.3 で daemon を畳んで起動。
6. **chunk unload / entity 退避中の frame**: `TurtleNetworkState.onServer` が level==null で
   fallback する既存挙動を維持（runner が無効 level で動くのを防ぐ）。
7. **world reload**: turtle BE は ticker も `residentSource` 永続も持たない（確認済み）。
   よって **reload で turtle daemon は復活しない** → これは #81 スコープ外（§9）。

---

## 8. テスト計画

### Unit（Rhino 必要・fake で worker を単純化）
runner の thread モデルは unit で完全再現が難しいため、**runner を介さず JsComputer の新 API を
直接叩く**層を厚くする:
- `collectDueTimers()` が server-thread-safe（JS 非実行）で countdown を正しく減算し due を返す。
- `fireTimer(due)` が JS を実行し one-shot を unref、repeating を rearm。
- `setExecutor` 差し替えで listener fire が executor 経由に乗る（fake executor が job を捕捉）。
- ACTION_RUNNER: `beginForegroundJob/endForegroundJob` で busy(isAlive) が立つ/降りる、
  resident は TIMER/LISTENER のみで判定される。

### Integration / GameTest（後続可）
- turtle daemon: `every` で自走移動、`onReceive` で反応、Stop で停止、二重起動の busy/畳み。
- 長い callback 中に貯まった due timer がキューで直列発火し取りこぼさない。
- frame 配送が runner thread で走り、server thread で JS が走らないこと（INV1/INV3）。

---

## 9. スコープ

### In scope（#81）
- `JsExecutor` SPI ＋ turtle runner（shared runner 化）
- timer countdown/fire 分割（`collectDueTimers`/`fireTimer`）＋ tickAll への countdown 駆動配線
- listener fire の runner thread 化（executor 経由）＋ ACTION_RUNNER handle 実体化
- liveness read 述語の lock を registry 自前 lock に閉じる（INV2）
- 新規 run の busy/resident 畳み込み、turtle 版 stopResident、述語マッピング
- 上記 unit テスト

### Out of scope
- **reload across-restart の daemon 復活**（turtle BE に ticker + 永続が無い）→ 既存ロードマップの
  「resident v2（persistence / startup）」へ。computer 側の `pendingRestart`/`residentSource` 機構を
  turtle BE に移植する別作業。
- `nextFireTick` 最適化（countdown 据え置き）

### 唯一の要決定（§7-4）
resident callback の実行予算をどう切るか（job 単位リセット vs 発火あたり上限）。
**推奨: job 単位で `r.ticks` リセット ＋ 1 発火あたり別途上限**。暴走 callback を殺しつつ
正規の常駐 daemon を無限に走らせられる。ここだけ実装者の判断 1 点。

---

## 10. なぜこの設計が「唯一」か（要約）

- 制約 A/B/C（§2）から、callback は **runner thread・brain 経由・直列**でしか実行できない →
  shared runner は選択ではなく帰結。
- callback の実行スレッドを VM ごとに切り替える必要 → `JsExecutor` SPI が最小の抽象（computer
  は inline、turtle は runner）。
- server thread が running VM の liveness を読む必要（tickAll の判定）＋ worker が VM monitor を
  長時間保持（制約 A）→ read 述語の lock を registry 自前 lock に閉じる（INV2）以外に
  デッドロックを避ける道が無い。#82 が registry に専用 lock を最初から入れてあるのはこのため。
- timer の正確な countdown（実時間）と JS fire（runner・idle 時）の両立 → collect/fire 分割は必然。

実装者の自由は §9 の予算判断 1 点のみ。それ以外は上記の制約から一意。
