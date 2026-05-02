# SAM Agent Metrics Report

Status: success
Mode: svace
Project: batmon
Language: kotlin
SAM Score: 4.77 / 5

## Project Metrics

- Physical LOC: 4376
- Code LOC: 3841
- File count: 41
- Class count: 118
- Method count: 430

## Metric Scores

- CC: 5.0 (good, scoring)
- DC: 4.96 (good, scoring)
- MCD: 5.0 (good, scoring)
- CBO: 4.46 (watch, scoring)
- LOC: 4.5 (watch, scoring)
- DEP: N/A (not_applicable, scoring)
- GM: N/A (not_applicable, scoring)
- UC: 0.0 (poor, non-scoring)
- NCS: N/A (not_applicable, non-scoring)

## Top Findings

1. [medium] CBO: High coupling detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 74
2. [medium] LOC: Oversized class or file detected
   - File: app/src/main/kotlin/com/android/synclab/glimpse/presentation/MainActivity.kt
   - Symbol: com.android.synclab.glimpse.presentation.MainActivity
   - Value: 722
3. [low] DC: Duplicated code block detected
4. [info] UC: Unused code candidates reported

## Outputs

- Agent JSON: /tmp/sam-agent-runs/batmon-20260502-153437/agent-report.json
- Developer HTML: /tmp/sam-agent-runs/batmon-20260502-153437/dev-report.html
- SAM HTML index: /tmp/sam-agent-runs/batmon-20260502-153437/attempts/svace/sam-result/html/SAM_Report_batmon_20260502_index.html
- SAM report JSON: /tmp/sam-agent-runs/batmon-20260502-153437/attempts/svace/sam-result/report.json
- Score CSV: /tmp/sam-agent-runs/batmon-20260502-153437/attempts/svace/sam-result/SAM_total_score.csv
- Log: /tmp/sam-agent-runs/batmon-20260502-153437/attempts/svace/log/sam.log_20260502
