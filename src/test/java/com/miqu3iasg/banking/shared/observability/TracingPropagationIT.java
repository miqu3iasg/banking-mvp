package com.miqu3iasg.banking.shared.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracingPropagationIT {

    @Test
    void tracingAttributesConstantsAreConsistent() {
        assertThat(TracingAttributes.TXID).isEqualTo("banking.txid");
        assertThat(TracingAttributes.CNPJ).isEqualTo("banking.cnpj");
        assertThat(TracingAttributes.PIX_KEY).isEqualTo("banking.pix.key");
        assertThat(TracingAttributes.OPERATION).isEqualTo("banking.operation");
        assertThat(TracingAttributes.CHARGE_ID).isEqualTo("banking.charge.id");
    }

    @Test
    void observationContextAcceptsHighCardinalityKeyValues() {
        var noopRegistry = ObservationRegistry.create();
        var context = new Observation.Context();

        context.addHighCardinalityKeyValue(
                KeyValue.of(TracingAttributes.OPERATION, "pixCharge")
        );

        var keyValues = context.getHighCardinalityKeyValues();
        assertThat(keyValues).anySatisfy(kv -> {
            assertThat(kv.getKey()).isEqualTo(TracingAttributes.OPERATION);
            assertThat(kv.getValue()).isEqualTo("pixCharge");
        });
    }

    @Test
    void multipleCustomAttributesCanBeAddedToContext() {
        var context = new Observation.Context();

        context.addHighCardinalityKeyValue(
                KeyValue.of(TracingAttributes.OPERATION, "createCharge")
        );
        context.addHighCardinalityKeyValue(
                KeyValue.of(TracingAttributes.TXID, "abc123def")
        );
        context.addHighCardinalityKeyValue(
                KeyValue.of(TracingAttributes.CNPJ, "12345678000190")
        );

        var keyValues = context.getHighCardinalityKeyValues();
        assertThat(keyValues).anySatisfy(kv ->
                assertThat(kv.getKey()).isEqualTo(TracingAttributes.OPERATION));
        assertThat(keyValues).anySatisfy(kv ->
                assertThat(kv.getKey()).isEqualTo(TracingAttributes.TXID));
        assertThat(keyValues).anySatisfy(kv ->
                assertThat(kv.getKey()).isEqualTo(TracingAttributes.CNPJ));
    }

    @Test
    void keyValueConstantsDoNotCollide() {
        assertThat(TracingAttributes.TXID).isNotEqualTo(TracingAttributes.CNPJ);
        assertThat(TracingAttributes.TXID).isNotEqualTo(TracingAttributes.PIX_KEY);
        assertThat(TracingAttributes.OPERATION).isNotEqualTo(TracingAttributes.CHARGE_ID);
        assertThat(TracingAttributes.CNPJ).isNotEqualTo(TracingAttributes.PIX_KEY);
    }
}
