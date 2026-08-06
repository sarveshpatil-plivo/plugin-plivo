package io.kestra.plugin.plivo.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

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
    title = "Validate the signature of an inbound Plivo webhook",
    description = """
        Recomputes Plivo's V3 request signature from the Auth Token, the exact URL Plivo posted to, the
        received body parameters, and the nonce, then compares it against the signature header.
        Use this after a core Webhook trigger to confirm an inbound SMS or call POST really came from Plivo
        before acting on `trigger.body`.
        Pass the value of the `X-Plivo-Signature-Ma-V3` header (or `X-Plivo-Signature-V3`) as `signature`
        and the `X-Plivo-Signature-V3-Nonce` header as `nonce`.
        See the <a href="https://www.plivo.com/docs/messaging/concepts/validate-signature/">Plivo documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Validate an inbound Plivo webhook received by a core Webhook trigger.",
            full = true,
            code = """
                id: verify_plivo_webhook
                namespace: company.team

                tasks:
                  - id: verify
                    type: io.kestra.plugin.plivo.security.VerifySignature
                    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
                    url: "https://kestra.example.com/api/v1/main/executions/webhook/company.team/verify_plivo_webhook/plivoKey"
                    params: "{{ trigger.body }}"
                    signature: "{{ trigger.headers['x-plivo-signature-ma-v3'][0] ?? trigger.headers['x-plivo-signature-v3'][0] }}"
                    nonce: "{{ trigger.headers['x-plivo-signature-v3-nonce'][0] }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: plivoKey
                """
        )
    }
)
public class VerifySignature extends Task implements RunnableTask<VerifySignature.Output> {

    @NotNull
    @Schema(
        title = "Plivo Auth Token",
        description = "The Auth Token used as the HMAC-SHA256 key; store as a Kestra secret"
    )
    @PluginProperty(secret = true, group = "connection")
    private Property<String> authToken;

    @NotNull
    @Schema(
        title = "Webhook URL",
        description = "The exact URL Plivo posted to, used as the base of the signed string"
    )
    @PluginProperty(group = "main")
    private Property<String> url;

    @Schema(
        title = "Body parameters",
        description = "The form parameters Plivo posted, e.g. `{{ trigger.body }}` from a core Webhook trigger"
    )
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> params;

    @NotNull
    @Schema(
        title = "Signature",
        description = "Value of the `X-Plivo-Signature-Ma-V3` header (or `X-Plivo-Signature-V3`)"
    )
    @PluginProperty(group = "main")
    private Property<String> signature;

    @NotNull
    @Schema(
        title = "Nonce",
        description = "Value of the `X-Plivo-Signature-V3-Nonce` header"
    )
    @PluginProperty(group = "main")
    private Property<String> nonce;

    public static String computeSignature(String authToken, String url, Map<String, String> params, String nonce) throws Exception {
        var base = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            base.append("?");
            var sorted = new TreeMap<>(params);
            for (var entry : sorted.entrySet()) {
                base.append(entry.getKey()).append(entry.getValue() == null ? "" : entry.getValue());
            }
        }
        base.append(".").append(nonce);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(base.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ba, bb);
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAuthToken = runContext.render(authToken).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rUrl = runContext.render(url).as(String.class).orElseThrow(() -> new IllegalArgumentException("url is required"));
        var rSignature = runContext.render(signature).as(String.class).orElseThrow(() -> new IllegalArgumentException("signature is required"));
        var rNonce = runContext.render(nonce).as(String.class).orElseThrow(() -> new IllegalArgumentException("nonce is required"));
        var rParams = runContext.render(params).asMap(String.class, Object.class);

        Map<String, String> stringParams = new TreeMap<>();
        if (rParams != null) {
            for (var entry : rParams.entrySet()) {
                stringParams.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }

        var expected = computeSignature(rAuthToken, rUrl, stringParams, rNonce);
        var valid = constantTimeEquals(expected, rSignature);

        if (valid) {
            runContext.logger().info("Plivo webhook signature is valid");
        } else {
            runContext.logger().warn("Plivo webhook signature is INVALID; expected {} received {}", expected, rSignature);
        }

        return Output.builder()
            .valid(valid)
            .build();
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Valid", description = "True when the recomputed signature matches the received signature")
        private final Boolean valid;
    }
}
