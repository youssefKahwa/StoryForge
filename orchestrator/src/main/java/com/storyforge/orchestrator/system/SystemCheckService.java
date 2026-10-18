package com.storyforge.orchestrator.system;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.provider.image.ImageQuota;
import com.storyforge.orchestrator.provider.media.MediaSvcLauncher;
import com.storyforge.orchestrator.sound.MusicLibrary;
import com.storyforge.orchestrator.youtube.YouTubeAuthService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Read-only health checks of everything a weekly run depends on, each with a plain-language fix. */
@Service
public class SystemCheckService {

    private static final long MIN_FREE_BYTES = 10L * 1024 * 1024 * 1024;

    private final StoryForgeProperties properties;
    private final MediaSvcLauncher mediaSvc;
    private final MusicLibrary music;
    private final ImageQuota quota;
    private final YouTubeAuthService youtube;
    private final NotificationService notifications;
    private final StorageService storage;

    public SystemCheckService(StoryForgeProperties properties, MediaSvcLauncher mediaSvc, MusicLibrary music,
                              ImageQuota quota, YouTubeAuthService youtube, NotificationService notifications,
                              StorageService storage) {
        this.properties = properties;
        this.mediaSvc = mediaSvc;
        this.music = music;
        this.quota = quota;
        this.youtube = youtube;
        this.notifications = notifications;
        this.storage = storage;
    }

    public List<CheckResult> run() {
        StoryForgeProperties.Providers providers = properties.providers();
        List<CheckResult> checks = new ArrayList<>();
        checks.add(claude());
        checks.add(tool("ffmpeg", "FFmpeg", properties.media().ffmpeg(), "-version",
                "Install FFmpeg (winget install Gyan.FFmpeg) and reopen the terminal."));
        checks.add(mediaService());
        checks.add("remotion".equals(providers.render()) ? remotion()
                : CheckResult.off("remotion", "Remotion renderer",
                        "Not used by this profile (" + providers.render() + ")."));
        checks.add(upscaler());
        checks.add("cloudflare".equals(providers.image()) ? cloudflare()
                : CheckResult.off("cloudflare", "Cloudflare images",
                        "Not used by this profile (" + providers.image() + ")."));
        checks.add(properties.sound().enabled() ? musicLibrary()
                : CheckResult.off("music", "Music library", "Sound design is off in this profile."));
        checks.add(disk());
        checks.add("youtube".equals(providers.publish()) ? youtubeCheck()
                : CheckResult.off("youtube", "YouTube upload", "Publish mode is " + providers.publish()
                        + ": you upload the export folder yourself. Set STORYFORGE_PUBLISH=youtube to automate."));
        checks.add(notifications.enabled() ? CheckResult.ok("telegram", "Telegram alerts", "Configured.")
                : CheckResult.off("telegram", "Telegram alerts", "Not configured; no phone alerts."));
        return checks;
    }

    private CheckResult claude() {
        StoryForgeProperties.Llm.ClaudeCode cc = properties.llm().claudeCode();
        if (cc == null || !cc.enabled()) {
            return CheckResult.off("claude", "Claude Code", "Disabled.");
        }
        String version = exec(List.of(cc.command(), "--version"), 20);
        if (version == null) {
            return CheckResult.fail("claude", "Claude Code", "The claude command did not answer.",
                    "Install Claude Code and make sure 'claude' runs in a terminal.");
        }
        boolean credentials = Files.isRegularFile(Path.of(System.getProperty("user.home"), ".claude",
                ".credentials.json"));
        return credentials ? CheckResult.ok("claude", "Claude Code", version.strip())
                : CheckResult.warn("claude", "Claude Code", version.strip() + ", but no saved login was found.",
                        "Open a terminal, run 'claude' once and log in with your Pro account.");
    }

    private CheckResult mediaService() {
        String label = "Voice and alignment service";
        if (!mediaSvc.needed()) {
            return CheckResult.off("media-svc", label, "Not used by this profile.");
        }
        if (mediaSvc.healthy()) {
            return CheckResult.ok("media-svc", label, "Running at " + properties.mediaSvc().url() + ".");
        }
        boolean installed = Files.isRegularFile(Path.of(properties.mediaSvc().python()));
        return installed
                ? CheckResult.warn("media-svc", label,
                        "Installed but not running; it starts automatically when a run needs it.", null)
                : CheckResult.fail("media-svc", label, "Not installed.", "Run scripts\\media-svc.ps1 -Install once.");
    }

