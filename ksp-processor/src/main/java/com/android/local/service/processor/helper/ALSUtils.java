package com.android.local.service.processor.helper;

import com.google.devtools.ksp.symbol.KSType;

/**
 * 参数类型 → 生成代码片段（KSP 版）。
 * <p>
 * 早期实现把类型转成字符串后用 {@code contains("int")} 判断，
 * 会把 {@code java.io.PrintStream}（含 "int"）、{@code StringBuilder}（含 "string"）误判。
 * 这里改为按 {@link KSType} 声明的全限定名做真实类型分派。
 */
public final class ALSUtils {

    private ALSUtils() {
    }

    /**
     * 生成把 String 形参转换成目标类型实参的表达式。
     * 泛型类型、自定义类型、CharSequence 等直接透传原变量。
     */
    public static String getParamStatementByParamType(String paramKey, KSType paramType) {
        String qualifiedName = KspUtils.qualifiedNameOf(paramType.getDeclaration());
        if (isInteger(qualifiedName)) {
            return "Integer.valueOf(" + paramKey + ")";
        }
        if (isLong(qualifiedName)) {
            return "Long.valueOf(" + paramKey + ")";
        }
        if (isShort(qualifiedName)) {
            return "Short.valueOf(" + paramKey + ")";
        }
        if (isByte(qualifiedName)) {
            return "Byte.valueOf(" + paramKey + ")";
        }
        if (isFloat(qualifiedName)) {
            return "Float.valueOf(" + paramKey + ")";
        }
        if (isDouble(qualifiedName)) {
            return "Double.valueOf(" + paramKey + ")";
        }
        if (isBoolean(qualifiedName)) {
            return "Boolean.valueOf(" + paramKey + ")";
        }
        if (isString(qualifiedName)) {
            return "String.valueOf(" + paramKey + ")";
        }
        // 其它类型（List/Map 等泛型、自定义类型、CharSequence）：直接传原变量
        return paramKey;
    }

    /**
     * 参数缺失时使用的默认字符串字面值。该值会被赋给 String 局部变量，因此必须是字符串字面量。
     */
    public static String getDefaultValueByParamTypeWhenNull(String paramKey, KSType paramType) {
        String qualifiedName = KspUtils.qualifiedNameOf(paramType.getDeclaration());
        if (isInteger(qualifiedName) || isLong(qualifiedName) || isShort(qualifiedName)
                || isByte(qualifiedName) || isFloat(qualifiedName) || isDouble(qualifiedName)) {
            return "0";
        }
        if (isBoolean(qualifiedName)) {
            return "false";
        }
        // String 以及其它类型统一给空串
        return "";
    }

    /**
     * 是否是可被 {@code Xxx.valueOf(param)} 转换的标量类型（含装箱类型）
     */
    public static boolean isConvertibleScalar(KSType paramType) {
        String qualifiedName = KspUtils.qualifiedNameOf(paramType.getDeclaration());
        return isInteger(qualifiedName) || isLong(qualifiedName) || isShort(qualifiedName)
                || isByte(qualifiedName) || isFloat(qualifiedName) || isDouble(qualifiedName)
                || isBoolean(qualifiedName);
    }

    private static boolean isInteger(String name) {
        return "kotlin.Int".equals(name) || "java.lang.Integer".equals(name);
    }

    private static boolean isLong(String name) {
        return "kotlin.Long".equals(name) || "java.lang.Long".equals(name);
    }

    private static boolean isShort(String name) {
        return "kotlin.Short".equals(name) || "java.lang.Short".equals(name);
    }

    private static boolean isByte(String name) {
        return "kotlin.Byte".equals(name) || "java.lang.Byte".equals(name);
    }

    private static boolean isFloat(String name) {
        return "kotlin.Float".equals(name) || "java.lang.Float".equals(name);
    }

    private static boolean isDouble(String name) {
        return "kotlin.Double".equals(name) || "java.lang.Double".equals(name);
    }

    private static boolean isBoolean(String name) {
        return "kotlin.Boolean".equals(name) || "java.lang.Boolean".equals(name);
    }

    private static boolean isString(String name) {
        return KspUtils.KOTLIN_STRING.equals(name) || KspUtils.JAVA_STRING.equals(name);
    }
}
