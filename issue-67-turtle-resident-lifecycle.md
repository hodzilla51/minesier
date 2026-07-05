# #67 設計書: Turtle 常駐ライフサイクル完成(resident v2 / #75 #77 #79 #78 #66 #76)

> 対象 issue: #67(親)、#75(状態永続)、#77(reload 復元)、#79(stop/replace)、
> #78(ターミナル UI)、#66(リモート制御プレーン)、#76(回帰テスト)。
> #80(仕様確定)は本書がその成果物。#81(shared runner)・#82(LivenessRegistry)は
> **マージ済みの前提**(PR #83/#84/#85)。

---

## 1. 背景と到達点(なぜ今この形か)

### 1.1 これまでの積み上げ

| 段階 | 成果 | 帰結 |
|---|---|---|
| resident v1(computer) | `every`/`after`/`clearTimers` + BE ticker + `residentSource`/`pendingRestart` NBT 永続。reload 後に source を再実行して daemon を復元 | computer は「閉じても・reload しても生きる」が完成済み |
| #82(PR #83) | `LivenessRegistry`: liveness を handle 集合(TIMER/LISTENER/ACTION_RUNNER)の述語に一本化。受信専用 daemon も resident | 「常駐とは何か」の定義が computer/turtle 共通になった |
| #81(PR #84/#85) | shared runner: turtle worker が foreground 終了後も生き、timer fire / frame 配送 job を直列処理。`JsExecutor` SPI、collect/fire 分割、INV1〜INV5 のロック規律 | turtle daemon が**セッション内では**完全動作(自走・受信・Stop) |

### 1.2 いま出来ないこと(本設計のスコープ)

実コード確認済みの残ギャップ:

1. **reload で turtle daemon が消える**(#77)。`TurtleBlockEntity` に ticker も
   `residentSource` 永続も無い。`TurtleManager.ACTIVE` は static メモリで、サーバ停止と
   共に消える。#81 §9 で明示的にスコープ外とされた部分。
2. **常駐メタデータが第一級でない**(#75)。「何が常駐しているか」は VM の handle 集合
   にしか存在せず、名前・source・永続形が無い。
3. **replace が無い**(#79)。`TurtleManager.run`(L42-50)は ACTIVE に同 pos が居れば
   **resident-idle でも一律 busy 拒否**。computer の `runCommand`(stopAllHandles →
   再実行)と非対称。#81 設計書 §4.3 の 2 段ガードは未実装のまま残っている。
4. **UI が常駐を隠す**(#78)。`MineSIerNet.handleOpenTerminal`(L182)は
   `TurtleManager.isRunning` で early-return するため、**走行中 turtle のターミナルは
   開くことすら出来ない**。開けた場合も常に `ProcessStateS2C(false, "")` を送る。
5. **リモート制御が無い**(#66)。ネットワーク越しに daemon を止める/一時停止する
   engine 保証の手段が無く、全て player script の自前実装になる。
6. **callback の tick 予算が無い**。#81 §7-4 の推奨「1 発火あたりの tick 上限」が未実装。
   foreground 予算(`maxTurtleProgramTicks`、tickAll L175-179)は
   `isForegroundDone()` 後は効かないので、`while(true) turtle.forward()` する callback
   は永遠に走る(命令数予算は blocking 呼び出しをほぼ消費しない)。

### 1.3 設計の北極星(#67/#80/#82 で合意済み・再掲)

- **computer と turtle は同一の常駐モデル**。差分は `turtle` API の有無と、
  「callback の JS がどのスレッドで走るか」(computer=server thread inline、
  turtle=runner thread)だけ。
- resident の定義は registry の述語(TIMER ∨ LISTENER)。**受信専用 daemon も常駐**。
- 新 top-level コマンドは既存常駐を**畳んでから**起動(computer の `runCommand` 準拠)。
- Stop は timers + listeners + プロセス状態を一括クリア。
- resident callback は foreground と同じ tick-paced な action path(shared runner)を通る。
- 常駐 daemon と別の foreground program は同時に走らない。

---

## 2. #80 の未決事項 3 点への回答(本書で確定)

1. **startup/reload は computer を鏡写しに出来るか** → **出来る。例外 1 点のみ文書化**。
   - disk 挿入時 `bootStartup`(C:/startup.js → D:/startup.js → legacy `startup`)を
     turtle にも移植。挿入経路は `TurtleBlock.useItemOn`(L118)と
     `TurtleDiskContainer`(L42/L59)の 2 箇所。
   - reload 復元は `residentSource` 再実行(§4)。
   - **turtle 固有の文書化すべき差**: 復元は「turtle が保存された物理位置・向き」から
     source を頭から再実行する。移動 daemon は中断地点の続きではなく**リプレイ**になる
     (JS クロージャは直列化できない、computer と同じ制約の turtle 的現れ)。座標に
     依存する daemon は自衛的に `turtle.scan()` 等で再定位する、を仕様とする。
2. **foreground abort と resident Stop は同一コントロールか** → **同一**。現行
   `TurtleManager.stop` が既に両対応(stopAllHandles + abort)。GUI Stop も #66 の
   `stop` も同じ entry point を叩く。分割しない。
3. **プロセス名は computer の first-line 規則のままか** → **そのまま**。
   `residentSource` 先頭非空行 40 字 cap。実装は共有 static helper に抽出(§7.6)。

これで #80 は「本書を #67 にリンクして close」できる。

---

## 3. 状態モデル(#75)— 誰が何を所有するか

### 3.1 真実の所在(不変条件)

```
資産                     所有者(daemon 稼働中)          永続化
──────────────────────────────────────────────────────────────
JS handle 集合           JsComputer.registry              されない(定義上不可能)
復元レシピ residentSource TurtleManager.Running.source     BE NBT(毎 tick flush)
fuel/inv/equip/slot      Running.world (TurtleAccess)     BE NBT(既存 syncLiveState)
transcript               Running.baseTranscript           finish() 時に BE へ
位置・向き               block state(移動の度に更新)     既存のまま
```

- **INV-P1**: `residentSource` は「今 reload が起きたら何を再実行すべきか」を常に表す。
  daemon 稼働中は Running が毎 tick BE へ flush(既存 `syncLiveState` に載せる)。
  daemon 終了(正常/Stop)で空文字を flush。**autosave がいつ走っても矛盾しない**
  (fuel/inv の dup 対策と同じ理由・同じ経路)。
- **INV-P2**: BE の `pendingRestart` は `loadAdditional` で
  `!residentSource.isEmpty()` から導出(computer L406 と同一)。NBT には書かない。
- **INV-P3**: 移動で BE が作り直されても矛盾しない。`syncLiveState` は「現在位置の BE」
  に毎 tick 書くので、hop 直後の新 BE も次 tick には residentSource を持つ。
  同一 tick 内に unload が来るケースは §5.3 の fold が最終 flush を行う。

### 3.2 TurtleBlockEntity への追加

```java
private static final String KEY_RESIDENT = "ResidentSource";  // computer と同 key 名
private String residentSource = "";
private boolean pendingRestart;
```

- `loadAdditional`/`saveAdditional` は computer(L404-406 / L431-433)を丸写し。
- `syncLiveState(...)` に `String residentSource` 引数を追加(§3.1)。
- `applyResult(...)` にも同引数を追加(finish 時の最終確定)。
- **クライアント漏洩対策**: turtle BE は描画のため `getUpdateTag` で全 NBT を client に
  送る(computer BE は送らない)。`residentSource` はプレイヤーのコードなので、
  `getUpdateTag` の結果から `KEY_RESIDENT` を remove して返す。access 制御
  (パスワード保護 turtle)との整合上必須。※ salt/hash も現状 update tag に乗っている
  ので、同じ場所で一緒に strip する(drive-by 修正、1 行)。

### 3.3 TurtleManager.Running への追加

```java
final String source;        // 起動コマンド全文 = 復元レシピ
boolean keepResidentOnFinish; // §5.3 の fold 用(unload/server-stop 時のみ true)
ServerPlayer viewer;        // 従来の player。null 可(無人復元)・差し替え可(§6)
```

`finish()` の residentSource 確定規則(**ここが心臓部**):

```
BE.residentSource = (keepResidentOnFinish || vm.isResident()) ? source : ""
```

- 正常終了で daemon が残った → source 保存(reload 復元対象)。
- Stop(abort + stopAllHandles)→ isResident()=false → 空文字 = 復元しない。
- unload fold(§5.3)→ handle は畳むが keepResidentOnFinish=true → source 保存。

---

## 4. 復元・startup(#77)

### 4.1 turtle BE に server ticker を追加

`TurtleBlock` に `ComputerBlock` L74 と同型の `BlockEntityTicker`(server 側のみ)を
追加する。ただし computer と役割が違う:

> **computer の ticker** = pendingRestart 処理 + 毎 tick の timer drain(VM が
> server-thread なので BE が直接 fire できる)。
> **turtle の ticker** = **pendingRestart のトランポリンだけ**。timer 駆動は今まで通り
> `TurtleManager.tickAll` が Running に対して行う(INV1: server thread は turtle VM の
> JS を実行しない)。二重駆動は構造的に起きない(ticker は Running が存在しない時
> しか仕事をしない)。

```java
// TurtleBlockEntity
public void serverTick() {
  if (!pendingRestart) return;
  pendingRestart = false;
  if (residentSource.isEmpty()) return;
  if (TurtleManager.isRunning(level, worldPosition)) return; // 保険(通常成立しない)
  TurtleManager.restartResident((ServerLevel) level, worldPosition, residentSource);
}
```

### 4.2 無人起動: `TurtleManager.restartResident`

現行 `run(level, pos, player, command)` の中身を `start(level, pos, viewer, command,
List<String> echoLines, boolean isRestart)` に抽出し、2 つの入口を作る:

- `run(...)`: 従来。echo は `"> cmd"` 形式、viewer = 実行プレイヤー。
- `restartResident(...)`: viewer = **null**、echo は `"[resident program restarted]"`
  1 行(computer L289 と同文言)。

viewer null 対応は 3 箇所だけ:
- `flushOutput`: `notifyClient && r.viewer != null` の時のみ send(transcript 追記は常に行う)。
- `finish()`: `r.viewer != null` の時のみ `TerminalScreenS2C` send。
- busy 通知(run 冒頭): 復元経路では到達しない(ticker が isRunning ガード済み)。

**復元の意味論**: source を頭から foreground として再実行 → 終了後 resident phase へ、
という通常 run と完全に同じ流れ。特別な「復元モード」は作らない(computer の
`runResident` と同じ思想)。初回実行で `print` される行も再実行で再び出る — これは
computer の既存挙動と同一で、仕様とする。

### 4.3 disk startup の移植

`ComputerBlock` L95-98 と同じ 2 行を turtle の disk 挿入 2 経路
(`TurtleBlock.useItemOn` L118 / `TurtleDiskContainer` の insert 系)に足す:

```java
turtle.setDisk(stack.copyWithCount(1));
turtle.bootStartup();  // 新設: computer の bootStartup と同一ロジック
```

`TurtleBlockEntity.bootStartup()` は C:→D:→legacy を解決し、
`TurtleManager.run` 相当で起動する必要がある(**computer と違い BE 内で直接
`vm.run` してはならない** — turtle の JS は runner thread でしか走れない、INV1)。
実装は `TurtleManager.startProgram(level, pos, viewer, source, "[startup]")` を呼ぶ形。
挿入操作したプレイヤーを viewer に出来る場合は渡す(挿入 UI から取れる)。
稼働中(busy)なら computer の hot-swap と同様、§5 の replace 規則に従う。

### 4.4 起動しないケース(明示)

- ブロック設置時: 自動起動**しない**(computer と同じ。startup は disk 挿入 or reload
  復元のみがトリガー)。
- source 再実行が即例外で死んだ場合: `isResident()`=false → finish が residentSource を
  空に flush → **restart ループは構造的に起きない**。エラーは transcript に残る。

---

## 5. stop / replace(#79)

### 5.1 busy の定義を registry に一致させる

`JsComputer` に `isBusy()` を追加(registry に ACTION_RUNNER handle が居るか。
`LivenessRegistry.snapshot(ACTION_RUNNER)` ベースの O(1) カウンタでよい)。

| VM の状態 | isBusy | isResident | 新規 run の扱い |
|---|---|---|---|
| foreground 実行中 / callback 実行中(turtle.* ブロック中含む) | true | 任意 | **拒否**(busy — wait) |
| resident-idle(daemon が待機中) | false | true | **replace**(§5.2) |
| 完全 idle | false | false | 起動 |

現行 run の「ACTIVE に同 pos が居たら一律拒否」を上表に置換する。
併せて run のガードに **level 比較を追加**(現行 L43 は pos のみ比較 — 異 dimension の
同座標で誤 busy になる既存バグの drive-by 修正)。

### 5.2 replace は「畳んでから 1 tick 遅延起動」

fold は非同期(runner thread の脱出 → 次 tick の finish() → BE writeback)なので、
新 run を同 tick で始めるとインベントリを stale な BE から複製してしまう。よって:

```java
// TurtleManager
private static final List<PendingStart> PENDING = new ArrayList<>();

run(...):
  Running existing = findAt(level, pos);
  if (existing != null) {
    if (existing.vm.isBusy()) { send "busy — wait"; return; }
    existing.vm.stopAllHandles();   // resident-idle daemon を畳む
    existing.brain.abort();
    PENDING.add(new PendingStart(level, pos, player, command));
    return;
  }
  start(...);  // 通常起動

tickAll() 末尾:
  for (PendingStart p : drain(PENDING)) {
    if (findAt(p.level, p.pos) == null) start(p...);   // fold 完了後に起動
    else PENDING.add(p);                                // まだ finish 前 → 次 tick 再試行
  }
```

- 1 tick の遅延は turtle の 14-tick pacing に対して知覚不能。
- fold された daemon の finish は §3.3 の規則で `residentSource=""` を書く
  (abort 経由なので keepResidentOnFinish=false)→ 新 program に古いレシピが
  遺伝しない(#79 の「stale callback を継がない」要件)。
- stale listener: `stopAllHandles` → `ListenerHandle.dispose` → NIC compare-and-clear
  (#82 実装済み)で消える。frame が brain キューに滞留していても、job 実行時の
  identity 一本化(#82)で no-op。**新規経路の追加漏れ無し**。
- 再試行が残り続ける病理(finish が来ない)は abort 済みなので高々 1-2 tick。保険と
  して PendingStart に TTL(例: 100 tick)を付け、超過時は viewer に
  `"[replace timed out]"` を通知して破棄。

### 5.3 unload / server 停止での fold(§4 の復元と対)

daemon 稼働中に chunk が unload されると、現状は Running が unload 済み chunk に
touch し続ける(暗黙の chunk 保持 or 不定動作)。**「unload = daemon を一時停止、
次回 load で復元」を正式仕様にする**(CC の伝統とも一致):

```java
// TurtleManager.init() に追加
ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> foldInChunk(level, chunk.getPos()));
ServerLifecycleEvents.SERVER_STOPPING.register(server -> foldAll());

private static void fold(Running r) {
  r.keepResidentOnFinish = r.vm.isResident();  // レシピは保存する
  r.vm.stopAllHandles();
  r.brain.abort();
  finish(r);                 // 即時 writeback(unload 前に BE がまだ触れるうちに)
  ACTIVE.remove(r);
}
```

- fold は tickAll を待たず**同期で** finish する(CHUNK_UNLOAD 時点なら BE はまだ取得
  可能)。runner thread は abort フラグ + interrupt で自走脱出(結果は捨てる。
  world 状態は毎 tick の syncLiveState + block state で既に永続側が真)。
- 次回 chunk load → `loadAdditional` が residentSource を読み pendingRestart → §4.1。
- **foreground 実行中の unload** も同じ fold に落とす。foreground は
  `keepResidentOnFinish = vm.isResident()` が通常 false(まだ handle を張る前に中断)
  なので「途中で止まった一回きりの program は復元しない」— これも仕様として文書化
  (中断点からの再開は JS 直列化が必要で不可能。リプレイは危険: 破壊・設置の重複)。

### 5.4 callback の tick 予算(#81 §7-4 の宿題)

`MineSIerConfig.maxTurtleCallbackTicks`(新設。デフォルト = `maxTurtleProgramTicks` と
同値でよい)を追加し、brain に「現在 job の経過 tick」を持たせる:

- `beginForegroundJob`/`endForegroundJob` の brain 側ラッパで job 開始 tick を記録。
- `tickAll` で `vm.isBusy() && foregroundDone && jobTicks > 上限` → `stop()` 相当
  (daemon ごと abort。transcript に `"[callback exceeded tick budget — stopped]"`)。
- foreground の既存予算(L175-179)は不変。

---

## 6. ターミナル UI(#78)

### 6.1 走行中でもターミナルを開けるようにする

`handleOpenTerminal` の `TurtleManager.isRunning` early-return(L182)を撤去し:

```java
Running r = TurtleManager.findAt(level, pos);
if (r != null) {
  r.viewer = player;                       // 最後に開いた人が出力購読者(単一 viewer)
  send TerminalScreenS2C(pos, TurtleManager.liveTranscript(r), true, true);
  send ProcessStateS2C(true, processName(r.source));
} else {
  send TerminalScreenS2C(pos, turtle.getTranscript(), true, true);
  send ProcessStateS2C(false, "");
}
sendProgramList(player, turtle);
```

- `liveTranscript(r)` = `String.join("\n", r.baseTranscript)`(BE の transcript は
  稼働中 stale — syncLiveState は意図的に transcript を flush しない)。
- viewer 差し替えは「単一 viewer、last-opener wins」。マルチ viewer ブロードキャスト
  は非目標(§9)。旧 viewer の画面はそれ以降更新されないだけで壊れない。
- `sendProgramList` は BE(ProgramStore)から従来通り取れる。

### 6.2 ProcessState の push 点

computer と同じタイミング対称にする:

| イベント | 送信 |
|---|---|
| turtle run 受理(`handleRun`) | `ProcessStateS2C(true, firstLine(command))` — **foreground も running 扱い**(turtle は長時間走るので Stop を有効化する必要がある。computer は同期実行なので UI が見ないだけで、意味論は同じ) |
| finish()(正常/Stop/fold) | viewer へ `ProcessStateS2C(vm.isResident(), name)`。daemon が残れば true + 名前、完全終了なら false |
| ターミナル open | §6.1 の通り実状態 |
| Stop 受理(`handleStop`) | 既存の `(false,"")` 先行送信を維持(楽観更新)。finish 側の push と重複しても最終状態は一致 |

client 側(`ComputerScreen.setProcessState`)は**変更不要** — 既に
running/processName/Run/Stop ボタン活性を汎用処理している(L186-191, L826)。

### 6.3 インベントリ/装備 UI は稼働中ブロックを維持

`handleOpenInventory`/`handleOpenEquipment` の isRunning ガードは**意図的に残す**。
稼働中の在庫の真実は Running のコピーにあり、BE ベースの menu を開くと dup 経路に
なる。「稼働中はターミナル(観測+Stop)だけ開ける」を仕様として文書化。

---

## 7. リモート制御プレーン(#66)

### 7.1 設計原則(issue の分業をそのまま採る)

- **routing / 認証 / プロトコルは player code の仕事**(フレームは今まで通りただの
  データ。crypto API で HMAC 検証するのは player の裁量 — security/CTF ピラーの教材
  になる)。
- **受理後の効果は engine の仕事**。player script が「これは正当な制御要求だ」と
  判断したら、**自分自身に対して** engine API を呼ぶ。他 turtle を直接操作する API は
  作らない(それはネットワーク越しにフレームで頼む — 現実の SSH/管理プレーンと同型)。

### 7.2 JS API: `proc` グローバル(新設・computer/turtle 共通)

> **実装時の確定(設計からの逸脱)**: 当初案の `pause`(timer 減算凍結 + frame 保留
> キュー退避)は #66 の "minimal control surface / no heavy permission logic" という
> 明示スコープに対して重すぎる。issue の候補コマンドは stop/claim/resume であり、
> claim は「coordinator に制御されている印を付ける」、resume は「claim を解除する」
> という**助言的メタデータ**が本旨。よって **timer 凍結つき pause は採らず**、claim/
> resume を advisory owner ラベルとして実装した(バッファリングの複雑性・frame ロスの
> リスクを回避)。実際に配送を止めたい player は `proc.status().claimed` を見て自分の
> callback 側で早期 return すればよい(policy は player code、が #66 の原則)。

```js
proc.stop()             // 常駐 handle 全クリアを要求(deferred)。residentSource も消える
proc.claim("owner")     // advisory owner ラベルを設定(engine は強制しない)
proc.resume()           // claim を解除
proc.status()           // { resident: bool, busy: bool, claimed: bool, claimedBy: string|null }
```

- `stop` の実体は callback 内から呼ばれるので「即時破壊」ではなく **deferred**:
  `stopRequested` フラグを立て、callback return 後に owner 側で `stopAllHandles()` +
  (turtle) `brain.abort()` を実行(Rhino scope 実行中の自壊を避ける)。turtle は
  `tickAll` が `vm.consumeStopRequest()` を検知して `stop(level,pos)` 相当に合流、
  computer は `ComputerBlockEntity.serverTick` が検知して `stopResident()`。
- `claimedBy` は `JsComputer` の volatile 文字列。`claim` で設定、`resume`/空文字で解除。
  `status()` が resident/busy(registry 述語)と併せて JS オブジェクトで返す。
- 権限モデルは足さない(issue の non-goal)。「自分の VM の中で呼べる」以上の制約なし。

### 7.3 実装の置き場所

- `JsComputer` に `proc` global を生やす(`print` 等と同列、~80 行)。
- turtle 側は `stopRequested` を `TurtleManager.tickAll` が検知して既存 `stop(level,pos)`
  に合流。computer 側は `ComputerBlockEntity.serverTick` が検知して `stopResident()`。
- **#66 はここまで**。`claim` の所有権強制や到達不能 turtle の強制停止は非目標のまま。

---

## 8. テスト計画(#76)

既存資産: `JsComputerResidentTest` / `LivenessRegistryTest` / `TurtleNetworkStateTest`
(Rhino 前提の unit)。同じ流儀で拡張する。runner thread の実機再現はしない
(#81 テスト方針を踏襲: JsComputer の新 API を直接叩く)。

### Unit(必須)

1. **residentSource 確定規則**(§3.3): 正常終了+resident → source /
   Stop → "" / keepResidentOnFinish → source。TurtleManager を介さず finish 規則を
   関数化してテスト。
2. **isBusy 述語**: begin/endForegroundJob で立つ/降りる、resident-idle で false。
3. **replace 遷移**: resident-idle への run 要求 → stopAllHandles + abort + PENDING、
   busy への要求 → 拒否。PENDING TTL 破棄。
4. **pendingRestart 導出**: NBT round-trip で residentSource → pendingRestart、
   空なら false。update tag に KEY_RESIDENT が**含まれない**こと。
5. **proc API**: stop の deferred 実行(callback 内 stop → return 後に handle 全滅)、
   pause 中 collectDueTimers が減算しない、resume 後に発火再開、status の各フィールド。
6. **callback tick 予算**: jobTicks 超過で abort 判定が立つ(brain を fake 化)。

### GameTest / 手動(文書化して #76 に残す)

- reload 復元 E2E: daemon 起動 → セーブ&リロード → `[resident program restarted]` が
  出て timer が再発火する。
- chunk unload fold → 再 load 復元(遠方移動 or force-unload)。
- 走行中ターミナル open → 実 transcript + running 表示 + Stop が効く。
- disk 挿入 startup(turtle)。
- replace: daemon 稼働中に新コマンド → 旧 listener が発火しなくなる。

---

## 9. 非目標(明示)

- foreground program の**中断点からの再開**(JS 継続の直列化は不可能。リプレイは
  world 破壊の重複リスクがあるため採らない)。
- マルチ viewer ターミナル同報 / 稼働中インベントリ UI。
- `proc` の権限・所有権強制(#66 non-goal)、他デバイスを直接叩く制御 API。
- computer 側 foreground の worker thread 化(#90 — 別トラック。ただし §7 の
  `proc` は computer にもそのまま載る設計にしてある)。
- #74(移動を跨ぐネットワーク回帰テスト)は隣接するが別 issue のまま。

---

## 10. 実装順序と issue 対応

| Phase | 内容 | 主ファイル | closes |
|---|---|---|---|
| 0 | 本書レビュー → 決定事項を #67/#80 に転記 | — | #80 |
| 1 | Running.source/viewer/keep フラグ、residentSource NBT+flush+update-tag strip、finish 確定規則、isBusy | TurtleManager / TurtleBlockEntity / JsComputer / LivenessRegistry | #75 |
| 2 | replace(PENDING)、level 比較修正、callback tick 予算、unload/server-stop fold | TurtleManager / TurtleBrain / MineSIerConfig | #79 |
| 3 | ticker + restartResident + bootStartup 移植 | TurtleBlock / TurtleBlockEntity / TurtleManager / TurtleDiskContainer | #77 |
| 4 | ターミナル open 撤廃ガード、ProcessState push 点、liveTranscript | MineSIerNet / TurtleManager | #78 |
| 5 | `proc` グローバル(stop/pause/resume/status、deferred stop 合流) | JsComputer / TurtleManager / ComputerBlockEntity | #66 |
| 6 | unit テスト一式 + GameTest 手順文書化 | src/test | #76 |

Phase 1→2→3 は依存が一直線(finish 規則が無いと fold が書けず、fold が無いと復元の
半分が空振る)。Phase 4/5 は 3 の後なら並行可。各 Phase を 1 PR とし、Phase 2 まで
入れば「セッション内の意味論完成」、Phase 3 で roadmap の resident v2(turtle 側)、
Phase 5 で #66 が閉じる。

### CLAUDE.md との整合

roadmap「resident v2: persistence — auto-restart on world reload / chunk load + startup
program」の turtle 半分が本書 Phase 3、computer 半分は実装済み(v1 + bootStartup)。
本書完了時に CLAUDE.md の該当節を更新すること。