    private CheckResult remotion() {
        Path dir = Path.of(properties.renderSvc().dir());
        return Files.isDirectory(dir.resolve("node_modules").resolve("@remotion"))
                ? CheckResult.ok("remotion", "Remotion renderer", "Installed at " + dir + ".")
                : CheckResult.fail("remotion", "Remotion renderer", "Not installed.",
                        "Run scripts\\render-svc.ps1 -Install once.");
    }

    private CheckResult upscaler() {
        StoryForgeProperties.Upscaler config = properties.upscaler();
        if (config == null || !config.enabled()) {
            return CheckResult.off("upscaler", "Image upscaler", "Disabled; images use Lanczos + sharpening.");
        }
        return Files.isRegularFile(Path.of(config.executable()))
                ? CheckResult.ok("upscaler", "Image upscaler",
                        "Real-ESRGAN " + config.model() + " " + config.scale() + "x.")
                : CheckResult.warn("upscaler", "Image upscaler",
                        "Real-ESRGAN not found; images fall back to Lanczos + sharpening.",
                        "Download realesrgan-ncnn-vulkan to " + config.executable() + ".");
    }

    private CheckResult cloudflare() {
        StoryForgeProperties.Cloudflare config = properties.cloudflare();
        if (config == null || blank(config.accountId()) || blank(config.apiToken())) {
            return CheckResult.fail("cloudflare", "Cloudflare images", "Account ID or API token missing.",
                    "Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN in .env.");
        }
        int left = quota.remaining();
        String detail = left + " of " + config.dailyImageLimit() + " free images left today (resets 00:00 UTC).";
        return left >= 60 ? CheckResult.ok("cloudflare", "Cloudflare images", detail)
                : CheckResult.warn("cloudflare", "Cloudflare images", detail,
                        "A long video needs 100+ images; start after the daily reset or images will be reused.");
    }

    private CheckResult musicLibrary() {
        Map<String, List<MusicLibrary.Track>> library = music.scan();
        List<String> missing = MusicLibrary.MOODS.stream()
                .filter(mood -> library.getOrDefault(mood, List.of()).isEmpty()).toList();
        String counts = MusicLibrary.MOODS.stream()
                .map(mood -> mood + " " + library.getOrDefault(mood, List.of()).size())
                .collect(Collectors.joining(", "));
        if (missing.size() == MusicLibrary.MOODS.size()) {
            return CheckResult.warn("music", "Music library",
                    "No music yet: videos get voice and sound effects only.",
                    "Add licensed tracks to " + music.root() + "\\<mood> (see docs/OWNER_SETUP.md).");
        }
        return missing.isEmpty() ? CheckResult.ok("music", "Music library", counts)
                : CheckResult.warn("music", "Music library", counts, "Empty moods borrow from similar ones; add "
                        + "tracks for " + String.join(", ", missing) + " for more variety.");
    }

    private CheckResult disk() {
        long free = storage.freeBytes();
        String detail = StorageService.human(free) + " free on the data drive.";
        return free >= MIN_FREE_BYTES ? CheckResult.ok("disk", "Disk space", detail)
                : CheckResult.warn("disk", "Disk space", detail,
                        "A run needs a few GB. Use Settings > Storage > Clean up old runs.");
    }

    private CheckResult youtubeCheck() {
        if (!youtube.configured()) {
            return CheckResult.fail("youtube", "YouTube upload", "Google client ID/secret missing.",
                    "Follow the YouTube section of docs/OWNER_SETUP.md.");
        }
        return youtube.connected() ? CheckResult.ok("youtube", "YouTube upload", "Channel connected.")
                : CheckResult.fail("youtube", "YouTube upload", "Not connected.", "Settings > YouTube > Connect.");
    }

    private static CheckResult tool(String key, String label, String command, String arg, String fix) {
        String out = exec(List.of(command, arg), 15);
        return out == null ? CheckResult.fail(key, label, "'" + command + "' did not run.", fix)
                : CheckResult.ok(key, label, out.lines().findFirst().orElse("").strip());
    }

    private static String exec(List<String> command, long timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
