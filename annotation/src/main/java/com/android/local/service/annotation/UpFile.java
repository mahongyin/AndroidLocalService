package com.android.local.service.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标明参数(String)是上传文件用的字段, 可对应多文件
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface UpFile {
    String message() default "@使用UpFile注解的参数类型必须为String";
}