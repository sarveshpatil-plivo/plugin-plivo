package io.kestra.plugin.plivo.message;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.plivo.AbstractPlivoConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow on new inbound Plivo SMS",
    description = """
        Polls the Plivo Messages API on an interval and creates an execution when inbound messages received
        within the last polling window are found. Fetched messages are exposed as trigger outputs.
        See the <a href="https://www.plivo.com/docs/messaging/api/message/list-all-messages/">Plivo documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Run a flow whenever an inbound SMS is received.",
            full = true,
            code = """
                id: on_inbound_sms
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ trigger.messages }}"

                triggers:
                  - id: inbound
                    type: io.kestra.plugin.plivo.message.Trigger
                    authId: "{{ secret('PLIVO_AUTH_ID') }}"
                    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
                    interval: PT1M
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {
    private static final String DEFAULT_BASE_URL = "https://api.plivo.com";
    private static final DateTimeFormatter PLIVO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @NotNull
    @Schema(
        title = "Plivo Auth ID",
        description = "The Auth ID used for basic authentication and to build the Account API URL"
    )
    @PluginProperty(group = "connection")
    private Property<String> authId;

    @NotNull
    @Schema(
        title = "Plivo Auth Token",
        description = "The Auth Token paired with the Auth ID; store as a Kestra secret"
    )
    @PluginProperty(secret = true, group = "connection")
    private Property<String> authToken;

    @Builder.Default
    @Schema(
        title = "Interval between polls",
        description = "How often the Plivo Messages API is polled for new inbound messages"
    )
    private final Duration interval = Duration.ofSeconds(60);

    @Builder.Default
    @Schema(
        title = "Max records",
        description = "Maximum number of inbound messages fetched per poll"
    )
    @PluginProperty(group = "execution")
    private Property<Integer> maxRecords = Property.ofValue(20);

    protected String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();

        var rAuthId = runContext.render(authId).as(String.class).orElseThrow(() -> new IllegalArgumentException("authId is required"));
        var rAuthToken = runContext.render(authToken).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rMaxRecords = runContext.render(maxRecords).as(Integer.class).orElse(20);

        var since = ZonedDateTime.now(ZoneOffset.UTC).minus(interval).format(PLIVO_TIME);
        var url = AbstractPlivoConnection.accountResourceUrl(baseUrl(), rAuthId, "Message/")
            + "?message_direction=inbound&limit=" + rMaxRecords
            + "&message_time__gt=" + java.net.URLEncoder.encode(since, java.nio.charset.StandardCharsets.UTF_8);

        List<Map<String, Object>> messages;
        try (var client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            var request = HttpRequest.builder()
                .addHeader("Authorization", AbstractPlivoConnection.basicAuthHeader(rAuthId, rAuthToken))
                .uri(URI.create(url))
                .method("GET")
                .build();

            HttpResponse<String> response;
            try {
                response = client.request(request, String.class);
            } catch (HttpClientResponseException e) {
                throw new RuntimeException(
                    "Plivo Messages API returned HTTP " + e.getResponse().getStatus().getCode() + ": " + e.getResponse().getBody(),
                    e
                );
            }

            var statusCode = response.getStatus().getCode();
            if (statusCode != 200) {
                throw new RuntimeException(
                    "Plivo Messages API returned HTTP " + statusCode + ": " + response.getBody()
                );
            }

            var parsed = JacksonMapper.ofJson().readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            var objects = (List<Map<String, Object>>) parsed.getOrDefault("objects", List.of());
            messages = objects;
        }

        if (messages.isEmpty()) {
            return Optional.empty();
        }

        runContext.logger().info("Fetched {} inbound message(s) from Plivo", messages.size());

        Output output = Output.builder()
            .count(messages.size())
            .messages(messages)
            .build();

        Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);

        return Optional.of(execution);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Count", description = "Number of inbound messages fetched in this poll")
        private final Integer count;

        @Schema(title = "Messages", description = "The inbound message objects returned by the Plivo Messages API")
        private final List<Map<String, Object>> messages;
    }
}
