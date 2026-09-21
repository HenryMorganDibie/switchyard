package com.henrymorgandibie.switchyard.network.management;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.exception.RequiredFieldMissingException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipantStatusRegistry;
import com.henrymorgandibie.switchyard.routing.domain.ParticipantStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkManagementHandlerTest {

    private static final String INSTITUTION_ID = "12345";

    @Test
    void signOnWithAnInstitutionIdMarksThatInstitutionUpAndApproves() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-1", networkManagementRequest("000001", "001", INSTITUTION_ID));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.mti()).isEqualTo(Mti.NETWORK_MANAGEMENT_RESPONSE);
        assertThat(unpacked.stringField(39)).isEqualTo("00");
        assertThat(registry.statusOf(INSTITUTION_ID)).isEqualTo(ParticipantStatus.UP);
    }

    @Test
    void signOffWithAnInstitutionIdMarksThatInstitutionDownAndApproves() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        registry.markUp(INSTITUTION_ID);
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-2", networkManagementRequest("000002", "002", INSTITUTION_ID));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("00");
        assertThat(registry.statusOf(INSTITUTION_ID)).isEqualTo(ParticipantStatus.DOWN);
    }

    @Test
    void signOnWithoutAnInstitutionIdIsRejectedAsAFormatError() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-3", networkManagementRequest("000003", "001", null));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("30");
    }

    @Test
    void signOffWithoutAnInstitutionIdIsRejectedAsAFormatError() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-4", networkManagementRequest("000004", "002", null));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("30");
    }

    @Test
    void echoTestApprovesWithoutTouchingAnyInstitutionsStatus() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-5", networkManagementRequest("000005", "301", null));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("00");
        assertThat(registry.statusOf(INSTITUTION_ID)).isEqualTo(ParticipantStatus.DOWN);
    }

    @Test
    void echoTestIgnoresAnInstitutionIdIfOnePassed() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-6", networkManagementRequest("000006", "301", INSTITUTION_ID));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("00");
        assertThat(registry.statusOf(INSTITUTION_ID))
                .as("echo must not be able to change an institution's tracked status")
                .isEqualTo(ParticipantStatus.DOWN);
    }

    @Test
    void unrecognizedFunctionCodeIsDeclinedAsInvalidTransaction() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-7", networkManagementRequest("000007", "999", null));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(39)).isEqualTo("12");
    }

    @Test
    void responseEchoesTheFunctionCodeAndStan() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] response = handler.handle("corr-8", networkManagementRequest("000008", "301", null));
        IsoMessage unpacked = IsoMessageUnpacker.unpack(response);

        assertThat(unpacked.stringField(70)).isEqualTo("301");
        assertThat(unpacked.stringField(11)).isEqualTo("000008");
    }

    @Test
    void missingFunctionCodeFailsValidationBeforeAnyStatusChange() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();
        NetworkManagementHandler handler = new NetworkManagementHandler(registry);

        byte[] malformed = IsoMessagePacker.pack(IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910120700")
                .numeric(11, "000009")
                .build());

        assertThatThrownBy(() -> handler.handle("corr-9", malformed))
                .isInstanceOf(RequiredFieldMissingException.class);
    }

    private static byte[] networkManagementRequest(String stan, String functionCode, String institutionId) {
        IsoMessage.Builder builder = IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910120700")
                .numeric(11, stan)
                .numeric(70, functionCode);
        if (institutionId != null) {
            builder.numeric(32, institutionId);
        }
        return IsoMessagePacker.pack(builder.build());
    }
}
