package com.storyforge.orchestrator.provider.realmedia;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.packaging.DescriptionBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealMediaFinderTest {

    @Test
    void acceptsOnlyLicensesSafeForAMonetizedVideo() {
        assertThat(List.of("Public domain", "PD-US", "CC0", "CC BY 4.0", "CC BY-SA 2.0", "cc by-sa 3.0"))
                .allMatch(RealMediaFinder::allowed);
        assertThat(List.of("CC BY-NC 2.0", "CC BY-ND 4.0", "CC BY-NC-SA 3.0", "GFDL", "Fair use", ""))
                .noneMatch(RealMediaFinder::allowed);
        assertThat(RealMediaFinder.licenseType("CC BY-SA 2.0")).isEqualTo("cc-by-sa-2.0");
        assertThat(RealMediaFinder.licenseType("Public domain")).isEqualTo("public_domain");
    }

    @Test
    void stripsHtmlFromAuthorNames() {
        assertThat(RealMediaFinder.stripHtml("<a href=\"//x\">Jane&nbsp;Doe</a> &amp; <b>co</b>"))
                .isEqualTo("Jane Doe & co");
    }

    @Test
    void longCreditListsFallBackToAShortFormInsideYouTubesLimit() {
        String credit = "\"Flannan Isles, the lighthouse - geograph.org.uk - 1234567\" by Some Photographer, CC BY-SA 2.0, "
                + "via Wikimedia Commons: https://commons.wikimedia.org/wiki/File:Flannan_Isles_lighthouse_1234567.jpg";
        List<String> credits = java.util.stream.IntStream.range(0, 40).mapToObj(i -> credit.replace("1234567",
                String.valueOf(1000000 + i))).toList();

        String description = DescriptionBuilder.build("Hook.", List.of(), List.of(), List.of(), credits, List.of());

        assertThat(description.length()).isLessThanOrEqualTo(DescriptionBuilder.MAX_CHARS);
        assertThat(description).contains("Photos and footage", "Some Photographer, CC BY-SA 2.0");
        assertThat(DescriptionBuilder.compactCredit(credit)).isEqualTo("Some Photographer, CC BY-SA 2.0");
    }
}
