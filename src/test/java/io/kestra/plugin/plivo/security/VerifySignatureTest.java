package io.kestra.plugin.plivo.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class VerifySignatureTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final String AUTH_TOKEN = "test_auth_token_123";
    private static final String URL = "https://example.com/plivo/inbound";
    private static final String NONCE = "1122334455";
    private static final String EXPECTED_SIGNATURE = "KySztPgzkLI3oAlgJa2rNFyK79i2OvoJAFlFacFrYgk=";
    private static final String EXPECTED_SIGNATURE_EMPTY = "bDg39Dp8agjIqFjmoxExLSghaf7abMfQFVNxoLUMH2s=";

    private static Map<String, Object> smsParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("To", "+14245502321");
        params.put("From", "+19722758847");
        params.put("Text", "Hello Plivo");
        params.put("Type", "sms");
        params.put("MessageUUID", "abc-123");
        return params;
    }

    @Test
    void matchesKnownVector() throws Exception {
        Map<String, String> stringParams = new LinkedHashMap<>();
        smsParams().forEach((k, v) -> stringParams.put(k, String.valueOf(v)));

        String computed = VerifySignature.computeSignature(AUTH_TOKEN, URL, stringParams, NONCE);
        assertThat(computed, is(EXPECTED_SIGNATURE));
    }

    @Test
    void matchesKnownVectorEmptyParams() throws Exception {
        String computed = VerifySignature.computeSignature(AUTH_TOKEN, URL, Map.of(), NONCE);
        assertThat(computed, is(EXPECTED_SIGNATURE_EMPTY));
    }

    @Test
    void validSignatureReportsValid() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        VerifySignature task = VerifySignature.builder()
            .authToken(Property.ofValue(AUTH_TOKEN))
            .url(Property.ofValue(URL))
            .params(Property.ofValue(smsParams()))
            .signature(Property.ofValue(EXPECTED_SIGNATURE))
            .nonce(Property.ofValue(NONCE))
            .build();

        VerifySignature.Output output = task.run(runContext);
        assertThat(output.getValid(), is(true));
    }

    @Test
    void tamperedSignatureReportsInvalid() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        VerifySignature task = VerifySignature.builder()
            .authToken(Property.ofValue(AUTH_TOKEN))
            .url(Property.ofValue(URL))
            .params(Property.ofValue(smsParams()))
            .signature(Property.ofValue("not-the-right-signature"))
            .nonce(Property.ofValue(NONCE))
            .build();

        VerifySignature.Output output = task.run(runContext);
        assertThat(output.getValid(), is(false));
    }

    @Test
    void wrongAuthTokenReportsInvalid() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        VerifySignature task = VerifySignature.builder()
            .authToken(Property.ofValue("wrong_token"))
            .url(Property.ofValue(URL))
            .params(Property.ofValue(smsParams()))
            .signature(Property.ofValue(EXPECTED_SIGNATURE))
            .nonce(Property.ofValue(NONCE))
            .build();

        VerifySignature.Output output = task.run(runContext);
        assertThat(output.getValid(), is(false));
    }
}
