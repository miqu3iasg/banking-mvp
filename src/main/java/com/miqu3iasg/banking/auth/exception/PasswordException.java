package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class PasswordException extends BusinessException {

    public PasswordException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public PasswordException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }
}
