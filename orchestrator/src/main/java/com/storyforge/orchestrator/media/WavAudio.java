package com.storyforge.orchestrator.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class WavAudio {

    public static final int SAMPLE_RATE = 24_000;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private WavAudio() {
    }

    public static final class Builder {

        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();

        public Builder silence(double seconds) {
            int samples = (int) Math.round(seconds * SAMPLE_RATE);
            pcm.writeBytes(new byte[samples * 2]);
            return this;
        }

        public Builder tone(double seconds, double frequency, double amplitude) {
            int samples = (int) Math.round(seconds * SAMPLE_RATE);
            int fade = Math.min(samples / 2, SAMPLE_RATE / 200);
            for (int i = 0; i < samples; i++) {
                double envelope = Math.min(1.0, Math.min(i, samples - 1 - i) / (double) Math.max(1, fade));
                short value = (short) (Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE) * amplitude * envelope
                        * Short.MAX_VALUE);
                pcm.write(value & 0xff);
                pcm.write((value >> 8) & 0xff);
            }
            return this;
        }

        public Builder append(Path wav) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
                AudioFormat format = in.getFormat();
                if (!format.matches(FORMAT)) {
                    throw new MediaException("Unsupported WAV format " + format + " in " + wav);
                }
                pcm.writeBytes(in.readAllBytes());
            } catch (IOException | UnsupportedAudioFileException e) {
                throw new MediaException("Cannot read " + wav, e);
            }
            return this;
        }

        public double durationSec() {
            return pcm.size() / 2.0 / SAMPLE_RATE;
        }

        public void write(Path out) {
            byte[] bytes = pcm.toByteArray();
            try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), FORMAT,
                    bytes.length / 2)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out.toFile());
            } catch (IOException e) {
                throw new MediaException("Cannot write " + out, e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static double durationSec(Path wav) {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(wav.toFile());
            return format.getFrameLength() / (double) format.getFormat().getFrameRate();
        } catch (IOException | UnsupportedAudioFileException e) {
            throw new MediaException("Cannot read " + wav, e);
        }
    }

    public static void concat(List<Path> inputs, Path out) {
        Builder builder = builder();
        inputs.forEach(builder::append);
        builder.write(out);
    }
}
