package com.android.local.service.processor.helper;

import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSName;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeArgument;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueArgument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * KSP 声明/类型相关的小工具，负责从 KSP 模型里取出注解值、类型名等。
 * <p>
 * 约定：注解的"身份"一律用 <b>全限定名</b> 比较，不允许用简单名（shortName）比较，
 * 否则任意同名第三方注解都会被误认成本框架的注解，且编译期不会报错。
 */
public final class KspUtils {

    /**
     * 类型身份相关的全限定名常量，集中管理，避免散落在各处
     */
    public static final String KOTLIN_STRING = "kotlin.String";
    public static final String JAVA_STRING = "java.lang.String";
    public static final String KOTLIN_UNIT = "kotlin.Unit";
    public static final String KOTLIN_MAP = "kotlin.collections.Map";
    public static final String KOTLIN_MUTABLE_MAP = "kotlin.collections.MutableMap";
    public static final String JAVA_MAP = "java.util.Map";

    private KspUtils() {
    }

    public static List<KSAnnotation> annotations(KSAnnotated annotated) {
        List<KSAnnotation> result = new ArrayList<>();
        Iterator<KSAnnotation> iterator = annotated.getAnnotations().iterator();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * 按注解的全限定名查找注解。
     *
     * @param qualifiedName 注解类全限定名，例如 com.android.local.service.annotation.Request
     */
    public static KSAnnotation findAnnotation(KSAnnotated annotated, String qualifiedName) {
        for (KSAnnotation annotation : annotations(annotated)) {
            if (qualifiedName.equals(annotationQualifiedName(annotation))) {
                return annotation;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(KSAnnotated annotated, String qualifiedName) {
        return findAnnotation(annotated, qualifiedName) != null;
    }

    /**
     * 取注解的全限定名。依赖库中已编译的注解也能解析出全限定名。
     */
    public static String annotationQualifiedName(KSAnnotation annotation) {
        if (annotation == null) {
            return null;
        }
        KSTypeReference annotationType = annotation.getAnnotationType();
        if (annotationType == null) {
            return null;
        }
        return qualifiedNameOf(annotationType.resolve().getDeclaration());
    }

    /**
     * 读取注解属性值。
     *
     * @param qualifiedName 注解类全限定名
     * @param argumentName  属性名：value / port / message ...
     */
    public static Object annotationValue(KSAnnotated annotated, String qualifiedName, String argumentName) {
        KSAnnotation annotation = findAnnotation(annotated, qualifiedName);
        if (annotation == null) {
            return null;
        }
        return valueOf(annotation, argumentName);
    }

    /**
     * 直接从已经拿到的注解实例读取属性值，避免重复查找
     */
    public static Object valueOf(KSAnnotation annotation, String argumentName) {
        if (annotation == null) {
            return null;
        }
        for (KSValueArgument argument : annotation.getArguments()) {
            if (argumentName.equals(nameOf(argument))) {
                return argument.getValue();
            }
        }
        // 未显式写出的属性，回退到注解类上声明的默认值
        for (KSValueArgument argument : annotation.getDefaultArguments()) {
            if (argumentName.equals(nameOf(argument))) {
                return argument.getValue();
            }
        }
        return null;
    }

    public static String annotationString(KSAnnotated annotated, String qualifiedName, String argumentName) {
        Object value = annotationValue(annotated, qualifiedName, argumentName);
        return value == null ? null : String.valueOf(value);
    }

    public static int annotationInt(KSAnnotated annotated, String qualifiedName, String argumentName, int defaultValue) {
        Object value = annotationValue(annotated, qualifiedName, argumentName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 忽略非法数字，返回默认值
            }
        }
        return defaultValue;
    }

    private static String nameOf(KSValueArgument argument) {
        KSName name = argument.getName();
        return name == null ? null : name.asString();
    }

    /**
     * 类型是否为指定全限定名的类型之一
     */
    public static boolean isTypeOneOf(KSType type, String... qualifiedNames) {
        if (type == null) {
            return false;
        }
        String qualifiedName = qualifiedNameOf(type.getDeclaration());
        if (qualifiedName == null) {
            return false;
        }
        for (String candidate : qualifiedNames) {
            if (candidate.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为可当作 String 使用的类型（kotlin.String / java.lang.String）
     */
    public static boolean isStringType(KSType type) {
        return isTypeOneOf(type, KOTLIN_STRING, JAVA_STRING);
    }

    /**
     * 是否为可当作 Map 使用的类型（kotlin.collections.Map / MutableMap / java.util.Map）
     */
    public static boolean isMapType(KSType type) {
        return isTypeOneOf(type, KOTLIN_MAP, KOTLIN_MUTABLE_MAP, JAVA_MAP);
    }
    /**
     * KSP 类型转成便于做字符串判断的类型描述，例如：
     * kotlin.String、kotlin.Int、kotlin.collections.List&lt;kotlin.String&gt;
     */
    public static String typeString(KSType type) {
        if (type == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        KSDeclaration declaration = type.getDeclaration();
        KSName qualifiedName = declaration.getQualifiedName();
        builder.append(qualifiedName != null ? qualifiedName.asString() : declaration.getSimpleName().asString());
        if (!type.getArguments().isEmpty()) {
            builder.append('<');
            List<KSTypeArgument> arguments = type.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                KSTypeArgument argument = arguments.get(i);
                if (argument.getType() == null) {
                    builder.append('*');
                } else {
                    builder.append(typeString(argument.getType().resolve()));
                }
            }
            builder.append('>');
        }
        if (type.isMarkedNullable()) {
            builder.append('?');
        }
        return builder.toString();
    }

    public static String qualifiedNameOf(KSDeclaration declaration) {
        if (declaration == null) {
            return null;
        }
        KSName qualifiedName = declaration.getQualifiedName();
        return qualifiedName == null ? null : qualifiedName.asString();
    }

    public static String packageNameOf(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        return index > 0 ? qualifiedName.substring(0, index) : "";
    }
}
