package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkListener;
import com.hodzilla51.minesier.net.SendResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Drives a real {@link JsComputer} VM with a {@link FakeNetwork} to exercise the registry-backed
 * resident lifecycle: listener-only daemons count as resident, replace/offReceive stay
 * identity-correct, and timers fire + unref through {@link JsComputer#drainDueTimers()}.
 */
class JsComputerResidentTest {

  @BeforeAll
  static void installFactory() {
    SafeContextFactory.install();
  }

  /**
   * A minimal NetworkApi: one listener slot per interface name, with identity compare-and-clear.
   */
  private static final class FakeNetwork implements NetworkApi {
    final Map<String, NetworkListener> listeners = new HashMap<>();
    final List<String> output = new ArrayList<>();
    int setCount;
    int clearCount;

    @Override
    public String address() {
      return "00:00:00:00:00:01";
    }

    @Override
    public String address(String interfaceName) {
      // Every named interface resolves, so net.nic(name) returns a handle.
      return "00:00:00:00:00:01/" + interfaceName;
    }

    @Override
    public SendResult send(String destination, String data) {
      return SendResult.DELIVERED;
    }

    @Override
    public SendResult send(String interfaceName, String destination, String data) {
      return SendResult.DELIVERED;
    }

    @Override
    public NetworkFrame receive() {
      return null;
    }

    @Override
    public NetworkFrame receive(String interfaceName) {
      return null;
    }

    @Override
    public SendResult forward(String interfaceName, NetworkFrame frame) {
      return SendResult.DELIVERED;
    }

    @Override
    public boolean setPromiscuous(String interfaceName, boolean enabled) {
      return true;
    }

    @Override
    public boolean setReceiveListener(String interfaceName, NetworkListener listener) {
      listeners.put(interfaceName, listener);
      setCount++;
      return true;
    }

    @Override
    public boolean clearReceiveListener(String interfaceName, NetworkListener expected) {
      if (listeners.get(interfaceName) != expected) {
        return false;
      }
      listeners.remove(interfaceName);
      clearCount++;
      return true;
    }

    @Override
    public void reportOutput(List<String> lines) {
      output.addAll(lines);
    }

    /** Simulates a frame arriving on the wire for the registered listener. */
    void deliver(String interfaceName, NetworkFrame frame) {
      NetworkListener listener = listeners.get(interfaceName);
      if (listener != null) {
        listener.onFrame(frame);
      }
    }
  }

  private static JsComputer withNetwork(FakeNetwork network) {
    JsComputer computer = new JsComputer();
    computer.setNetwork(network);
    return computer;
  }

  @Test
  void listenerOnlyDaemonIsResidentButNotTickDriven() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);

    computer.run("net.nic('north').onReceive(function(f){ print(f.data); });");

    assertTrue(computer.isResident(), "a listener-only daemon must read as resident");
    assertTrue(computer.isAlive());
    assertFalse(computer.hasTickDrivenWork(), "no timer means no tick-driven work");
    assertNotNull(network.listeners.get("north"), "the listener is installed on the NIC");
  }

  @Test
  void stopAllHandlesClearsTheListener() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);
    computer.run("net.nic('north').onReceive(function(f){});");
    assertTrue(computer.isResident());

    computer.stopAllHandles();

    assertFalse(computer.isResident());
    assertFalse(computer.isAlive());
    assertNull(network.listeners.get("north"), "the NIC slot must be cleared");
  }

  @Test
  void deliveredFrameRunsTheCallback() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);
    computer.run("net.nic('north').onReceive(function(f){ print('got ' + f.data); });");

    network.deliver("north", new NetworkFrame("peer", "me", "hello"));

    assertTrue(network.output.contains("got hello"), "callback output: " + network.output);
  }

  @Test
  void replaceOnSameInterfaceDisposesOldListener() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);

    computer.run("net.nic('north').onReceive(function(f){ print('a'); });");
    NetworkListener first = network.listeners.get("north");
    computer.run("net.nic('north').onReceive(function(f){ print('b'); });");
    NetworkListener second = network.listeners.get("north");

    assertSame(second, network.listeners.get("north"));
    assertFalse(first == second, "a fresh lambda is installed on replace");
    // First listener was compare-and-cleared during the replace; still exactly one live listener.
    assertTrue(network.clearCount >= 1);
    assertTrue(computer.isResident());
  }

  @Test
  void offReceiveDisposesHandleSoResidentClears() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);
    computer.run("net.nic('north').onReceive(function(f){});");
    assertTrue(computer.isResident());

    computer.run("net.nic('north').offReceive();");

    assertFalse(computer.isResident(), "explicit offReceive must drop residency");
    assertNull(network.listeners.get("north"));
  }

  @Test
  void repeatingTimerFiresEveryInterval() {
    JsComputer computer = new JsComputer();
    computer.run("every(2, function(){ print('tick'); });");

    assertTrue(computer.isResident());
    assertTrue(computer.hasTickDrivenWork());

    assertTrue(computer.drainDueTimers().isEmpty(), "interval 2: not due on tick 1");
    assertEquals(List.of("tick"), computer.drainDueTimers(), "due on tick 2");
    assertTrue(computer.drainDueTimers().isEmpty(), "re-armed: not due again immediately");
    assertEquals(List.of("tick"), computer.drainDueTimers(), "fires again at the next interval");
    assertTrue(computer.isResident(), "a repeating timer stays resident");
  }

  @Test
  void oneShotTimerUnrefsAfterFiring() {
    JsComputer computer = new JsComputer();
    computer.run("after(1, function(){ print('once'); });");
    assertTrue(computer.hasTickDrivenWork());

    assertEquals(List.of("once"), computer.drainDueTimers());

    assertFalse(computer.hasTickDrivenWork(), "one-shot is unref'd after firing");
    assertFalse(computer.isResident());
  }

  @Test
  void callbackRegisteringNewTimerNeverDropsToIdle() {
    JsComputer computer = new JsComputer();
    // A one-shot that arms a repeating timer: after the drain, the VM must still be resident.
    computer.run("after(1, function(){ every(5, function(){}); });");

    computer.drainDueTimers();

    assertTrue(computer.isResident(), "the timer armed inside the callback keeps it alive");
    assertTrue(computer.hasTickDrivenWork());
  }

  @Test
  void collectDueTimersIsServerSafeAndPendingPreventsDoubleCollect() {
    JsComputer computer = new JsComputer();
    computer.run("every(1, function(){ print('tick'); });");

    // First collect (no JS): the timer falls due and is marked pending.
    List<Object> due = computer.collectDueTimers();
    assertEquals(1, due.size());
    // A second collect before firing must NOT re-collect the pending timer.
    assertTrue(computer.collectDueTimers().isEmpty(), "pending timer is not collected twice");

    // Firing it (JS) re-arms and clears pending.
    assertEquals(List.of("tick"), computer.fireTimer(due.get(0)));
    assertTrue(computer.isResident(), "repeating timer stays resident after firing");

    // Next interval: collectable again.
    assertEquals(1, computer.collectDueTimers().size());
  }

  @Test
  void executorRoutesReceiveCallbackOffThread() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);
    List<Runnable> posted = new ArrayList<>();
    computer.setExecutor(posted::add); // capture jobs instead of running them inline

    computer.run("net.nic('north').onReceive(function(f){ print('got ' + f.data); });");
    network.deliver("north", new NetworkFrame("peer", "me", "hi"));

    // The callback did not run inline — it was posted to the executor.
    assertEquals(1, posted.size());
    assertTrue(network.output.isEmpty(), "callback must not run until the executor runs the job");

    posted.get(0).run(); // simulate the owning thread draining its queue
    assertTrue(
        network.output.contains("got hi"), "callback runs when the job runs: " + network.output);
  }

  @Test
  void actionRunnerMakesAliveButNotResident() {
    JsComputer computer = new JsComputer();
    assertFalse(computer.isAlive());

    computer.beginForegroundJob();
    assertTrue(computer.isAlive(), "an executing job makes the VM alive/busy");
    assertFalse(computer.isResident(), "a busy runner is not resident");

    // Nested begin/end: the handle is held until the outermost job returns.
    computer.beginForegroundJob();
    computer.endForegroundJob();
    assertTrue(computer.isAlive(), "still busy: inner job ended, outer still running");

    computer.endForegroundJob();
    assertFalse(computer.isAlive(), "no job running and no resident handle");
  }

  @Test
  void clearTimersLeavesListenerResident() {
    FakeNetwork network = new FakeNetwork();
    JsComputer computer = withNetwork(network);

    // Both a timer and a listener live in the same VM (run() does not auto-stop handles — that is
    // the block entity's job — so they coexist here).
    computer.run("every(3, function(){}); net.nic('south').onReceive(function(f){});");
    assertTrue(computer.isResident());
    assertTrue(computer.hasTickDrivenWork());

    // clearTimers() removes only TIMER handles; the listener daemon stays resident.
    computer.run("clearTimers();");

    assertFalse(computer.hasTickDrivenWork(), "timers cleared");
    assertTrue(computer.isResident(), "the listener still keeps the VM resident");
    assertNotNull(network.listeners.get("south"));
  }
}
