package com.sentria.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiTrendVerdictFormatter {

    private final OpenAiCompatibleClient aiClient;

    public AiTrendVerdictFormatter(OpenAiCompatibleClient aiClient) {
        this.aiClient = aiClient;
    }

    public String format(String technicalSummary, String fallbackDraft) {
        String prompt = """
                Rewrite this long-period system report for a normal user.

                Requirements:
                - English only
                - Keep this structure:
                  Global Long-Period Status:
                  Key Trends:
                  Most Used Applications:
                  Behavior Insights:
                  Recommendations:
                - Keep names human readable (CPU Usage, CPU Temperature, RAM Usage, Battery Level,
                  Battery Time Remaining, SSD Health, SSD Data Written, Storage Usage, Storage Free Space,
                  Network Download, Network Upload)
                - Keep values factual and do not invent anything
                - Explain clearly what happened over the configured long period
                - Keep short and practical
                - If low storage is present, include cleanup recommendation
                - If high CPU temperature is present, include cooling recommendation
                - If sustained upload traffic is present, include a background-sync check recommendation

                Technical summary:
                %s

                Fallback draft:
                %s
                """.formatted(technicalSummary, fallbackDraft);

        return aiClient.complete(prompt).orElse(fallbackDraft);
    }
}




