package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.QaReport.Check;
import com.storyforge.orchestrator.model.QaReport.Severity;
import com.storyforge.orchestrator.qa.QaHtmlRenderer;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageBlockedException;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QaStage implements StageWorker {

    private final QaService qa;

    public QaStage(QaService qa) {
        this.qa = qa;
    }

    @Override
    public Stage stage() {
        return Stage.QA;
    }

    @Override
    public List<String> outputs() {
        return List.of("qa_report.json", "qa_report.html");
    }

    @Override
    public void execute(StageContext context) {
        QaReport report = qa.evaluate(context.run(), context.files());
        context.usage().provider("qa-gate");
        context.files().write(Stage.QA, "qa_report.json", report);
        context.files().writeText(Stage.QA, "qa_report.html",
                QaHtmlRenderer.render(context.run().topicTitle(), report));
        if (!report.passed()) {
            List<String> failed = report.checks().stream()
                    .filter(check -> !check.passed() && check.severity() == Severity.CRITICAL)
                    .map(Check::id).toList();
            throw new StageBlockedException("QA failed: " + String.join(", ", failed));
        }
    }
}
