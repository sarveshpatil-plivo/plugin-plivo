package io.kestra.plugin.plivo.message;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;
import lombok.experimental.SuperBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class SendTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void sendSms(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/v1/Account/.*/Message/"))
                .willReturn(aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "message": "message(s) queued",
                          "message_uuid": ["db3ce55a-7f1d-11e1-8ea7-1231380bc196"],
                          "api_id": "db342550-7f1d-11e1-8ea7-1231380bc196"
                        }
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .authId(Property.ofValue("MAXXXXXXXXXXXXXXXXXX"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+14150000002"))
            .to(Property.ofValue("+14150000001"))
            .body(Property.ofValue("hello"))
            .build();

        Send.Output output = task.run(runContext);

        assertThat(output.getMessageUuid(), hasItem("db3ce55a-7f1d-11e1-8ea7-1231380bc196"));
        assertThat(output.getApiId(), is("db342550-7f1d-11e1-8ea7-1231380bc196"));

        verify(postRequestedFor(urlPathMatching("/v1/Account/.*/Message/"))
            .withRequestBody(matchingJsonPath("$.src", equalTo("+14150000002")))
            .withRequestBody(matchingJsonPath("$.dst", equalTo("+14150000001")))
            .withRequestBody(matchingJsonPath("$.text", equalTo("hello"))));
    }

    @Test
    void failsOnNon202(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/v1/Account/.*/Message/"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"error": "The specified destination is invalid."}
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .authId(Property.ofValue("MAXXXXXXXXXXXXXXXXXX"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+14150000002"))
            .to(Property.ofValue("invalid"))
            .body(Property.ofValue("test"))
            .build();

        assertThrows(RuntimeException.class, () -> task.run(runContext));
    }

    @SuperBuilder
    static class TestSend extends Send {
        private final String base;

        @Override
        protected String baseUrl() {
            return base;
        }
    }
}
