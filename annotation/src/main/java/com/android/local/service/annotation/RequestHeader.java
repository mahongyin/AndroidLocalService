package com.android.local.service.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接收请求头的参数。key都是String小写
 * <p>
 * 参数类型可以是 Map / MutableMap / 原始类型 Map；
 * 也可以是可用 Map 拷贝构造的具体子类（HashMap、ConcurrentHashMap、TreeMap、LinkedHashMap 等），
 * 此时生成代码会写成 {@code new HashMap<String, String>(headersMap)}。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface RequestHeader {
    String message() default "@使用RequestHeader注解的参数类型必须为Map<String,String>，或可用 Map 拷贝构造的具体子类（如HashMap、ConcurrentHashMap、TreeMap）";
}
