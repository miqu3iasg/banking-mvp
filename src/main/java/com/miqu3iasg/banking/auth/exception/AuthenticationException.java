package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class AuthenticationException extends BusinessException {

    public AuthenticationException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public AuthenticationException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }

    public AuthenticationException(FaultCode faultCode, Throwable cause) {
        super(faultCode.getDefaultMessage(), faultCode, cause);
    }
}
