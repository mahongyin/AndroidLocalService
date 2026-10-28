package com.android.local.service.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**它修饰的方法 返回值须File类型。 [私有目录无需权限]、[外部目录需要权限 甚至R需要MANAGE_EXTERNAL_STORAGE权限]
 * value=请求地址的路径
 * return 目录下的页面文件路径
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface PageFile {
    String value();
}
