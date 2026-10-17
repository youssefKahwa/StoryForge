package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Dossier.Claim;
import com.storyforge.orchestrator.model.Dossier.Source;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageBlockedException;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ResearchStage implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(ResearchStage.class);

    record SourceFix(List<Source> sources, List<ClaimFix> claims) {
    }

    record ClaimFix(String id, List<String> sourceIds) {
    }

    private final LlmRouter llm;
    private final JsonMapper json;
    private final int minSources;

    public ResearchStage(LlmRouter llm, JsonMapper json, StoryForgeProperties properties) {
        this.llm = llm;
        this.json = json;
        this.minSources = properties.qa().minSources();
    }

    @Override
    public Stage stage() {
        return Stage.RESEARCH;
    }

    @Override
    public List<String> outputs() {
        return List.of("dossier.json", "sources.json");
    }

    @Override
    public void execute(StageContext context) {
        TopicBrief brief = context.files().read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Dossier dossier = llm.json(context, stage(), "research", Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "core_promise", brief.corePromise(),
                "min_sources", String.valueOf(minSources)), Dossier.class);
        if (dossier.claims() == null || dossier.claims().isEmpty() || dossier.sources() == null) {
            throw new StageBlockedException("Research gate: dossier has no claims or sources");
        }
        List<Claim> weak = weakKeyClaims(dossier);
        if (!weak.isEmpty()) {
            log.info("Run {}: {} key claim(s) single-sourced, asking for second sources", context.run().id(), weak.size());
            SourceFix fix = llm.json(context, stage(), "research_fix", Map.of(
                    "topic", brief.title(),
                    "sources", json.writeValueAsString(dossier.sources().stream()
                            .map(s -> Map.of("id", s.id(), "url", s.url())).toList()),
                    "claims", json.writeValueAsString(weak.stream()
                            .map(c -> Map.of("id", c.id(), "text", c.text(), "sourceIds", c.sourceIds())).toList())),
                    SourceFix.class);
            dossier = merge(dossier, fix);
            dossier = demote(dossier, weakKeyClaims(dossier));
        }
        context.files().write(Stage.RESEARCH, "dossier.json", dossier);
        context.files().write(Stage.RESEARCH, "sources.json", dossier.sources());
        if (dossier.sources().size() < minSources) {
            throw new StageBlockedException("Research gate: " + dossier.sources().size() + " sources, needs at least "
                    + minSources);
        }
    }

    static List<Claim> weakKeyClaims(Dossier dossier) {
        return dossier.claims().stream().filter(Claim::key)
                .filter(claim -> dossier.independentSourceCount(claim) < 2).toList();
    }

    static Dossier merge(Dossier dossier, SourceFix fix) {
        if (fix == null || fix.sources() == null || fix.claims() == null) {
            return dossier;
        }
        Set<String> takenIds = dossier.sources().stream().map(Source::id).collect(Collectors.toCollection(HashSet::new));
        Set<String> knownUrls = dossier.sources().stream().map(Source::url).collect(Collectors.toSet());
        List<Source> sources = new ArrayList<>(dossier.sources());
        Map<String, String> renamed = new java.util.HashMap<>();
        for (Source source : fix.sources()) {
            if (source.url() == null || knownUrls.contains(source.url())) {
                continue;
            }
            String id = source.id();
            int n = sources.size() + 1;
            while (id == null || takenIds.contains(id)) {
                id = "s" + n++;
            }
            takenIds.add(id);
            renamed.put(source.id(), id);
            sources.add(new Source(id, source.title(), source.url(), source.publisher(), source.tier(), source.accessed()));
        }
        Map<String, ClaimFix> fixes = fix.claims().stream()
                .collect(Collectors.toMap(ClaimFix::id, Function.identity(), (a, b) -> a));
        List<Claim> claims = dossier.claims().stream().map(claim -> {
            ClaimFix claimFix = fixes.get(claim.id());
            if (claimFix == null || claimFix.sourceIds() == null) {
                return claim;
            }
            Set<String> ids = new LinkedHashSet<>(claim.sourceIds());
            claimFix.sourceIds().stream().map(renamed::get).filter(id -> id != null).forEach(ids::add);
            return new Claim(claim.id(), claim.text(), List.copyOf(ids), claim.key(), claim.contested());
        }).toList();
        return new Dossier(dossier.topic(), sources, claims, dossier.timeline(), dossier.characters(),
                dossier.places(), dossier.contested(), dossier.wowFacts(), dossier.visualNotes());
    }

    static Dossier demote(Dossier dossier, List<Claim> stillWeak) {
        if (stillWeak.isEmpty()) {
            return dossier;
        }
        Set<String> ids = stillWeak.stream().map(Claim::id).collect(Collectors.toSet());
        List<Claim> claims = dossier.claims().stream().map(claim -> ids.contains(claim.id())
                ? new Claim(claim.id(), claim.text(), claim.sourceIds(), false, true)
                : claim).toList();
        List<String> contested = new ArrayList<>(dossier.contested() == null ? List.of() : dossier.contested());
        stillWeak.forEach(claim -> contested.add("Single-sourced, narrate with attribution: " + claim.id()));
        return new Dossier(dossier.topic(), dossier.sources(), claims, dossier.timeline(), dossier.characters(),
                dossier.places(), contested, dossier.wowFacts(), dossier.visualNotes());
    }
}
