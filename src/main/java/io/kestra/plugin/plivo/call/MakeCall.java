package io.kestra.plugin.plivo.call;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.plivo.AbstractPlivoConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Make an outbound voice call via the Plivo Voice API",
    description = """
        Posts a call request to the Plivo Voice API using Auth ID and Auth Token for basic authentication.
        The answerUrl must return valid Plivo XML (for example a <Speak> element) that controls the call.
        Returns the request UUID and API ID from the API response.
        See the <a href="https://www.plivo.com/docs/voice/api/call">Plivo documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Make an outbound call that plays a spoken message.",
            full = true,
            code = """
                id: make_call
                namespace: company.team

                tasks:
                  - id: call
                    type: io.kestra.plugin.plivo.call.MakeCall
                    authId: "{{ secret('PLIVO_AUTH_ID') }}"
                    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
                    from: "{{ secret('PLIVO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    answerUrl: "https://example.com/answer.xml"
                """
        )
    }
)
public class MakeCall extends AbstractPlivoConnection implements RunnableTask<MakeCall.Output> {

    @NotNull
    @Schema(
        title = "Caller ID",
        description = "The source Plivo phone number placing the call"
    )
    @PluginProperty(group = "main")
    private Property<String> from;

    @NotNull
    @Schema(
        title = "Recipient phone number",
        description = "The destination phone number in E.164 format"
    )
    @PluginProperty(group = "main")
    private Property<String> to;

    @NotNull
    @Schema(
        title = "Answer URL",
        description = "URL returning Plivo XML that controls the call once answered, e.g. a <Speak> element"
    )
    @PluginProperty(group = "main")
    private Property<String> answerUrl;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAuthId = runContext.render(getAuthId()).as(String.class).orElseThrow(() -> new IllegalArgumentException("authId is required"));
        var rAuthToken = runContext.render(getAuthToken()).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rFrom = runContext.render(from).as(String.class).orElseThrow(() -> new IllegalArgumentException("from is required"));
        var rTo = runContext.render(to).as(String.class).orElseThrow(() -> new IllegalArgumentException("to is required"));
        var rAnswerUrl = runContext.render(answerUrl).as(String.class).orElseThrow(() -> new IllegalArgumentException("answerUrl is required"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("from", rFrom);
        parameters.put("to", rTo);
        parameters.put("answer_url", rAnswerUrl);

        var url = accountResourceUrl(baseUrl(), rAuthId, "Call/");
        var payload = JacksonMapper.ofJson().writeValueAsString(parameters);

        runContext.logger().debug("Placing Plivo call to {}", rTo);

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
                    "Plivo Voice API returned HTTP " + e.getResponse().getStatus().getCode() + ": " + e.getResponse().getBody(),
                    e
                );
            }

            var statusCode = response.getStatus().getCode();
            if (statusCode != 201) {
                throw new RuntimeException(
                    "Plivo Voice API returned HTTP " + statusCode + ": " + response.getBody()
                );
            }

            var parsed = JacksonMapper.ofJson().readValue(response.getBody(), CallResponse.class);
            runContext.logger().info("Call fired, apiId={} requestUuid={}", parsed.getApiId(), parsed.getRequestUuid());

            return Output.builder()
                .requestUuid(parsed.getRequestUuid())
                .apiId(parsed.getApiId())
                .message(parsed.getMessage())
                .build();
        }
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Request UUID", description = "Identifier assigned by Plivo to the placed call")
        private final String requestUuid;

        @Schema(title = "API ID", description = "Identifier assigned by Plivo to this API request")
        private final String apiId;

        @Schema(title = "Message", description = "Human-readable status returned by Plivo, e.g. 'call fired'")
        private final String message;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CallResponse {
        private String message;
        @JsonProperty("request_uuid")
        private String requestUuid;
        @JsonProperty("api_id")
        private String apiId;
    }
}
