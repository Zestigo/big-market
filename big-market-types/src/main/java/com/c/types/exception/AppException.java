package com.c.types.exception;

import com.c.types.enums.ResponseCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用领域通用异常
 *
 * @author cyh
 * @date 2026/02/05
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    /**
     * 基于响应枚举构造业务异常
     *
     * @param responseCode 响应码枚举对象
     */
    public AppException(ResponseCode responseCode) {
        this.code = responseCode.getCode();
        this.info = responseCode.getInfo();
    }

    /**
     * 基于响应枚举和异常原因构造业务异常
     *
     * @param responseCode 响应码枚举对象
     * @param cause        底层异常原因
     */
    public AppException(ResponseCode responseCode, Throwable cause) {
        this.code = responseCode.getCode();
        this.info = responseCode.getInfo();
        super.initCause(cause);
    }

    /**
     * 显式指定错误码和错误信息构造异常
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public AppException(String code, String message) {
        this.code = code;
        this.info = message;
    }
}