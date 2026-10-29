package com.android.local.service.processor.helper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 类型校验工具：用 {@link Types} 做结构化判断，而不是比较 {@code TypeMirror.toString()} 字符串。
 * <p>
 * 之前 kapt 版处理器用
 * {@code type.equals("java.util.Map<java.lang.String,java.lang.String>")} 判断 @RequestHeader 参数，
 * 存在这些问题：
 * <ul>
 *   <li>Java 侧写成原始类型 {@code Map} 时字符串是 {@code java.util.Map}，会被误判为非法；</li>
 *   <li>声明为 {@code HashMap<String,String>} 时字符串不同，报错信息无法说明真正原因；</li>
 *   <li>{@code Map<String, ? extends CharSequence>} 这类可接收 {@code Map<String,String>} 的类型会被误拒；</li>
 *   <li>开启类型注解（type-use annotation）后 {@code toString()} 可能带上注解，字符串直接失配；</li>
 *   <li>本质问题是"字符串是否相等"，而不是"这个形参能否接收 Map&lt;String,String&gt;"。</li>
 * </ul>
 * 这里改为 {@link Types#isAssignable} 判断：只要生成的 {@code headersMap}（Map&lt;String,String&gt;）
 * 能作为实参传给该形参，就认为合法，与 javac 的实际编译检查保持一致。
 */
public final class TypeChecks {

    private static final String JAVA_UTIL_MAP = "java.util.Map";
    private static final String JAVA_LANG_STRING = "java.lang.String";

    /**
     * 生成代码会用 {@code Xxx.valueOf(param)} 转换的标量装箱类型
     */
    private static final String[] BOXED_SCALAR_TYPES = {
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Boolean"
    };

    /**
     * 当形参是 Map 接口（SortedMap / NavigableMap / ConcurrentMap 等）或抽象类型时，
     * 用这些具体实现来构造一个可赋给该形参的 Map。
     * 顺序即优先级。
     */
    private static final String[] HEADER_IMPLEMENTATIONS = {
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentSkipListMap"
    };

    private final Types types;
    private final Elements elements;

    public TypeChecks(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    /**
     * 是否恰好是 String（忽略类型注解/nullability 注解差异）。
     * 用于 @UpFile / @UpJson / @UpXml：这些参数最终由生成代码以 String 实参传入，
     * 必须与 String 类型一致（不接受 CharSequence 等宽类型，避免改变原有语义）。
     */
    public boolean isExactlyString(TypeMirror type) {
        return isSameErasure(type, JAVA_LANG_STRING);
    }

    /**
     * 该形参能否接收 Map&lt;String,String&gt; 实参。
     * 覆盖：Map / MutableMap / 可空 Map / 原始类型 Map / Map&lt;String, ? extends X&gt;，
     * 拒绝 HashMap、Map&lt;String,Object&gt; 等"接收后无法赋值"的类型。
     */
    public boolean acceptsStringMap(TypeMirror type) {
        if (type == null) {
            return false;
        }
        TypeMirror stringMap = stringMapType();
        if (stringMap == null) {
            return false;
        }
        try {
            return types.isAssignable(stringMap, type);
        } catch (IllegalArgumentException e) {
            // 类型解析错误（例如源码本身有编译错误）时按不合法处理
            return false;
        }
    }

    /**
     * 能否为该形参提供 Map&lt;String,String&gt; 实参：
     * 1. 能直接接收（Map / MutableMap / 可空 Map / 原始类型 Map / Map&lt;String, ? extends X&gt;）；
     * 2. 或者是可用 Map 拷贝构造的具体 Map 子类（HashMap / ConcurrentHashMap / TreeMap / LinkedHashMap ...），
     *    生成代码会写成 {@code new HashMap<String,String>(headersMap)}。
     */
    public boolean canProvideStringMap(TypeMirror type) {
        return acceptsStringMap(type) || headerImplementation(type) != null;
    }

    /**
     * 为无法直接接收 {@code Map<String,String>} 的形参挑选一个可构造的具体 Map 实现：
     * <ul>
     *   <li>形参自身是可实例化的具体 Map 子类（HashMap / ConcurrentHashMap / 自定义 Map 类）→ 返回自身；</li>
     *   <li>形参是 Map 接口（SortedMap / NavigableMap / ConcurrentMap ...）→ 返回可赋给它的具体实现；</li>
     *   <li>无法满足（例如元素类型不是 String，或没有可用实现）→ 返回 null。</li>
     * </ul>
     */
    public TypeMirror headerImplementation(TypeMirror type) {
        if (type == null) {
            return null;
        }
        // 1. 形参自身就能用 Map 拷贝构造
        if (canConstructFromStringMap(type)) {
            return type;
        }
        // 2. 形参是接口/抽象类型时，选一个可赋给它的具体实现
        TypeElement stringElement = elements.getTypeElement(JAVA_LANG_STRING);
        if (stringElement == null) {
            return null;
        }
        for (String implementation : HEADER_IMPLEMENTATIONS) {
            TypeElement implementationElement = elements.getTypeElement(implementation);
            if (implementationElement == null) {
                continue;
            }
            TypeMirror candidate;
            try {
                candidate = types.getDeclaredType(implementationElement,
                        stringElement.asType(), stringElement.asType());
            } catch (IllegalArgumentException e) {
                continue;
            }
            try {
                if (types.isAssignable(candidate, type)) {
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // 忽略无法比较的类型
            }
        }
        return null;
    }

    /**
     * 是否是 Map 的子类型（HashMap、ConcurrentHashMap 等为 true；String、List 为 false）
     */
    public boolean isMapSubtype(TypeMirror type) {
        TypeElement mapElement = elements.getTypeElement(JAVA_UTIL_MAP);
        if (mapElement == null || type == null) {
            return false;
        }
        try {
            return types.isAssignable(types.erasure(type), types.erasure(mapElement.asType()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 是否是可实例化、且存在"接收 Map 的公有构造函数"的具体 Map 子类。
     * 这类形参可以用 {@code new 类型(headersMap)} 拷贝构造出来，因此也应当被接受。
     */
    public boolean canConstructFromStringMap(TypeMirror type) {
        if (!isMapSubtype(type)) {
            return false;
        }
        Element element = types.asElement(type);
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        // 接口 / 抽象类 / 枚举 无法 new
        if (typeElement.getKind() != ElementKind.CLASS) {
            return false;
        }
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        TypeMirror stringMap = stringMapType();
        if (stringMap == null) {
            return false;
        }
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) enclosed;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (constructor.getParameters().size() != 1) {
                continue;
            }
            TypeMirror parameterType = constructor.getParameters().get(0).asType();
            try {
                // 构造函数形参一般是 Map<? extends K, ? extends V>，比较擦除类型即可
                if (types.isAssignable(stringMap, types.erasure(parameterType))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 忽略无法比较的类型
            }
        }
        return false;
    }

    /**
     * 该类型能否作为普通请求参数（非 @RequestHeader / 非上传参数）。
     * <p>
     * 生成代码会把请求参数读成 String，再按类型转换，因此形参类型必须满足其一：
     * <ul>
     *   <li>标量：int/long/short/byte/float/double/boolean 及其装箱类型（会用 Xxx.valueOf 转换）；</li>
     *   <li>String 的父类型：String、CharSequence、Object、Serializable 等（String 可直接赋值）。</li>
     * </ul>
     * {@code List<String>}、数组、{@code Date}、自定义实体等类型无法从 String 得到，视为不支持。
     */
    public boolean isSupportedRequestParam(TypeMirror type) {
        return isConvertibleScalar(type) || acceptsString(type);
    }

    /**
     * 是否是可被 {@code Xxx.valueOf(param)} 转换的标量类型
     */
    public boolean isConvertibleScalar(TypeMirror type) {
        if (type == null) {
            return false;
        }
        switch (type.getKind()) {
            case INT:
            case LONG:
            case SHORT:
            case BYTE:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                return true;
            default:
                break;
        }
        for (String boxed : BOXED_SCALAR_TYPES) {
            if (isSameErasure(type, boxed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该形参能否接收 String 实参（用于判断能否直接把 String 变量透传给形参）
     */
    public boolean acceptsString(TypeMirror type) {
        TypeElement stringElement = elements.getTypeElement(JAVA_LANG_STRING);
        if (stringElement == null || type == null) {
            return false;
        }
        try {
            return types.isAssignable(stringElement.asType(), type);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private TypeMirror stringMapType() {
        TypeElement mapElement = elements.getTypeElement(JAVA_UTIL_MAP);
        TypeElement stringElement = elements.getTypeElement(JAVA_LANG_STRING);
        if (mapElement == null || stringElement == null) {
            return null;
        }
        return types.getDeclaredType(mapElement, stringElement.asType(), stringElement.asType());
    }

    private boolean isSameErasure(TypeMirror type, String qualifiedName) {
        TypeElement target = elements.getTypeElement(qualifiedName);
        if (target == null || type == null) {
            return false;
        }
        try {
            return types.isSameType(types.erasure(type), types.erasure(target.asType()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
