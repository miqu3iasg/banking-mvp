package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class ApiKeyException extends BusinessException {

    public ApiKeyException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public ApiKeyException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }
}
