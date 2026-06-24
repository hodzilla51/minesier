# Contributing to MineSIer

Thanks for contributing. MineSIer is an experimental Fabric mod for learning
programming and networking inside Minecraft. The working language for issues,
pull requests, code, and documentation is English.

## Development setup

- JDK 25
- A supported Minecraft 26.2 development environment

Useful commands:

```sh
./gradlew build
./gradlew runClient
./gradlew spotlessCheck
./gradlew spotlessApply
```

Run `spotlessApply` before committing Java changes. The CI build runs
`./gradlew build`, which includes formatting checks.

## Contribution scope

Before starting a large feature, open or comment on an issue so the design can
be discussed. Keep pull requests focused: avoid unrelated refactors, generated
files, and drive-by formatting changes.

MineSIer deliberately keeps the mod at Layer 2 where possible. Higher-level
network behavior such as routing, NAT, VPNs, and transport protocols should
remain programmable unless there is a clear beginner-facing reason to provide
a managed block.

All participants must follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Pull requests

- Explain the user-visible behavior and design trade-offs.
- Include test evidence. Use a development client when block placement, GUI, or
  world behavior is involved.
- Add or update documentation and examples for public JavaScript APIs.
- Do not expose Java classes, files, reflection, or host networking to the
  Rhino sandbox.

## Reporting bugs and proposing features

Use the GitHub issue forms. Security-sensitive reports should follow
[SECURITY.md](SECURITY.md) instead of a public issue.
