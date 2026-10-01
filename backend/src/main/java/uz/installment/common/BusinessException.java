package uz.installment.common;

import lombok.Getter;

/** Biznes qoidasi buzilganda (422). code — frontend tarjima kaliti sifatida ishlatadi. */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
