package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class MfaException extends BusinessException {

    public MfaException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public MfaException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }
}
