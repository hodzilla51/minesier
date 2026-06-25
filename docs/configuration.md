# Configuration

MineSIer writes `config/minesier.properties` on startup. The file contains
operator-tunable safety and resource limits for MineSIer's own programmable
surface.

These limits bound MineSIer script execution, network callback pressure, frame
sizes, inboxes, transcripts, and Turtle runtime behavior. They are not intended
to replace general Minecraft server anti-lag or moderation tools.

## Limits

| Key | Default | Purpose |
| --- | ---: | --- |
| `instructionObserveEvery` | `100000` | Rhino instruction observer interval. |
| `maxScriptInstructions` | `200000000` | Top-level script instruction budget. |
| `maxCallbackInstructions` | `100000` | Timer/network callback instruction budget. |
| `maxNetworkQueuedEvents` | `1024` | Max queued MineSIer network callback/data-plane events. |
| `maxNetworkEventsPerTick` | `4` | Max queued events dispatched each server tick. |
| `maxInboxFrames` | `64` | Max queued frames per NIC inbox. |
| `maxFrameBytes` | `4096` | Max UTF-8 payload size for a frame. |
| `maxTranscriptLines` | `200` | Max saved terminal transcript lines. |
| `maxTurtleProgramTicks` | `12000` | Max runtime for one Turtle program. |
| `maxTurtleWaitTicks` | `1200` | Max duration accepted by one `turtle.wait()` call. |

Invalid or out-of-range values fall back to safe defaults or are clamped.
