package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.Scene.Segment;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.script.SpeechText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The narrator that talks instead of reading: every scene goes through media-svc's human delivery engine
 * (/speak), with the NVIDIA Magpie voice ("magpie:Jason") or a Kokoro voice/blend. The script's delivery marks travel
 * with the text: the scene's tone sets the starting mood, [mood: x] switches it, [beat] holds a pause before a reveal,
 * and @say lines plus config/pronunciations.json fix words the voice gets wrong.
 */
@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "tts", havingValue = "delivery")
public class DeliveryTtsProvider implements TtsProvider {

    /** Part of the cache key: bump it when the delivery engine changes, so old takes are not reused. */
    static final String ENGINE_VERSION = "delivery-v1";
    static final String MAGPIE = "magpie:";

    private static final Logger log = LoggerFactory.getLogger(DeliveryTtsProvider.class);

    private final MediaSvcClient media;
    private final StoryForgeProperties.Narration narration;
    private final Path cacheDir;
    private final JsonMapper json;
    private final Map<String, Map<String, String>> pronunciations;

    public DeliveryTtsProvider(MediaSvcClient media, StoryForgeProperties properties, JsonMapper json) {
        this.media = media;
        this.narration = properties.narration();
        this.cacheDir = Path.of(properties.dataDir()).toAbsolutePath().resolve("cache").resolve("tts");
        this.json = json;
        this.pronunciations = loadPronunciations(Path.of(properties.configDir()).resolve("pronunciations.json"), json);
    }

    @Override
    public String name() {
        return "delivery";
    }

    @Override
    public String voice() {
        return narration.voice();
    }

    @Override
    public String resolveVoice(String requested) {
        String voice = requested != null ? requested : narration.voice();
        if (isMagpie(voice) && !media.magpieReady()) {
            log.warn("The Magpie voice is unavailable (key, library or NVIDIA API); this run is narrated with {}",
                    narration.fallback());
            return narration.fallback();
        }
        return voice;
    }

    @Override
    public double synthesize(Scene scene, Path outputWav) {
        return synthesize(scene, outputWav, 1.0, null);
    }

    @Override
    public double synthesize(Scene scene, Path outputWav, double speedFactor, String voice) {
        String chosen = voice != null ? voice : narration.voice();
        boolean magpie = isMagpie(chosen);
        String engine = magpie ? "magpie" : "kokoro";
        String name = magpie ? chosen.substring(MAGPIE.length()) : chosen;
        double sceneSpeed = scene.speed() > 0 ? scene.speed() : 1.0;
        double speed = Math.round(narration.speed() * sceneSpeed * speedFactor * 1000) / 1000.0;
        List<Map<String, Object>> items = items(scene);
        Map<String, Map<String, String>> lexicon = lexicon(scene);
        String mood = mood(scene.tone(), scene.emotion());
        Path cached = cacheDir.resolve(hash(String.join("|", ENGINE_VERSION, engine, name, String.valueOf(speed),
                String.valueOf(narration.deliveryPace()), String.valueOf(narration.withBreaths()), narration.lang(),
                mood, json.writeValueAsString(items), json.writeValueAsString(lexicon))) + ".wav");
        try {
            if (!Files.isRegularFile(cached)) {
                Files.createDirectories(cacheDir);
                MediaSvcClient.Speech speech = media.speak(items, engine, name, speed, narration.lang(), mood,
                        narration.deliveryPace(), narration.withBreaths(), lexicon, scene.id() + ":" + speedFactor,
                        cached);
                log.info("Scene {} voiced by {} {}: {} thoughts, {} takes{}", scene.id(), engine, name,
                        speech.chunks(), speech.takes(), speech.issues().isEmpty() ? "" : ", issues " + speech.issues());
            }
            Files.copy(cached, outputWav, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return WavAudio.durationSec(outputWav);
    }

    static boolean isMagpie(String voice) {
        return voice != null && voice.startsWith(MAGPIE);
    }

    /** The scene in script order: words (as said), mood switches, held beats and pauses. */
    static List<Map<String, Object>> items(Scene scene) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Segment segment : scene.segments()) {
            switch (segment.kind() == null ? "text" : segment.kind()) {
                case "text" -> items.add(Map.of("kind", "text", "text", SpeechText.normalize(segment.text())));
                case "mood" -> items.add(Map.of("kind", "mood", "text", segment.text()));
                case "beat" -> items.add(Map.of("kind", "beat", "ms", segment.pauseMs()));
                case "pause" -> items.add(Map.of("kind", "pause", "ms", segment.pauseMs()));
                default -> {
                    // @say rules travel in the lexicon, not as words
                }
            }
        }
        return items;
    }

    /** The scene's tone ("playful", "tense"...) as a starting mood: hype, neutral, serious or angry. */
    static String mood(String tone, String emotion) {
        String t = ((tone == null ? "" : tone) + " " + (emotion == null ? "" : emotion)).toLowerCase(Locale.ROOT);
        if (t.matches(".*\\b(angry|furious|rant|outraged|rage)\\b.*")) {
            return "angry";
        }
        if (t.matches(".*\\b(playful|excited|hype|fun|funny|sarcastic|energetic|cocky|upbeat|comic|humor|humour|"
                + "triumphant|amazed|curious|surprised)\\b.*")) {
            return "hype";
        }
        if (t.matches(".*\\b(serious|tense|dark|sad|somber|sombre|reflective|calm|grave|ominous|mysterious|eerie|"
                + "tragic|quiet|haunting|melancholic)\\b.*")) {
            return "serious";
        }
        return "neutral";
    }

    /** config/pronunciations.json, then the scene's own @say lines ("word = respelling" or "word = ipa: ..."). */
    Map<String, Map<String, String>> lexicon(Scene scene) {
        return lexicon(pronunciations, scene);
    }

    static Map<String, Map<String, String>> lexicon(Map<String, Map<String, String>> base, Scene scene) {
        Map<String, Map<String, String>> lexicon = new LinkedHashMap<>(base);
        for (Segment segment : scene.segments()) {
            if (!"say".equals(segment.kind()) || segment.text() == null || !segment.text().contains("=")) {
                continue;
            }
            String[] rule = segment.text().split("=", 2);
            String word = rule[0].strip().toLowerCase(Locale.ROOT);
            String said = rule[1].strip();
            if (word.isEmpty() || said.isEmpty()) {
                continue;
            }
            lexicon.put(word, said.toLowerCase(Locale.ROOT).startsWith("ipa:")
                    ? Map.of("ipa", said.substring(4).strip()) : Map.of("say", said));
        }
        return lexicon;
    }

    static Map<String, Map<String, String>> loadPronunciations(Path file, JsonMapper json) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, Map<String, String>> raw = json.readValue(file.toFile(),
                new TypeReference<Map<String, Map<String, String>>>() { });
        Map<String, Map<String, String>> lower = new LinkedHashMap<>();
        raw.forEach((word, entry) -> {
            if (!word.startsWith("_")) {
                lower.put(word.toLowerCase(Locale.ROOT), entry);
            }
        });
        return lower;
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
