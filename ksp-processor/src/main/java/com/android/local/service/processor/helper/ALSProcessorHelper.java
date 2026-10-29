package com.android.local.service.processor.helper;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSName;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeArgument;
import com.google.devtools.ksp.symbol.KSTypeAlias;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueParameter;
import com.google.devtools.ksp.symbol.Variance;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;

/**
 * KSP 版本的注解处理器实现，负责为带有 @ServicePort / @Request / @Page / @PageFile / @RequestSSE
 * 注解的类生成 ALS_XXX 包装类（生成的仍然是 Java 源码，和 kapt 时代保持一致）。
 */
public class ALSProcessorHelper {

    private static final String AUTO_CREATE_CLASS_PREFIX = "ALS_";

    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_XML = "/xml";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    // 生成方法所用到的名称
    private static final String AFTER_PARAM_NAME = "contentType";
    private static final String FIRST_PARAM_NAME = "action";
    private static final String SECOND_PARAM_NAME = "paramsMap";
    private static final String REQUEST_HEADER_NAME = "headersMap";
    private static final String METHOD_NAME = "handleRequest";
    private static final String METHOD_RETURN_TYPE = "Response";
    private static final String METHOD_RETURN_TYPE_PACKAGE_NAME = "org.nanohttpd.protocols.http.response";
    private static final String SERVICE_PACKAGE_NAME = "com.android.local.service.core.service";
    private static final String SERVICE_CLASS_NAME = "ALSService";
    private static final String SERVICE_FIELD_NAME = "realService";
    private static final String SERVICE_FIELD_NAME_PORT = "port";

    private static final String REQUEST_LISTENER_PACKAGE_NAME = "com.android.local.service.core.i";
    private static final String REQUEST_LISTENER_CLASS_NAME = "RequestListener";
    private static final String REQUEST_LISTENER_METHOD_NAME = "onRequest";
    private static final String I_HTTP_SESSION_PACKAGE_NAME = "org.nanohttpd.protocols.http";
    private static final String I_HTTP_SESSION_CLASS_NAME = "IHTTPSession";
    private static final String I_HTTP_SESSION_NAME = "session";
    private static final String I_SERVICE_PACKAGE_NAME = "com.android.local.service.core.i";
    private static final String I_SERVICE_CLASS_NAME = "IService";

    private static final String OVERRIDE_GET_SERVICE_METHOD_NAME = "getService";
    private static final String OVERRIDE_GET_SERVICE_WITH_PORT_METHOD_NAME = "getServiceByPort";
    private static final String OVERRIDE_GET_SERVICE_PORT_METHOD_NAME = "getServicePort";

    private static final String INIT_METHOD_NAME = "init";

    private static final String DEFAULT_UP_FILE_MESSAGE = "@使用UpFile注解的参数类型必须为String";
    private static final String DEFAULT_UP_JSON_MESSAGE = "@使用UpJson注解的参数类型必须为String";
    private static final String DEFAULT_UP_XML_MESSAGE = "@使用UpXml注解的参数类型必须为String";
    private static final String DEFAULT_REQUEST_HEADER_MESSAGE =
            "@使用RequestHeader注解的参数类型必须为Map<String,String>，或可用 Map 拷贝构造的具体子类（如HashMap、ConcurrentHashMap、TreeMap）";

    /**
     * 形参是 Map 接口/抽象类型时，用这些具体实现来构造一个可赋给该形参的 Map。顺序即优先级。
     */
    private static final String[] HEADER_IMPLEMENTATIONS = {
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentSkipListMap"
    };

    private final CodeGenerator codeGenerator;
    private final KSPLogger logger;
    private final Resolver resolver;
    private final Map<String, MethodSpec.Builder> methodSpecBuilderCache = new LinkedHashMap<>();
    private final Map<String, TypeSpec.Builder> typeSpecBuilderCache = new LinkedHashMap<>();
    private final Map<String, KSClassDeclaration> classDeclarationCache = new LinkedHashMap<>();
    private KSType stringMapType;

    public ALSProcessorHelper(CodeGenerator codeGenerator, KSPLogger logger, Resolver resolver) {
        this.codeGenerator = codeGenerator;
        this.logger = logger;
        this.resolver = resolver;
    }

