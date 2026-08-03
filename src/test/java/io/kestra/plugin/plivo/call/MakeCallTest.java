package io.kestra.plugin.plivo.call;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class MakeCallTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void makeCall(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/v1/Account/.*/Call/"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "message": "call fired",
                          "request_uuid": "cf3ce55a-7f1d-11e1-8ea7-1231380bc196",
                          "api_id": "df342550-7f1d-11e1-8ea7-1231380bc196"
                        }
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        MakeCall task = TestMakeCall.builder()
            .base(wireMock.getHttpBaseUrl())
            .authId(Property.ofValue("MAXXXXXXXXXXXXXXXXXX"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+14150000002"))
            .to(Property.ofValue("+14150000001"))
            .answerUrl(Property.ofValue("https://example.com/answer.xml"))
            .build();

        MakeCall.Output output = task.run(runContext);

        assertThat(output.getRequestUuid(), is("cf3ce55a-7f1d-11e1-8ea7-1231380bc196"));
        assertThat(output.getApiId(), is("df342550-7f1d-11e1-8ea7-1231380bc196"));

        verify(postRequestedFor(urlPathMatching("/v1/Account/.*/Call/"))
            .withRequestBody(matchingJsonPath("$.from", equalTo("+14150000002")))
            .withRequestBody(matchingJsonPath("$.to", equalTo("+14150000001")))
            .withRequestBody(matchingJsonPath("$.answer_url", equalTo("https://example.com/answer.xml"))));
    }

    @Test
    void failsOnNon201(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/v1/Account/.*/Call/"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"error": "The specified destination is invalid."}
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        MakeCall task = TestMakeCall.builder()
            .base(wireMock.getHttpBaseUrl())
            .authId(Property.ofValue("MAXXXXXXXXXXXXXXXXXX"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+14150000002"))
            .to(Property.ofValue("invalid"))
            .answerUrl(Property.ofValue("https://example.com/answer.xml"))
            .build();

        assertThrows(RuntimeException.class, () -> task.run(runContext));
    }

    @SuperBuilder
    @NoArgsConstructor(force = true)
    public static class TestMakeCall extends MakeCall {
        private final String base;

        @Override
        protected String baseUrl() {
            return base;
        }
    }
}
