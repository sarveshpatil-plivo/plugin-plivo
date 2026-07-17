package io.kestra.plugin.plivo;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPlivoConnection extends Task {
    protected static final String DEFAULT_BASE_URL = "https://api.plivo.com";

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

    @Schema(
        title = "Options",
        description = "Optional HTTP client overrides for timeouts, charset, headers, and max content length"
    )
    @PluginProperty(dynamic = true, group = "advanced")
    protected RequestOptions options;

    protected String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    public static String basicAuthHeader(String authId, String authToken) {
        return "Basic " + Base64.getEncoder().encodeToString(
            (authId + ":" + authToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String accountResourceUrl(String baseUrl, String authId, String resource) {
        return baseUrl + "/v1/Account/" + URLEncoder.encode(authId, StandardCharsets.UTF_8) + "/" + resource;
    }

    protected HttpConfiguration httpClientConfigurationWithOptions() throws IllegalVariableEvaluationException {
        HttpConfiguration.HttpConfigurationBuilder configuration = HttpConfiguration.builder();

        if (this.options != null) {
            configuration
                .timeout(
                    TimeoutConfiguration.builder()
                        .connectTimeout(this.options.getConnectTimeout())
                        .readIdleTimeout(this.options.getReadIdleTimeout())
                        .build()
                )
                .defaultCharset(this.options.getDefaultCharset());
        }

        return configuration.build();
    }

    protected HttpRequest.HttpRequestBuilder createRequestBuilder(
        RunContext runContext) throws IllegalVariableEvaluationException {

        HttpRequest.HttpRequestBuilder builder = HttpRequest.builder();

        if (this.options != null && this.options.getHeaders() != null) {
            Map<String, String> headers = runContext.render(this.options.getHeaders())
                .asMap(String.class, String.class);

            if (headers != null) {
                headers.forEach(builder::addHeader);
            }
        }
        return builder;
    }

    @Getter
    @Builder
    public static class RequestOptions {
        @Schema(
            title = "Connect timeout",
            description = "Time allowed to establish the HTTP connection before failing"
        )
        @PluginProperty(group = "execution")
        private final Property<Duration> connectTimeout;

        @Schema(
            title = "Read timeout",
            description = "Maximum time to read data before failing; defaults to 10s"
        )
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Duration> readTimeout = Property.ofValue(Duration.ofSeconds(10));

        @Schema(
            title = "Read idle timeout",
            description = "How long a read connection may stay idle before closing; defaults to 5m"
        )
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Duration> readIdleTimeout = Property.ofValue(Duration.of(5, ChronoUnit.MINUTES));

        @Schema(
            title = "Connection pool idle timeout",
            description = "Time an idle pooled connection stays open before closing; defaults to 0s (no TTL)"
        )
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Duration> connectionPoolIdleTimeout = Property.ofValue(Duration.ofSeconds(0));

        @Schema(
            title = "Max response size",
            description = "Maximum response content length in bytes; defaults to 10MB"
        )
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Integer> maxContentLength = Property.ofValue(1024 * 1024 * 10);

        @Schema(
            title = "Default request charset",
            description = "Charset applied to requests when not specified; defaults to UTF-8"
        )
        @Builder.Default
        @PluginProperty(group = "advanced")
        private final Property<Charset> defaultCharset = Property.ofValue(StandardCharsets.UTF_8);

        @Schema(
            title = "HTTP headers",
            description = "HTTP headers to include in the request"
        )
        @PluginProperty(group = "advanced")
        public Property<Map<String, String>> headers;
    }
}
