# Contributing

## Updating or Adding Fixtures

1. Place new or updated static Delta Lake fixtures under `src/main/resources/golden/`.
   - When syncing golden tables from [`delta-io/delta`](https://github.com/delta-io/delta/tree/master/connectors/golden-tables/src/main/resources/golden), keep fixture files unmodified so they remain byte-for-byte identical to upstream, and update the upstream commit hash in `NOTICE` and per-table `"notice"` entries if applicable.
   - Native fixtures created directly for this repository do not require a `"notice"` field in `index.json` (it is optional per table).
2. Regenerate the fixture index (existing per-table `"notice"` values are preserved automatically; pass `--default-notice "..."` only when importing new third-party tables that require attribution):
   ```bash
   python3 dev/generate_index.py
   ```
3. Run the full verification suite (Java unit tests, isolated JAR byte-for-byte extraction check, and Python index validation):
   ```bash
   ./gradlew check
   ```
