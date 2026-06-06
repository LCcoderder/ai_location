package com.example.ai_springboot.common;


import lombok.Data;

/**
 * 全局统一的 API 返回数据封装类
 */
@Data
public class Result<T> {
    private Integer code; // 状态码：200 成功，400 失败，500 错误等
    private String msg;   // 提示信息
    private T data;       // 具体的数据内容

    // 成功时的快捷方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    public static Result<?> success() {
        return success(null);
    }

    // 失败时的快捷方法
    public static Result<?> error(Integer code, String msg) {
        Result<?> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static Result<?> error(String msg) {
        return error(400, msg);
    }
}