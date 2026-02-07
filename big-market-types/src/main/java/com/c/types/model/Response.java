package com.c.types.model;

import com.c.types.enums.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应体对象
 *
 * @param <T> 泛型数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = -2474596551402989242L;

    /**
     * 响应码
     */
    private String code;

    /**
     * 响应描述
     */
    private String info;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 快捷构造：执行成功（无数据）
     *
     * @param <T> 数据泛型
     * @return Response 实例
     */
    public static <T> Response<T> success() {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).build();
    }

    /**
     * 快捷构造：执行成功（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据泛型
     * @return Response 实例
     */
    public static <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                       .data(data).build();
    }

    /**
     * 快捷构造：通过枚举构造错误响应
     *
     * @param responseCode 响应码枚举
     * @param <T>          数据泛型
     * @return Response 实例
     */
    public static <T> Response<T> fail(ResponseCode responseCode) {
        return Response.<T>builder().code(responseCode.getCode()).info(responseCode.getInfo()).build();
    }

    /**
     * 快捷构造：自定义错误描述
     *
     * @param code 错误码
     * @param info 错误描述
     * @param <T>  数据泛型
     * @return Response 实例
     */
    public static <T> Response<T> fail(String code, String info) {
        return Response.<T>builder().code(code).info(info).build();
    }
}