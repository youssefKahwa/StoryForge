package com.storyforge.orchestrator.qa;

import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.QaReport.Check;
import org.springframework.web.util.HtmlUtils;

public final class QaHtmlRenderer {

    private QaHtmlRenderer() {
    }

    public static String render(String title, QaReport report) {
        StringBuilder rows = new StringBuilder();
        for (Check check : report.checks()) {
            rows.append("<tr class=\"").append(check.passed() ? "pass" : check.severity().name().toLowerCase())
                    .append("\"><td>").append(check.passed() ? "PASS" : "FAIL").append("</td><td>")
                    .append(check.severity()).append("</td><td>").append(escape(check.category()))
                    .append("</td><td>").append(escape(check.id())).append("</td><td>")
                    .append(escape(check.detail())).append("</td></tr>\n");
        }
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>QA report</title>
                <style>
                body{font-family:system-ui,sans-serif;margin:24px;color:#1d1d1f;background:#fafafa}
                table{border-collapse:collapse;width:100%%}td,th{padding:6px 10px;border-bottom:1px solid #ddd;text-align:left}
                tr.pass td:first-child{color:#2e7d32;font-weight:600}tr.critical td:first-child{color:#c62828;font-weight:600}
                tr.warning td:first-child{color:#ef6c00;font-weight:600}.verdict{font-size:20px;font-weight:700}
                </style></head><body>
                <h1>QA report: %s</h1>
                <p class="verdict">%s: %d critical failure(s), %d warning(s). Disclosure required: %s</p>
                <table><thead><tr><th>Result</th><th>Severity</th><th>Category</th><th>Check</th><th>Detail</th></tr></thead>
                <tbody>
                %s</tbody></table>
                <p>Generated %s</p>
                </body></html>
                """.formatted(escape(title), report.passed() ? "PASSED" : "BLOCKED", report.criticalFailures(),
                report.warnings(), report.disclosureRequired() ? "yes" : "no", rows, report.generatedAt());
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
