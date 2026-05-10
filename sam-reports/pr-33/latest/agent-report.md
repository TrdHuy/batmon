# SAM Agent Metrics Report

Status: success
Mode: svace
Project: batmon
Language: kotlin
SAM Score: 4.64 / 5

## Project Metrics

- Physical LOC: 5236
- Code LOC: 4599
- File count: 44
- Class count: 129
- Method count: 490

## Metric Scores

- CC: 5.0 (good, scoring)
- DC: 4.94 (good, scoring)
- MCD: 5.0 (good, scoring)
- CBO: 4.26 (watch, scoring)
- LOC: 4.08 (watch, scoring)
- DEP: N/A (not_applicable, scoring)
- GM: N/A (not_applicable, scoring)
- UC: 0.0 (poor, non-scoring)
- NCS: N/A (not_applicable, non-scoring)

## Top Findings

1. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 85
2. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/infra/repository/GamepadRepositoryImpl.kt
   - Symbol: com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl
   - Value: 62
3. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 953
4. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/infra/repository/GamepadRepositoryImpl.kt
   - Symbol: com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl
   - Value: 595
5. [low] DC: Duplicated code block detected
6. [low] DC: Duplicated code block detected
7. [info] UC: Unused code candidates reported

## Outputs

- Agent JSON: /tmp/sam-agent-runs/batmon-20260510-213244/agent-report.json
- Developer HTML: /tmp/sam-agent-runs/batmon-20260510-213244/dev-report.html
- SAM HTML index: /tmp/sam-agent-runs/batmon-20260510-213244/attempts/svace/sam-result/html/SAM_Report_batmon_20260510_index.html
- SAM report JSON: /tmp/sam-agent-runs/batmon-20260510-213244/attempts/svace/sam-result/report.json
- Score CSV: /tmp/sam-agent-runs/batmon-20260510-213244/attempts/svace/sam-result/SAM_total_score.csv
- Log: /tmp/sam-agent-runs/batmon-20260510-213244/attempts/svace/log/sam.log_20260510
