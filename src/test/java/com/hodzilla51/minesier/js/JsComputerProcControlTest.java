package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@code proc} control surface and the {@code isBusy}/{@code consumeStopRequest}
 * predicates that the Turtle and Computer owners use to drive resident lifecycle. Runner-thread
 * behavior is verified by driving these VM-level APIs directly (per the #67 test plan).
 */
class JsComputerProcControlTest {

  @BeforeAll
  static void installFactory() {
    SafeContextFactory.install();
  }

  @Test
  void procStopIsDeferredAndConsumedExactlyOnce() {
    JsComputer computer = new JsComputer();
    // proc.stop() from a program only requests the stop; it doesn't tear the VM down mid-run.
    List<String> out = computer.run("proc.stop(); print('still here');");
    assertEquals(List.of("still here"), out, "the program runs to completion after proc.stop()");

    assertTrue(computer.consumeStopRequest(), "the owner sees the pending stop");
    assertFalse(computer.consumeStopRequest(), "the request is cleared after being consumed");
  }

  @Test
  void claimAndResumeToggleTheAdvisoryOwner() {
    JsComputer computer = new JsComputer();
    List<String> out =
        computer.run(
            "proc.claim('coordinator');"
                + "var s = proc.status();"
                + "print(s.claimed + ' ' + s.claimedBy);"
                + "proc.resume();"
                + "print(proc.status().claimed);");
    assertEquals(List.of("true coordinator", "false"), out);
  }

  @Test
  void statusReportsResidencyFromLiveHandles() {
    JsComputer computer = new JsComputer();
    List<String> out =
        computer.run(
            "print(proc.status().resident); every(5, function(){}); print(proc.status().resident);");
    assertEquals(List.of("false", "true"), out, "arming a timer makes the VM resident");
  }

  @Test
  void isBusyTracksTheForegroundRunnerHandle() {
    JsComputer computer = new JsComputer();
    assertFalse(computer.isBusy(), "idle VM is not busy");

    computer.beginForegroundJob();
    assertTrue(computer.isBusy(), "an executing job marks the VM busy");
    assertFalse(computer.isResident(), "a busy runner alone is not resident");

    computer.endForegroundJob();
    assertFalse(computer.isBusy(), "the job ended, so the VM is idle again");
  }

  @Test
  void residentDaemonIsNotBusyBetweenCallbacks() {
    JsComputer computer = new JsComputer();
    computer.run("every(5, function(){});");
    // A daemon idling between callbacks is resident but not busy — a new run may fold it.
    assertTrue(computer.isResident());
    assertFalse(computer.isBusy());
  }
}