    private void createTypeSpecBuilder(KSClassDeclaration classDeclaration) {
        String key = KspUtils.qualifiedNameOf(classDeclaration);
        if (key == null || typeSpecBuilderCache.containsKey(key)) {
            return;
        }
        String simpleName = classDeclaration.getSimpleName().asString();
        String className = AUTO_CREATE_CLASS_PREFIX + simpleName;
        String packageName = KspUtils.packageNameOf(key);
        int port = KspUtils.annotationInt(classDeclaration, ALSAnnotation.SERVICE_PORT, "port", 0);

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ClassName.get(packageName, simpleName))
                .addSuperinterface(ClassName.get(I_SERVICE_PACKAGE_NAME, I_SERVICE_CLASS_NAME))
                .addField(FieldSpec.builder(ClassName.get(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME), SERVICE_FIELD_NAME)
                        .addModifiers(Modifier.PRIVATE)
                        .build()
                )
                .addField(FieldSpec.builder(TypeName.INT, SERVICE_FIELD_NAME_PORT)
                        .addModifiers(Modifier.PRIVATE)
                        .initializer("$L", port)
                        .build()
                );
        builder.addMethod(createInitMethod())
                .addMethod(getOverrideGetServiceMethod())
                .addMethod(getOverrideGetServiceByPortMethod())
                .addMethod(getOverrideGetServicePortMethod());
        typeSpecBuilderCache.put(key, builder);
        classDeclarationCache.put(key, classDeclaration);
    }

    private MethodSpec createInitMethod() {
        return MethodSpec.methodBuilder(INIT_METHOD_NAME)
                .addModifiers(Modifier.PRIVATE)
                .beginControlFlow("if ($N == null)", SERVICE_FIELD_NAME)
                .addStatement("this.$N = new $N($N)", SERVICE_FIELD_NAME, SERVICE_CLASS_NAME, SERVICE_FIELD_NAME_PORT)
                .addStatement("this.$N.setRequestListener($L)", SERVICE_FIELD_NAME, innerClass())
                .endControlFlow()
                .build();
    }

    private TypeSpec innerClass() {
        return TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(ClassName.get(REQUEST_LISTENER_PACKAGE_NAME, REQUEST_LISTENER_CLASS_NAME))
                .addMethod(MethodSpec.methodBuilder(REQUEST_LISTENER_METHOD_NAME)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(ClassName.get(I_HTTP_SESSION_PACKAGE_NAME, I_HTTP_SESSION_CLASS_NAME), I_HTTP_SESSION_NAME)
                        .addParameter(String.class, FIRST_PARAM_NAME)
                        .addParameter(String.class, AFTER_PARAM_NAME)
                        .addParameter(Map.class, REQUEST_HEADER_NAME)
                        .addParameter(Map.class, SECOND_PARAM_NAME)
                        .addStatement("return $N($N, $N, $N, $N, $N)", METHOD_NAME, I_HTTP_SESSION_NAME, FIRST_PARAM_NAME, AFTER_PARAM_NAME, REQUEST_HEADER_NAME, SECOND_PARAM_NAME)
                        .returns(ClassName.get(METHOD_RETURN_TYPE_PACKAGE_NAME, METHOD_RETURN_TYPE))
                        .build())
                .build();
    }

    private MethodSpec getOverrideGetServiceMethod() {
        return MethodSpec.methodBuilder(OVERRIDE_GET_SERVICE_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addStatement("$N()", INIT_METHOD_NAME)
                .addStatement("return $N", SERVICE_FIELD_NAME)
                .returns(ClassName.get(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME))
                .build();
    }

    private MethodSpec getOverrideGetServiceByPortMethod() {
        return MethodSpec.methodBuilder(OVERRIDE_GET_SERVICE_WITH_PORT_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.INT, SERVICE_FIELD_NAME_PORT)
                .addStatement("this.$N = $N", SERVICE_FIELD_NAME_PORT, SERVICE_FIELD_NAME_PORT)
                .addStatement("return $N()", OVERRIDE_GET_SERVICE_METHOD_NAME)
                .returns(ClassName.get(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME))
                .build();
    }

    private MethodSpec getOverrideGetServicePortMethod() {
        return MethodSpec.methodBuilder(OVERRIDE_GET_SERVICE_PORT_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addStatement("return $N", SERVICE_FIELD_NAME_PORT)
                .returns(int.class)
                .build();
    }

    private MethodSpec.Builder createOrGetMethodSpecBuilder(KSClassDeclaration classDeclaration) {
        String key = KspUtils.qualifiedNameOf(classDeclaration);
        MethodSpec.Builder builder = methodSpecBuilderCache.get(key);
        if (builder != null) {
            return builder;
        }
        builder = MethodSpec.methodBuilder(METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(METHOD_RETURN_TYPE_PACKAGE_NAME, METHOD_RETURN_TYPE))
                .addParameter(ClassName.get(I_HTTP_SESSION_PACKAGE_NAME, I_HTTP_SESSION_CLASS_NAME), I_HTTP_SESSION_NAME)
                .addParameter(String.class, FIRST_PARAM_NAME)
                .addParameter(String.class, AFTER_PARAM_NAME)
                .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), REQUEST_HEADER_NAME)
                .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), SECOND_PARAM_NAME);

        methodSpecBuilderCache.put(key, builder);
        return builder;
    }

    /**
     * 把所有收集到的类写成 Java 源码。KSP 增量处理要求通过 CodeGenerator 创建文件。
     */
    public void createClassFiles() {
        for (Map.Entry<String, TypeSpec.Builder> entry : typeSpecBuilderCache.entrySet()) {
            String key = entry.getKey();
            TypeSpec.Builder typeSpecBuilder = entry.getValue();
            MethodSpec.Builder methodBuilder = methodSpecBuilderCache.get(key);
            if (methodBuilder != null) {
                methodBuilder.addStatement("return $N.$N()", SERVICE_FIELD_NAME, "errorPath");
                typeSpecBuilder.addMethod(methodBuilder.build());
            }
            KSClassDeclaration classDeclaration = classDeclarationCache.get(key);
            if (classDeclaration == null) {
                continue;
            }
            //自动生成的类放在自己创建类同一个包里边
            String packageName = KspUtils.packageNameOf(key);
            String generatedClassName = AUTO_CREATE_CLASS_PREFIX + classDeclaration.getSimpleName().asString();
            JavaFile javaFile = JavaFile.builder(packageName, typeSpecBuilder.build()).build();

            KSFile containingFile = classDeclaration.getContainingFile();
            Dependencies dependencies = containingFile == null
                    ? new Dependencies(false)
                    : new Dependencies(false, containingFile);
            try (OutputStream outputStream = codeGenerator.createNewFile(
                    dependencies, packageName, generatedClassName, "java")) {
                outputStream.write(javaFile.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.error("生成 " + generatedClassName + " 失败：" + e.getMessage(), classDeclaration);
            }
        }
    }

    public void processService(KSClassDeclaration classDeclaration) {
        createTypeSpecBuilder(classDeclaration);
        createOrGetMethodSpecBuilder(classDeclaration);
    }

    public void processPage(KSFunctionDeclaration function) {
        KSClassDeclaration classDeclaration = parentClassOf(function);
        if (classDeclaration == null) {
            return;
        }
        createTypeSpecBuilder(classDeclaration);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(classDeclaration);

        String methodName = function.getSimpleName().asString();
        String value = KspUtils.annotationString(function, ALSAnnotation.PAGE, "value");
        builder.beginControlFlow("if (action.equals($S))", value);
        builder.addStatement("return $N.assetsSuccess($N())", SERVICE_FIELD_NAME, methodName);
        builder.endControlFlow();
    }

    public void processFile(KSFunctionDeclaration function) {
        KSClassDeclaration classDeclaration = parentClassOf(function);
        if (classDeclaration == null) {
            return;
        }
        createTypeSpecBuilder(classDeclaration);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(classDeclaration);

        String methodName = function.getSimpleName().asString();
        String value = KspUtils.annotationString(function, ALSAnnotation.PAGE_FILE, "value");
        builder.beginControlFlow("if (action.equals($S))", value);
        builder.addStatement("return $N.fileSuccess($N())", SERVICE_FIELD_NAME, methodName);
        builder.endControlFlow();
    }

    public void processSSE(KSFunctionDeclaration function) {
        KSClassDeclaration classDeclaration = parentClassOf(function);
        if (classDeclaration == null) {
            return;
        }
        createTypeSpecBuilder(classDeclaration);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(classDeclaration);

        String methodName = function.getSimpleName().asString();
        String value = KspUtils.annotationString(function, ALSAnnotation.REQUEST_SSE, "value");
        builder.beginControlFlow("if (action.equals($S))", value);
        CodeBlock parameters = createParamStatements(builder, function);
        builder.addStatement("return $N.initSSE($N($L), $N)", SERVICE_FIELD_NAME, methodName, parameters, I_HTTP_SESSION_NAME);
        builder.endControlFlow();
    }

    public void processRequest(KSFunctionDeclaration function) {
        KSClassDeclaration classDeclaration = parentClassOf(function);
        if (classDeclaration == null) {
            return;
        }
        createTypeSpecBuilder(classDeclaration);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(classDeclaration);

        String methodName = function.getSimpleName().asString();
        String value = KspUtils.annotationString(function, ALSAnnotation.REQUEST, "value");
        builder.beginControlFlow("if (action.equals($S))", value);
        CodeBlock parameters = createParamStatements(builder, function);

        //- 添加try catch
        builder.beginControlFlow("try");
        if (isUnitReturnType(function)) { // 没有返回值的
            builder.addStatement("$N($L)", methodName, parameters);
            builder.addStatement("return $N.successEmpty()", SERVICE_FIELD_NAME);
        } else { // 有返回值的
            builder.addStatement("return $N.success($N($L))", SERVICE_FIELD_NAME, methodName, parameters);
        }
        builder.nextControlFlow("catch (Exception e)")
                .addStatement("return $N.customResponse(e)", SERVICE_FIELD_NAME)
                .endControlFlow();
        builder.endControlFlow();
    }

    /**
     * 生成方法参数声明（String xxx = paramsMap.get("xxx") 等），并返回调用用户方法时需要传入的实参
     */
    private CodeBlock createParamStatements(MethodSpec.Builder builder, KSFunctionDeclaration function) {
        CodeBlock.Builder arguments = CodeBlock.builder();
        boolean first = true;
        for (KSValueParameter parameter : function.getParameters()) {
            KSName parameterName = parameter.getName();
            if (parameterName == null) {
                continue;
            }
            String paramKey = parameterName.asString();
            KSType paramType = parameter.getType().resolve();

            boolean isHeader = KspUtils.hasAnnotation(parameter, ALSAnnotation.REQUEST_HEADER);
            boolean isUpFile = KspUtils.hasAnnotation(parameter, ALSAnnotation.UP_FILE);
            boolean isUpJson = KspUtils.hasAnnotation(parameter, ALSAnnotation.UP_JSON);
            boolean isUpXml = KspUtils.hasAnnotation(parameter, ALSAnnotation.UP_XML);

            if (isUpFile) {
                validateStringParam(parameter, ALSAnnotation.UP_FILE, DEFAULT_UP_FILE_MESSAGE);
            }
            if (isUpJson) {
                validateStringParam(parameter, ALSAnnotation.UP_JSON, DEFAULT_UP_JSON_MESSAGE);
            }
            if (isUpXml) {
                validateStringParam(parameter, ALSAnnotation.UP_XML, DEFAULT_UP_XML_MESSAGE);
            }

            // 普通参数：请求参数只能读成 String，类型必须能从 String 转换
            if (!isHeader && !isUpFile && !isUpJson && !isUpXml && !isSupportedRequestParam(paramType)) {
                logger.error(unsupportedParamMessage(paramKey, paramType), parameter);
            }

            if (isHeader) {
                HeaderPlan plan = headerPlan(paramType);
                if (plan.handling == HeaderHandling.INVALID) {
                    String message = KspUtils.annotationString(parameter, ALSAnnotation.REQUEST_HEADER, "message");
                    logger.error(message == null ? DEFAULT_REQUEST_HEADER_MESSAGE : message, parameter);
                }
                if (!first) {
                    arguments.add(", ");
                }
                if (plan.handling == HeaderHandling.CONSTRUCT && plan.implementation != null) {
                    // 具体 Map 子类或接口：泛型不协变，Map<String,String> 不能直接赋给 HashMap/SortedMap，
                    // 需要用拷贝构造生成一个可赋给该形参的实现
                    arguments.add("new $T<>($L)", plan.implementation, REQUEST_HEADER_NAME);
                } else {
                    // Map / MutableMap / 原始类型 Map：直接使用 handleRequest 的 headersMap 形参
                    arguments.add("$L", REQUEST_HEADER_NAME);
                }
                first = false;
                continue;
            }

            builder.addStatement("String $N = $N.get($S)", paramKey, SECOND_PARAM_NAME, paramKey);
            if (isUpJson) {
                builder.beginControlFlow("if ($N.contains($S))", AFTER_PARAM_NAME, APPLICATION_JSON)
                        .addStatement("$N = $N.get($S)", paramKey, SECOND_PARAM_NAME, "json")
                        .endControlFlow();
            }
            if (isUpXml) {
                builder.beginControlFlow("if ($N.contains($S))", AFTER_PARAM_NAME, APPLICATION_XML)
                        .addStatement("$N = $N.get($S)", paramKey, SECOND_PARAM_NAME, "xml")
                        .endControlFlow();
            }
            builder.beginControlFlow("if ($N == null)", paramKey)
                    .addStatement("$N = $S", paramKey, ALSUtils.getDefaultValueByParamTypeWhenNull(paramKey, paramType))
                    .endControlFlow();

            if (!first) {
                arguments.add(", ");
            }
            arguments.add("$L", ALSUtils.getParamStatementByParamType(paramKey, paramType));
            first = false;
        }
        return arguments.build();
    }

    private void validateStringParam(KSValueParameter parameter, String annotationQualifiedName, String defaultMessage) {
        if (!KspUtils.isStringType(parameter.getType().resolve())) {
            String message = KspUtils.annotationString(parameter, annotationQualifiedName, "message");
            logger.error(message == null ? defaultMessage : message, parameter);
        }
    }

    /**
     * 该类型能否作为普通请求参数（非 @RequestHeader / 非上传参数）。
     * <p>
     * 生成代码会把请求参数读成 String 再转换，因此形参类型必须满足其一：
     * 标量（Int/Long/Short/Byte/Float/Double/Boolean 及装箱类型），或 String 的父类型
     * （String、CharSequence、Any 等）。集合、数组、枚举、自定义实体都不支持。
     */
    private boolean isSupportedRequestParam(KSType type) {
        if (ALSUtils.isConvertibleScalar(type)) {
            return true;
        }
        return isAssignable(type, resolver.getBuiltIns().getStringType());
    }

    private String unsupportedParamMessage(String paramName, KSType type) {
        return "@Request 参数「" + paramName + "」的类型 " + type
                + " 无法从请求参数(String)转换。"
                + "支持 Int/Long/Short/Byte/Float/Double/Boolean 及其装箱类型、String 及其父类型（CharSequence、Any 等）；"
                + "集合、数组、枚举、Date、自定义实体等请声明为 String 后自行解析。";
    }

    /**
     * 判断 @RequestHeader 参数的处理方式（与 kapt 版语义保持一致）：
     * <ul>
     *   <li>{@link HeaderHandling#PASS_THROUGH}：Map / MutableMap / java.util.Map / 原始类型 Map / 可赋值的通配类型，
     *       直接传 {@code headersMap}；</li>
     *   <li>{@link HeaderHandling#CONSTRUCT}：HashMap / TreeMap 等具体子类，或 SortedMap / NavigableMap / ConcurrentMap
     *       等接口，生成 {@code new X<>(headersMap)} 拷贝构造；</li>
     *   <li>{@link HeaderHandling#INVALID}：其余类型（例如 Map&lt;String,Object&gt;）。</li>
     * </ul>
     */
    private HeaderPlan headerPlan(KSType paramType) {
        KSType stringMap = stringMapType();
        if (KspUtils.isMapType(paramType)) {
            boolean rawMap = paramType.getArguments().isEmpty();
            if (rawMap || hasStringKeyValueArguments(paramType) || isAssignable(paramType, stringMap)) {
                return HeaderPlan.passThrough();
            }
            return HeaderPlan.invalid();
        }
        if (stringMap == null) {
            return HeaderPlan.invalid();
        }
        // 具体 Map 子类（HashMap / TreeMap / 自定义 Map 类）
        if (isConstructibleItself(paramType)) {
            return HeaderPlan.construct(javaClassNameOf(paramType));
        }
        // Map 接口（SortedMap / NavigableMap / ConcurrentMap 等）：挑一个可赋给它的具体实现
        ClassName implementation = headerImplementation(paramType);
        return implementation != null ? HeaderPlan.construct(implementation) : HeaderPlan.invalid();
    }

    /**
     * 形参自身是否是可 new 出来、且带 Map 拷贝构造的具体 Map 类
     */
    private boolean isConstructibleItself(KSType paramType) {
        KSType mapType = stringMapType();
        if (mapType == null || !isAssignable(mapType, paramType)) {
            // mapType.isAssignableFrom(paramType)：paramType 是 Map 的子类型
            return false;
        }
        KSClassDeclaration classDeclaration = classDeclarationOf(paramType);
        if (classDeclaration == null) {
            return false;
        }
        if (classDeclaration.getClassKind() != ClassKind.CLASS) {
            return false;
        }
        if (classDeclaration.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        return hasMapConstructor(classDeclaration);
    }

    /**
     * 为接口型/抽象型 Map 形参挑选一个可赋给它的具体实现，找不到时返回 null
     */
    private ClassName headerImplementation(KSType paramType) {
        for (String implementationName : HEADER_IMPLEMENTATIONS) {
            KSClassDeclaration declaration = resolver.getClassDeclarationByName(
                    resolver.getKSNameFromString(implementationName));
            if (declaration == null) {
                continue;
            }
            KSType candidate;
            try {
                candidate = declaration.asType(stringTypeArguments());
            } catch (RuntimeException e) {
                continue;
            }
            if (isAssignable(paramType, candidate)) {
                return javaClassNameOf(candidate);
            }
        }
        return null;
    }

    /**
     * paramType 能否接收 candidate（即 candidate 可赋给 paramType）
     */
    private boolean isAssignable(KSType paramType, KSType candidate) {
        if (paramType == null || candidate == null) {
            return false;
        }
        try {
            return paramType.isAssignableFrom(candidate);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean hasStringKeyValueArguments(KSType type) {
        List<KSTypeArgument> arguments = type.getArguments();
        if (arguments.size() != 2) {
            return false;
        }
        KSTypeArgument key = arguments.get(0);
        KSTypeArgument value = arguments.get(1);
        return key.getType() != null && value.getType() != null
                && KspUtils.isStringType(key.getType().resolve())
                && KspUtils.isStringType(value.getType().resolve());
    }

    /**
     * Map&lt;String,String&gt; 的两个 String 类型实参
     */
    private List<KSTypeArgument> stringTypeArguments() {
        KSType stringType = resolver.getBuiltIns().getStringType();
        KSTypeArgument stringArgument = resolver.getTypeArgument(
                resolver.createKSTypeReferenceFromKSType(stringType), Variance.INVARIANT);
        return Arrays.asList(stringArgument, stringArgument);
    }

    /**
     * 取类型对应的类声明，遇到 typealias 时展开到真实类型。
     * <p>
     * Kotlin 的 {@code HashMap} 是 {@code java.util.HashMap} 的 typealias，
     * KSP 给出的声明是 {@link KSTypeAlias} 而不是 {@link KSClassDeclaration}，
     * 不展开就会被误判成"不是 Map 子类"。
     */
    private KSClassDeclaration classDeclarationOf(KSType type) {
        KSDeclaration declaration = type.getDeclaration();
        if (declaration instanceof KSTypeAlias) {
            KSType aliased = ((KSTypeAlias) declaration).getType().resolve();
            return classDeclarationOf(aliased);
        }
        return declaration instanceof KSClassDeclaration ? (KSClassDeclaration) declaration : null;
    }

    /**
     * 是否存在接收 Map 的构造函数。
     * <p>
     * KSP 只直接暴露主构造函数；类库里的 Java 类型可能拿不到构造器信息，
     * 这种情况按"可构造"处理，由 javac 在生成代码后做最终校验（标准 Map 实现都有该构造函数）。
     */
    private boolean hasMapConstructor(KSClassDeclaration classDeclaration) {
        KSType mapType = stringMapType();
        if (mapType == null) {
            return false;
        }
        KSFunctionDeclaration primaryConstructor = classDeclaration.getPrimaryConstructor();
        if (primaryConstructor == null) {
            return true;
        }
        List<KSValueParameter> parameters = primaryConstructor.getParameters();
        if (parameters.size() != 1) {
            return false;
        }
        try {
            return parameters.get(0).getType().resolve().isAssignableFrom(mapType);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 取 KSP 类型在 Java 里对应的类名（不含泛型），用于 {@code new X<>(...)}。
     * <p>
     * 先用 {@link #classDeclarationOf(KSType)} 展开 typealias
     * （Kotlin 的 {@code HashMap} 实际是 {@code java.util.HashMap}），
     * 再对残留的 kotlin.* 名字做 Java 名字映射，避免生成 {@code kotlin.collections.HashMap} 这种 Java 不存在的类名。
     */
    private ClassName javaClassNameOf(KSType type) {
        KSClassDeclaration classDeclaration = classDeclarationOf(type);
        String name = null;
        if (classDeclaration != null) {
            KSName qualifiedName = classDeclaration.getQualifiedName();
            if (qualifiedName != null) {
                String kotlinName = qualifiedName.asString();
                if (kotlinName.startsWith("kotlin.")) {
                    KSName javaName = null;
                    try {
                        javaName = resolver.mapKotlinNameToJava(qualifiedName);
                    } catch (RuntimeException ignored) {
                        // 映射失败时退回原始名字
                    }
                    name = javaName != null ? javaName.asString() : kotlinName;
                } else {
                    name = kotlinName;
                }
            }
        }
        if (name == null) {
            name = "java.util.HashMap";
        }
        int index = name.lastIndexOf('.');
        return index > 0 ? ClassName.get(name.substring(0, index), name.substring(index + 1)) : ClassName.bestGuess(name);
    }

    /**
     * 构造 Map&lt;String,String&gt; 的 KSType，用于可赋值性比较
     */
    private KSType stringMapType() {
        if (stringMapType != null) {
            return stringMapType;
        }
        KSClassDeclaration mapDeclaration = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(KspUtils.KOTLIN_MAP));
        if (mapDeclaration == null) {
            return null;
        }
        stringMapType = mapDeclaration.asType(stringTypeArguments());
        return stringMapType;
    }

    private boolean isUnitReturnType(KSFunctionDeclaration function) {
        KSTypeReference returnType = function.getReturnType();
        if (returnType == null) {
            return true;
        }
        return KspUtils.isTypeOneOf(returnType.resolve(), KspUtils.KOTLIN_UNIT);
    }

    private KSClassDeclaration parentClassOf(KSFunctionDeclaration function) {
        KSDeclaration parent = function.getParentDeclaration();
        return parent instanceof KSClassDeclaration ? (KSClassDeclaration) parent : null;
    }

    /**
     * @RequestHeader 参数的三种处理方式
     */
    private enum HeaderHandling {
        /** 直接传 headersMap */
        PASS_THROUGH,
        /** 用 Map 拷贝构造 new X<>(headersMap) */
        CONSTRUCT,
        /** 不支持的类型 */
        INVALID
    }

    /**
     * @RequestHeader 参数的处理计划：怎么处理 + 用哪个实现类构造
     */
    private static final class HeaderPlan {

        final HeaderHandling handling;
        final ClassName implementation;

        private HeaderPlan(HeaderHandling handling, ClassName implementation) {
            this.handling = handling;
            this.implementation = implementation;
        }

        static HeaderPlan passThrough() {
            return new HeaderPlan(HeaderHandling.PASS_THROUGH, null);
        }

        static HeaderPlan construct(ClassName implementation) {
            return new HeaderPlan(HeaderHandling.CONSTRUCT, implementation);
        }

        static HeaderPlan invalid() {
            return new HeaderPlan(HeaderHandling.INVALID, null);
        }
    }
}
