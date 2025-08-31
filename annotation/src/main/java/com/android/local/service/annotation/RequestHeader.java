package com.android.local.service.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface RequestHeader {
    String message() default "@使用RequestHeader注解的参数类型必须为Map<String,String>";
}
