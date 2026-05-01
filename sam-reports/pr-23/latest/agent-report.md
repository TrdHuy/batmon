# SAM Agent Metrics Report

Status: success
Mode: svace
Project: batmon
Language: kotlin
SAM Score: 4.69 / 5

## Project Metrics

- Physical LOC: 4101
- Code LOC: 3588
- File count: 39
- Class count: 113
- Method count: 386

## Metric Scores

- CC: 5.0 (good, scoring)
- DC: 4.96 (good, scoring)
- MCD: 5.0 (good, scoring)
- CBO: 4.22 (watch, scoring)
- LOC: 4.35 (watch, scoring)
- DEP: N/A (not_applicable, scoring)
- GM: N/A (not_applicable, scoring)
- UC: 0.0 (poor, non-scoring)
- NCS: N/A (not_applicable, non-scoring)

## Top Findings

1. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 87
2. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 800
3. [low] DC: Duplicated code block detected
4. [info] UC: Unused code candidates reported

## Outputs

- Agent JSON: /tmp/sam-agent-runs/batmon-20260501-111406/agent-report.json
- Developer HTML: /tmp/sam-agent-runs/batmon-20260501-111406/dev-report.html
- SAM HTML index: /tmp/sam-agent-runs/batmon-20260501-111406/attempts/svace/sam-result/html/SAM_Report_batmon_20260501_index.html
- SAM report JSON: /tmp/sam-agent-runs/batmon-20260501-111406/attempts/svace/sam-result/report.json
- Score CSV: /tmp/sam-agent-runs/batmon-20260501-111406/attempts/svace/sam-result/SAM_total_score.csv
- Log: /tmp/sam-agent-runs/batmon-20260501-111406/attempts/svace/log/sam.log_20260501
