package com.sentria.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiMetricsSummaryFormatter {

    private final OpenAiCompatibleClient aiClient;

    public AiMetricsSummaryFormatter(OpenAiCompatibleClient aiClient) {
        this.aiClient = aiClient;
    }

    public String format(String rawMetricsSummary, String fallbackDraft) {
        String prompt = """
                Rewrite this technical monitoring summary for a non-technical user.

                Rules:
                - Output must be in English
                - Use plain sentences and short lines
                - Keep exactly these sections and titles:
                  Overall Status:
                  Context:
                  Key Metrics:
                  Signals:
                  Recommendations:
                - Use readable metric names only:
                  CPU Usage, CPU Temperature, RAM Usage, Battery Level, Battery Charging State,
                  Battery Time Remaining, SSD Health, SSD Data Written (Total), Storage Usage,
                  Storage Free Space, Network Download, Network Upload
                - Keep units visible: percent as %%, storage as GB, charging as on/off, network as MB/s, temperature as C
                - Never use internal keys like status=, window_seconds=, or raw enum names
                - Do not invent data; use only given numbers and facts
                - Keep it concise and easy to read on mobile

                Recommendation behavior:
                - If battery is full and charging, include: unplug the charger
                - If battery is full but charging state is unclear/off, include conditional advice: "If the charger is still connected, unplug it"
                - If SSD health is low, include backup recommendation
                - If free storage is low, include cleanup/move-files recommendation
                - If CPU temperature is high, include cooling/ventilation recommendation
                - If battery remaining time is very low, include practical power-saving recommendation
                - If no risk is detected, include one calm maintenance recommendation

                Technical context:
                %s

                Draft fallback message:
                %s
                """.formatted(rawMetricsSummary, fallbackDraft);

        return aiClient.complete(prompt).orElse(fallbackDraft);
    }
}











