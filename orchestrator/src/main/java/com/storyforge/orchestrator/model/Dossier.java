package com.storyforge.orchestrator.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Dossier(
        String topic,
        List<Source> sources,
        List<Claim> claims,
        List<TimelineEvent> timeline,
        List<Person> characters,
        List<String> places,
        List<String> contested,
        List<String> wowFacts,
        List<String> visualNotes) {

    public record Source(String id, String title, String url, String publisher, String tier, String accessed) {

        public String host() {
            try {
                String host = URI.create(url).getHost();
                return host == null ? url : host.replaceFirst("^www\\.", "");
            } catch (IllegalArgumentException e) {
                return url;
            }
        }
    }

    public record Claim(String id, String text, List<String> sourceIds, boolean key, boolean contested) {
    }

    public record TimelineEvent(String date, String event, List<String> sourceIds) {
    }

    public record Person(String name, String role, boolean living) {
    }

    public Map<String, Source> sourcesById() {
        return sources.stream().collect(Collectors.toMap(Source::id, Function.identity(), (a, b) -> a));
    }

    public Optional<Claim> claim(String id) {
        return claims.stream().filter(claim -> claim.id().equals(id)).findFirst();
    }

    public long independentSourceCount(Claim claim) {
        Map<String, Source> byId = sourcesById();
        return claim.sourceIds().stream().map(byId::get).filter(source -> source != null)
                .map(Source::host).distinct().count();
    }
}
