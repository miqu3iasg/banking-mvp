package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.shared.observability.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    ObservationFilter efiOperationFilter() {
        return context -> {
            String httpUrl = extractHttpUrl(context);
            if (httpUrl == null) {
                return context;
            }

            if (httpUrl.contains("/v2/cob/")) {
                context.addHighCardinalityKeyValue(
                        KeyValue.of(TracingAttributes.OPERATION, extractEfiPixOperation(httpUrl))
                );
                String txid = extractPathSegment(httpUrl, "/v2/cob/");
                if (txid != null) {
                    context.addHighCardinalityKeyValue(
                            KeyValue.of(TracingAttributes.TXID, txid)
                    );
                }
            }

            if (httpUrl.contains("/v2/gn/evp")) {
                context.addHighCardinalityKeyValue(
                        KeyValue.of(TracingAttributes.OPERATION, "evpKeyManagement")
                );
                String key = extractPathSegment(httpUrl, "/v2/gn/evp/");
                if (key != null) {
                    context.addHighCardinalityKeyValue(
                            KeyValue.of(TracingAttributes.PIX_KEY, key)
                    );
                }
            }

            if (httpUrl.contains("/v2/webhook/")) {
                context.addHighCardinalityKeyValue(
                        KeyValue.of(TracingAttributes.OPERATION, "webhookRegistration")
                );
                String key = extractPathSegment(httpUrl, "/v2/webhook/");
                if (key != null) {
                    context.addHighCardinalityKeyValue(
                            KeyValue.of(TracingAttributes.PIX_KEY, key)
                    );
                }
            }

            if (httpUrl.contains("/v1/charge/")) {
                context.addHighCardinalityKeyValue(
                        KeyValue.of(TracingAttributes.OPERATION, extractEfiBoletoOperation(httpUrl))
                );
            }

            if (httpUrl.contains("/cnpj/v1/")) {
                context.addHighCardinalityKeyValue(
                        KeyValue.of(TracingAttributes.OPERATION, "cnpjLookup")
                );
                String cnpj = extractPathSegment(httpUrl, "/cnpj/v1/");
                if (cnpj != null) {
                    context.addHighCardinalityKeyValue(
                            KeyValue.of(TracingAttributes.CNPJ, cnpj)
                    );
                }
            }

            return context;
        };
    }

    private String extractHttpUrl(Observation.Context context) {
        if (context instanceof RequestReplyReceiverContext<?, ?> receiverCtx) {
            var url = receiverHttpUrl(receiverCtx);
            if (url != null) return url;
        }
        var uri = context.get("http.url");
        return uri != null ? uri.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String receiverHttpUrl(RequestReplyReceiverContext<?, ?> receiverCtx) {
        try {
            var carrier = ((RequestReplyReceiverContext<Object, Object>) receiverCtx).getCarrier();
            if (carrier instanceof org.springframework.http.client.ClientHttpRequest request) {
                return request.getURI().toString();
            }
        } catch (ClassCastException ignored) { }
        return null;
    }

    private String extractEfiPixOperation(String url) {
        if (url.contains("/v2/cob/")) {
            return "pixCharge";
        }
        return "efiPix";
    }

    private String extractEfiBoletoOperation(String url) {
        if (url.contains("/v1/charge/one-step")) {
            return "issueBoleto";
        }
        if (url.contains("/v1/charge/")) {
            return "getChargeStatus";
        }
        return "efiBoleto";
    }

    private String extractPathSegment(String url, String prefix) {
        int idx = url.indexOf(prefix);
        if (idx < 0) {
            return null;
        }
        String after = url.substring(idx + prefix.length());
        int queryIdx = after.indexOf('?');
        String segment = queryIdx >= 0 ? after.substring(0, queryIdx) : after;
        int slashIdx = segment.indexOf('/');
        if (slashIdx >= 0) {
            segment = segment.substring(0, slashIdx);
        }
        return segment.isEmpty() ? null : segment;
    }
}
