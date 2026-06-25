# System Integration Design Principles

> **Status: design direction.** This document describes the intended
> system-integrator experience. It is not a promise that every described
> facility exists in the current mod release.

MineSIer aims to let players experience, within Minecraft, work that is as
close as practical to real system integration. The point is not merely to
write code or build a network. It is to understand a customer's situation,
make a system work within real constraints, and keep it valuable in operation.

## Responsibility boundary

MineSIer provides the world in which system-integration work can take place:

- customers, users, existing equipment, and business situations;
- the working substrate that an SIer normally takes for granted, such as
  workstations, a simple operating system, editing and execution tools,
  persistence, and physical/network primitives;
- ways to observe and operate systems, including logs, status, and controlled
  reproduction of relevant conditions; and
- requests, acceptance criteria, and the consequences of live operation.

Players are responsible for the solution:

- understanding the problem and constraints;
- choosing an architecture and trade-offs;
- designing, implementing, integrating, deploying, and operating services;
- deciding what to test and how to manage risk; and
- diagnosing, mitigating, and correcting failures after release.

MineSIer should not make players reimplement commodity foundations merely to
begin meaningful work. Conversely, it should not provide a finished business
application when creating that application is the subject of a request.

## Requests specify outcomes, not a path

An ordinary business request specifies observable facts and outcomes:

- the user's problem and the current state of the world;
- constraints such as available machines, cost, time, data, or connectivity;
- required externally observable behaviour; and
- any meaningful non-functional requirements, such as access restrictions,
  persistence, response time, recovery, or auditability.

It does not prescribe a programming language construct, network protocol,
storage implementation, screen layout, or internal architecture unless that is
itself part of the required external contract.

World-facing interfaces are contracts. If a villager must place an order, read
a message, or receive a delivery result, the request may define those user
actions and outcomes. A player remains free to decide how the system fulfils
them.

Some requests deliberately have a product or infrastructure as their subject:
for example, an email service, an authentication service, or a router. In that
case the product's user-visible behaviour is part of the request, while its
internal implementation remains the player's decision.

## Operation is the ultimate evaluation

The primary question is whether a system continues to provide value in actual
operation, not whether it matches a prescribed implementation or has a
particular test suite. Testing is a player-selected risk-management practice,
not a coverage minigame or a complete guarantee against future incidents.

Basic acceptance may establish that a delivery is usable. Later operation may
expose problems through normal usage, growth, concurrent actions, restarts,
connectivity changes, changed requirements, or other modeled conditions.

Operational incidents must be causal rather than arbitrary:

- each incident follows from world state, user behaviour, or a modeled failure
  condition;
- players can investigate it through available evidence;
- its impact can be contained or recovered from; and
- a change to the system or its operation can prevent or reduce recurrence.

The goal is to create responsibility for the system after delivery, rather than
to punish players with unknowable random failures.

## Platform and solution examples

| MineSIer platform | Player solution or request subject |
| --- | --- |
| Workstation OS, editor, terminal, runtime, files, logs | Business applications and their data models |
| Cable, NICs, Layer 2 frames, and optional convenience devices | Addressing, routing, discovery, transport, and application protocols |
| Villagers and their observable interactions | Order processing, inventory systems, notifications, and customer-facing UI |
| World state, lifecycle events, and operational evidence | Deployment, monitoring, backup, recovery, and incident response |

The networking specification follows the same boundary: MineSIer supplies the
physical medium and Layer 2 primitives, while players build the higher-level
behaviour needed by their systems.

## Design test for future features

When adding a feature, ask:

1. Does it make a realistic SIer activity possible, observable, or operable?
2. Is it a prerequisite working environment that should be provided by the
   platform, or a meaningful design decision that should remain with players?
3. Does it define an observable contract without prescribing an unnecessary
   implementation path?
4. If it introduces failure, can that failure be understood and addressed from
   evidence available in the world?

Features that satisfy these questions strengthen the system-integrator
experience; features that remove the relevant design or operational judgement
do not.
