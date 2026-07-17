package io.kestra.plugin.plivo.message;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;

import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Send an SMS via the Plivo Messages API",
    description = """
        Posts a message to the Plivo Messages API using Auth ID and Auth Token for basic authentication.
        Returns the message UUIDs and API ID from the API response.
        See the <a href="https://www.plivo.com/docs/messaging/api/messages">Plivo documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send an SMS on a failed flow execution.",
            full = true,
            code = """
                id: sms_on_failure
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.plivo.message.Send
                    authId: "{{ secret('PLIVO_AUTH_ID') }}"
                    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
                    from: "{{ secret('PLIVO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Flow {{ flow.id }} failed on execution {{ execution.id }}."
                """
        ),
        @Example(
            title = "Send an SMS message.",
            full = true,
            code = """
                id: send_sms
                namespace: company.team

                tasks:
                  - id: send_sms
                    type: io.kestra.plugin.plivo.message.Send
                    authId: "{{ secret('PLIVO_AUTH_ID') }}"
                    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
                    from: "{{ secret('PLIVO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Hello from Kestra."
                """
        ),
    }
)
public class Send extends AbstractMessageSend {
}
