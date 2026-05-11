# SAM Agent Metrics Report

Status: success
Mode: svace
Project: batmon
Language: kotlin
SAM Score: 4.45 / 5

## Project Metrics

- Physical LOC: 6374
- Code LOC: 5657
- File count: 52
- Class count: 174
- Method count: 723

## Metric Scores

- CC: 5.0 (good, scoring)
- DC: 4.93 (good, scoring)
- MCD: 5.0 (good, scoring)
- CBO: 4.02 (watch, scoring)
- LOC: 3.44 (needs_attention, scoring)
- DEP: N/A (not_applicable, scoring)
- GM: N/A (not_applicable, scoring)
- UC: 0.0 (poor, non-scoring)
- NCS: N/A (not_applicable, non-scoring)

## Top Findings

1. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 99
2. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/infra/repository/GamepadRepositoryImpl.kt
   - Symbol: com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl
   - Value: 65
3. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 1411
4. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/infra/repository/GamepadRepositoryImpl.kt
   - Symbol: com.android.synclab.glimpse.infra.repository.GamepadRepositoryImpl
   - Value: 630
5. [low] DC: Duplicated code block detected
6. [low] DC: Duplicated code block detected
7. [low] DC: Duplicated code block detected
8. [info] UC: Unused code candidates reported

## Outputs

- Agent JSON: /tmp/sam-agent-runs/batmon-20260511-220142/agent-report.json
- Developer HTML: /tmp/sam-agent-runs/batmon-20260511-220142/dev-report.html
- SAM HTML index: /tmp/sam-agent-runs/batmon-20260511-220142/attempts/svace/sam-result/html/SAM_Report_batmon_20260511_index.html
- SAM report JSON: /tmp/sam-agent-runs/batmon-20260511-220142/attempts/svace/sam-result/report.json
- Score CSV: /tmp/sam-agent-runs/batmon-20260511-220142/attempts/svace/sam-result/SAM_total_score.csv
- Log: /tmp/sam-agent-runs/batmon-20260511-220142/attempts/svace/log/sam.log_20260511
