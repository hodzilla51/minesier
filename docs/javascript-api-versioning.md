# JavaScript API versioning

MineSIer exposes a small API version to every Computer and Turtle script:

```js
print(minesier.apiVersion);       // 1
print(minesier.apiVersionString); // "1"
```

## Current version

API version `1` covers the current public script globals:

- `print`
- `require`
- `fs`
- `net`
- `ip`
- `crypto`
- `redstone`
- `monitor`
- `turtle`
- resident execution helpers: `after`, `every`, `clearTimers`

Some globals are only present on blocks that support them. For example, `turtle`
is present on Turtles, while `redstone` and `monitor` are currently Computer
APIs.

## Compatibility policy

MineSIer is still experimental, but disk files can outlive code changes. Within a
major API version:

- Existing function names should keep their basic meaning.
- Return shapes should not be changed incompatibly without a migration path.
- New optional fields, functions, modules, or globals may be added.
- Bug fixes may make invalid inputs fail more consistently.
- Deprecated APIs should remain for at least one subsequent minor development
  cycle when practical.

Increment `minesier.apiVersion` when a saved disk program may need code changes
to keep working.

## Deprecation practice

When replacing a public API:

1. Add the replacement first.
2. Document the old and new forms.
3. Keep the old form as a compatibility alias when it is low risk.
4. Remove the old form only with an API version bump or before a public stable
   release boundary.
