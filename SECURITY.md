# Security Policy

MineSIer executes untrusted JavaScript inside a Rhino sandbox and interacts with
Minecraft world state. Sandbox escapes, unintended host access, denial of
service, and multiplayer authorization problems are security issues.

## Reporting a vulnerability

Do not publish a proof of concept or exploit in a public issue. Use GitHub's
private vulnerability reporting for this repository. If it is unavailable,
contact the repository owner through their GitHub profile and include the word
"security" in the message title.

Please include:

- affected MineSIer version or commit
- a minimal reproduction
- impact and attack preconditions
- suggested mitigation, if known

## Supported versions

Only the latest commit on `main` is supported while the mod remains
experimental.
