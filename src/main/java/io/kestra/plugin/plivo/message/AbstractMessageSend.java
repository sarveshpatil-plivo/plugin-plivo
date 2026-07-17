package io.kestra.plugin.plivo.message;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
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
public abstract class AbstractMessageSend extends AbstractPlivoConnection implements RunnableTask<AbstractMessageSend.Output> {

    @NotNull
    @Schema(
        title = "Sender ID",
        description = "The source Plivo phone number, short code, or alphanumeric sender ID"
    )
    @PluginProperty(group = "main")
    private Property<String> from;

    @NotNull
    @Schema(
        title = "Recipient phone number",
        description = "The destination phone number in E.164 format; multiple recipients are joined with '<'"
    )
    @PluginProperty(group = "main")
    private Property<String> to;

    @NotNull
    @Schema(
        title = "Message body",
        description = "The text content of the message"
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    protected void additionalParameters(RunContext runContext, Map<String, Object> parameters) throws Exception {
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAuthId = runContext.render(getAuthId()).as(String.class).orElseThrow(() -> new IllegalArgumentException("authId is required"));
        var rAuthToken = runContext.render(getAuthToken()).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rFrom = runContext.render(from).as(String.class).orElseThrow(() -> new IllegalArgumentException("from is required"));
        var rTo = runContext.render(to).as(String.class).orElseThrow(() -> new IllegalArgumentException("to is required"));
        var rBody = runContext.render(body).as(String.class).orElseThrow(() -> new IllegalArgumentException("body is required"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("src", rFrom);
        parameters.put("dst", rTo);
        parameters.put("text", rBody);
        additionalParameters(runContext, parameters);

        var url = accountResourceUrl(baseUrl(), rAuthId, "Message/");
        var payload = JacksonMapper.ofJson().writeValueAsString(parameters);

        runContext.logger().debug("Sending Plivo message to {}", rTo);

        try (var client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            var request = createRequestBuilder(runContext)
                .addHeader("Authorization", basicAuthHeader(rAuthId, rAuthToken))
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
                    .contentType("application/json")
                    .charset(StandardCharsets.UTF_8)
                    .content(payload)
                    .build())
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
            if (statusCode != 202) {
                throw new RuntimeException(
                    "Plivo Messages API returned HTTP " + statusCode + ": " + response.getBody()
                );
            }

            var parsed = JacksonMapper.ofJson().readValue(response.getBody(), MessageResponse.class);
            runContext.logger().info("Message queued, apiId={} messageUuid={}", parsed.getApiId(), parsed.getMessageUuid());

            return Output.builder()
                .messageUuid(parsed.getMessageUuid())
                .apiId(parsed.getApiId())
                .message(parsed.getMessage())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Message UUIDs", description = "One message UUID per recipient assigned by Plivo")
        private final List<String> messageUuid;

        @Schema(title = "API ID", description = "Identifier assigned by Plivo to this API request")
        private final String apiId;

        @Schema(title = "Message", description = "Human-readable status returned by Plivo, e.g. 'message(s) queued'")
        private final String message;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MessageResponse {
        private String message;
        @com.fasterxml.jackson.annotation.JsonProperty("message_uuid")
        private List<String> messageUuid;
        @com.fasterxml.jackson.annotation.JsonProperty("api_id")
        private String apiId;
    }
}
