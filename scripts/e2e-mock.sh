#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/../orchestrator"
./mvnw -B -q test -Dtest=MockPipelineE2ETest
echo "Video:     $(pwd)/target/e2e-data/runs/1/assemble/final_16x9.mp4"
echo "QA report: $(pwd)/target/e2e-data/runs/1/qa/qa_report.html"
