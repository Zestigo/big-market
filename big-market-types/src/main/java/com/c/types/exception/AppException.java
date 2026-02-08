package com.c.types.exception;

import com.c.types.enums.ResponseCode;
import lombok.Getter;

/**
 * 应用领域通用异常
 * 职责：统一业务异常抛出，支持错误码映射与堆栈追踪。
 *
 * @author cyh
 * @date 2026/02/05
 */
@Getter
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private final String code;

    /** 异常详细信息 (对应 RuntimeException 的 message) */
    private final String info;

    /**
     * 基于响应枚举构造业务异常
     */
    public AppException(ResponseCode responseCode) {
        super(responseCode.getInfo());
        this.code = responseCode.getCode();
        this.info = responseCode.getInfo();
    }

    /**
     * 基于响应枚举和自定义描述构造（支持覆盖枚举默认描述）
     */
    public AppException(ResponseCode responseCode, String message) {
        super(message);
        this.code = responseCode.getCode();
        this.info = message;
    }

    /**
     * 基于响应枚举和异常原因构造（用于保留原始堆栈）
     */
    public AppException(ResponseCode responseCode, Throwable cause) {
        super(responseCode.getInfo(), cause);
        this.code = responseCode.getCode();
        this.info = responseCode.getInfo();
    }

    /**
     * 显式指定错误码和错误信息构造
     */
    public AppException(String code, String message) {
        super(message);
        this.code = code;
        this.info = message;
    }

    /**
     * 最全构造函数：自定义错误码、描述以及原始堆栈
     */
    public AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.info = message;
    }

}