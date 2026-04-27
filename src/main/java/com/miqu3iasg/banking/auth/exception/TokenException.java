package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class TokenException extends BusinessException {

    public TokenException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public TokenException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }

    public TokenException(FaultCode faultCode, Throwable cause) {
        super(faultCode.getDefaultMessage(), faultCode, cause);
    }
}
