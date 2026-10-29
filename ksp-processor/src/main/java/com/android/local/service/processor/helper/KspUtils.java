package com.android.local.service.processor.helper;

import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSName;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeArgument;
import com.google.devtools.ksp.symbol.KSValueArgument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * KSP 声明/类型相关的小工具，负责从 KSP 模型里取出注解值、类型名等
 */
public final class KspUtils {

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
     * 只按注解的短名匹配。注解可能来自已编译的依赖（SOURCE 保留级注解在字节码里不可见），
     * 用短名可以避开解析注解类型，最稳妥。
     */
    public static KSAnnotation findAnnotation(KSAnnotated annotated, String shortName) {
        for (KSAnnotation annotation : annotations(annotated)) {
            if (annotation.getShortName().asString().equals(shortName)) {
                return annotation;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(KSAnnotated annotated, String shortName) {
        return findAnnotation(annotated, shortName) != null;
    }

    public static Object annotationValue(KSAnnotated annotated, String shortName, String argumentName) {
        KSAnnotation annotation = findAnnotation(annotated, shortName);
        if (annotation == null) {
            return null;
        }
        for (KSValueArgument argument : annotation.getArguments()) {
            if (argumentName.equals(nameOf(argument))) {
                return argument.getValue();
            }
        }
        for (KSValueArgument argument : annotation.getDefaultArguments()) {
            if (argumentName.equals(nameOf(argument))) {
                return argument.getValue();
            }
        }
        // 单参数注解的简写形式：@Request("xxx")，此时参数名可能为空
        if (!annotation.getArguments().isEmpty()) {
            return annotation.getArguments().get(0).getValue();
        }
        return null;
    }

    public static String annotationString(KSAnnotated annotated, String shortName, String argumentName) {
        Object value = annotationValue(annotated, shortName, argumentName);
        return value == null ? null : String.valueOf(value);
    }

    public static int annotationInt(KSAnnotated annotated, String shortName, String argumentName, int defaultValue) {
        Object value = annotationValue(annotated, shortName, argumentName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return defaultValue;
    }

    private static String nameOf(KSValueArgument argument) {
        KSName name = argument.getName();
        return name == null ? null : name.asString();
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
        KSName qualifiedName = declaration.getQualifiedName();
        return qualifiedName == null ? null : qualifiedName.asString();
    }

    public static String packageNameOf(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        return index > 0 ? qualifiedName.substring(0, index) : "";
    }

    public static String simpleNameOf(String qualifiedName) {
        int index = qualifiedName.lastIndexOf('.');
        return index > 0 ? qualifiedName.substring(index + 1) : qualifiedName;
    }
}
