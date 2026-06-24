# Releasing MineSIer

## Before a release

1. Ensure `main` is green in GitHub Actions.
2. Run `./gradlew build` and `./gradlew spotlessCheck` locally.
3. Verify the built jar in a development client.
4. Review `README.md`, `docs/`, `THIRD-PARTY-NOTICES.md`, and `LICENSE`.
5. Update the version in `gradle.properties`.

## Publishing a release

1. Create and push an annotated version tag, for example `v1.1.0`.
2. Create a GitHub Release from that tag.
3. Attach the remapped mod jar from `build/libs/`; do not attach development
   jars unless explicitly labelled as such.
4. Summarize player-visible changes, compatibility requirements, and known
   limitations in the release notes.

## After a release

Verify the downloadable jar starts on the documented Minecraft, Fabric Loader,
and Java versions. If a bundled dependency changes, update
`THIRD-PARTY-NOTICES.md` before the next release.
