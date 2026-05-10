package com.miqu3iasg.banking.shared.observability;

public final class TracingAttributes {

    private TracingAttributes() { }

    public static final String TXID = "banking.txid";
    public static final String CNPJ = "banking.cnpj";
    public static final String PIX_KEY = "banking.pix.key";
    public static final String OPERATION = "banking.operation";
    public static final String CHARGE_ID = "banking.charge.id";
}
