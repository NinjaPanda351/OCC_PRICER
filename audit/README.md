# Audit artifacts

Baseline: dcf68e64ef8a845f63ae4429360dc52b1cfb8c21, version 4.1.6.

- [Architecture and defect audit](ARCHITECTURE_AUDIT.md): 38 prioritized findings with pinned source references.
- [Repair plan](REPAIR_PLAN.md): target boundaries, staged implementation, dependencies, acceptance tests and rollout.
- [AuditProbes.java](AuditProbes.java): isolated reproductions of 29 baseline defects.
- [probe-results.txt](probe-results.txt): actual probe output.
- [build-results.txt](build-results.txt): production compilation command and result summary.

## Reproduce on Windows with JDK 25

Run from the repository root in PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out/audit-classes | Out-Null
$auditSources = Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName
javac -encoding UTF-8 --release 25 -Xlint:all -cp 'lib/*' -d out/audit-classes $auditSources
if ($LASTEXITCODE -ne 0) { throw 'Production compilation failed' }
javac -encoding UTF-8 --release 25 -cp 'out/audit-classes;lib/*' -d out/audit-classes audit/AuditProbes.java
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
java -cp 'out/audit-classes;lib/*' AuditProbes
if ($LASTEXITCODE -ne 0) { throw 'A baseline defect probe did not reproduce' }
```

The harness creates a unique data directory under out/, launches a child JVM with an isolated APPDATA value and an in-memory PreferencesFactory, and exits explicitly to stop Swing helper threads. It does not send email, print, open a browser, call live APIs, or use real trade data.

These are **baseline defect reproductions**: CONFIRMED means the incorrect behavior was observed, not that the application passed a correctness test. After production fixes, replace these reflection-based probes with normal tests asserting corrected behavior.
