package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.FaultCode;

public class RegistrationException extends BusinessException {

    public RegistrationException(FaultCode faultCode) {
        super(faultCode.getDefaultMessage(), faultCode);
    }

    public RegistrationException(String message, FaultCode faultCode) {
        super(message, faultCode);
    }
}
