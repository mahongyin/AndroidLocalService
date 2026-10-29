package com.android.local.service.processor.helper;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSName;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeArgument;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueParameter;
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
    private static final String DEFAULT_REQUEST_HEADER_MESSAGE = "@使用RequestHeader注解的参数类型必须为Map<String,String>";

    private final CodeGenerator codeGenerator;
    private final KSPLogger logger;
    private final Map<String, MethodSpec.Builder> methodSpecBuilderCache = new LinkedHashMap<>();
    private final Map<String, TypeSpec.Builder> typeSpecBuilderCache = new LinkedHashMap<>();
    private final Map<String, KSClassDeclaration> classDeclarationCache = new LinkedHashMap<>();

    public ALSProcessorHelper(CodeGenerator codeGenerator, KSPLogger logger) {
        this.codeGenerator = codeGenerator;
        this.logger = logger;
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
            String paramTypeString = KspUtils.typeString(paramType);

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

            if (isHeader) {
                validateHeaderParam(parameter);
                // 直接使用 handleRequest 的 headersMap 形参，无需再声明局部变量
                if (!first) {
                    arguments.add(", ");
                }
                arguments.add("$L", REQUEST_HEADER_NAME);
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
                    .addStatement("$N = $S", paramKey, ALSUtils.getDefaultValueByParamTypeWhenNull(paramKey, paramTypeString))
                    .endControlFlow();

            if (!first) {
                arguments.add(", ");
            }
            arguments.add("$L", ALSUtils.getParamStatementByParamType(paramKey, paramTypeString));
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

    private void validateHeaderParam(KSValueParameter parameter) {
        KSType type = parameter.getType().resolve();
        List<KSTypeArgument> typeArguments = type.getArguments();
        boolean isStringKeyValue = typeArguments.size() == 2
                && isStringTypeArgument(typeArguments.get(0))
                && isStringTypeArgument(typeArguments.get(1));
        if (!KspUtils.isMapType(type) || !isStringKeyValue) {
            String message = KspUtils.annotationString(parameter, ALSAnnotation.REQUEST_HEADER, "message");
            logger.error(message == null ? DEFAULT_REQUEST_HEADER_MESSAGE : message, parameter);
        }
    }

    private boolean isStringTypeArgument(KSTypeArgument typeArgument) {
        if (typeArgument.getType() == null) {
            return false;
        }
        return KspUtils.isStringType(typeArgument.getType().resolve());
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
}
