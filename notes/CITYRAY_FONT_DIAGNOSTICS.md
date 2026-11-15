# Cityray font path diagnostics — stage 1

This branch adds one **metadata-only** step to the existing AtlasDim Diagnostics report.

## Purpose
Identify whether the expected Kanzi HMI directory and `.kzb`, `.ttf`, or `.otf` files exist on the connected Cityray QNX system. The location and content of the actual Cityray font are **not yet confirmed**.

## What the new step does
- Runs a single `ls -ld` query against a fixed set of Kanzi HMI path patterns.
- Reports file names, permissions, sizes and error messages.
- Reuses the existing QNX diagnostic connection.
- Does **not** read file contents, extract font bytes, upload files, write to QNX, modify services or restart processes.
- Does **not** recursively scan the filesystem.

## Important limitations
- The existing Diagnostics feature already runs other read-only inspection commands; this change does not remove them.
- A `.kzb` listing does not establish that a particular font is embedded inside it.
- Shell glob behavior and the actual Cityray filesystem layout must be verified on a safe test system.
- Neither compilation nor in-vehicle behavior has been verified as part of this change.
- No new permissions or QNX authentication paths are added.

## Verification checklist
1. Compile `:app:assembleDebug` and run `:app:lintDebug` in a controlled build environment.
2. Confirm the report still correctly summarizes graphics.conf, Screen windows and cluster processes after the new step.
3. Check a mock/unavailable QNX connection: diagnostic should fail cleanly.
4. Review `ls` output on a non-vehicle test environment for missing glob matches and large output.
5. Only after review, use the diagnostic while the vehicle is stationary and its displays are functioning normally.
6. Inspect the report section **Cityray: пути ресурсов и шрифтов (только метаданные)**.

No font export is implemented in stage 1.
