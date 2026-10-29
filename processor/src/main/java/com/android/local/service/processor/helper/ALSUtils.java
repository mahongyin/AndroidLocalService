package com.android.local.service.processor.helper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 参数类型 → 生成代码片段。
 * <p>
 * 早期实现用 {@code paramType.toString().toLowerCase().contains("int")} 这类子串判断，
 * 会把 {@code java.io.PrintStream}（含 "int"）、{@code StringBuilder}（含 "string"）
 * 之类的类型误判。这里改为基于 {@link TypeKind} 与擦除类型比较的真实类型分派。
 */
public class ALSUtils {

    private static final String JAVA_LANG_STRING = "java.lang.String";
    private static final String JAVA_LANG_INTEGER = "java.lang.Integer";
    private static final String JAVA_LANG_LONG = "java.lang.Long";
    private static final String JAVA_LANG_SHORT = "java.lang.Short";
    private static final String JAVA_LANG_BYTE = "java.lang.Byte";
    private static final String JAVA_LANG_FLOAT = "java.lang.Float";
    private static final String JAVA_LANG_DOUBLE = "java.lang.Double";
    private static final String JAVA_LANG_BOOLEAN = "java.lang.Boolean";

    private final Types types;
    private final Elements elements;

    public ALSUtils(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    /**
     * 生成把 String 形参转换成目标类型实参的表达式。
     * 泛型类型、自定义类型、CharSequence 等直接透传原变量。
     */
    public String getParamStatementByParamType(String paramKey, TypeMirror paramType) {
        switch (paramType.getKind()) {
            case INT:
                return boxed(paramKey, "Integer");
            case LONG:
                return boxed(paramKey, "Long");
            case SHORT:
                return boxed(paramKey, "Short");
            case BYTE:
                return boxed(paramKey, "Byte");
            case FLOAT:
                return boxed(paramKey, "Float");
            case DOUBLE:
                return boxed(paramKey, "Double");
            case BOOLEAN:
                return boxed(paramKey, "Boolean");
            default:
                break;
        }
        if (isSameErasure(paramType, JAVA_LANG_INTEGER)) {
            return boxed(paramKey, "Integer");
        }
        if (isSameErasure(paramType, JAVA_LANG_LONG)) {
            return boxed(paramKey, "Long");
        }
        if (isSameErasure(paramType, JAVA_LANG_SHORT)) {
            return boxed(paramKey, "Short");
        }
        if (isSameErasure(paramType, JAVA_LANG_BYTE)) {
            return boxed(paramKey, "Byte");
        }
        if (isSameErasure(paramType, JAVA_LANG_FLOAT)) {
            return boxed(paramKey, "Float");
        }
        if (isSameErasure(paramType, JAVA_LANG_DOUBLE)) {
            return boxed(paramKey, "Double");
        }
        if (isSameErasure(paramType, JAVA_LANG_BOOLEAN)) {
            return boxed(paramKey, "Boolean");
        }
        if (isSameErasure(paramType, JAVA_LANG_STRING)) {
            return "String.valueOf(" + paramKey + ")";
        }
        // 其它类型（List/Map 等泛型、自定义类型、CharSequence）：直接传原变量
        return paramKey;
    }

    /**
     * 参数缺失时使用的默认字符串字面值。该值会被赋给 String 局部变量，因此必须是字符串字面量。
     */
    public String getDefaultValueByParamTypeWhenNull(String paramKey, TypeMirror paramType) {
        switch (paramType.getKind()) {
            case INT:
            case LONG:
            case SHORT:
            case BYTE:
            case FLOAT:
            case DOUBLE:
                return "0";
            case BOOLEAN:
                return "false";
            default:
                break;
        }
        if (isSameErasure(paramType, JAVA_LANG_INTEGER)
                || isSameErasure(paramType, JAVA_LANG_LONG)
                || isSameErasure(paramType, JAVA_LANG_SHORT)
                || isSameErasure(paramType, JAVA_LANG_BYTE)
                || isSameErasure(paramType, JAVA_LANG_FLOAT)
                || isSameErasure(paramType, JAVA_LANG_DOUBLE)) {
            return "0";
        }
        if (isSameErasure(paramType, JAVA_LANG_BOOLEAN)) {
            return "false";
        }
        // String 以及其它类型统一给空串
        return "";
    }

    private static String boxed(String paramKey, String boxedType) {
        return boxedType + ".valueOf(" + paramKey + ")";
    }

    private boolean isSameErasure(TypeMirror type, String qualifiedName) {
        if (type == null) {
            return false;
        }
        TypeElement target = elements.getTypeElement(qualifiedName);
        if (target == null) {
            return false;
        }
        try {
            return types.isSameType(types.erasure(type), types.erasure(target.asType()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
