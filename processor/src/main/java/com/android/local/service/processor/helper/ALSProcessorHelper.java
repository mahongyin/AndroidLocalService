package com.android.local.service.processor.helper;

import com.android.local.service.annotation.Page;
import com.android.local.service.annotation.Request;
import com.android.local.service.annotation.RequestHeader;
import com.android.local.service.annotation.ServicePort;
import com.android.local.service.annotation.UpJson;
import com.android.local.service.annotation.UpXml;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

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

    private static final String I_SERVICE_PACKAGE_NAME = "com.android.local.service.core.i";
    private static final String I_SERVICE_CLASS_NAME = "IService";

    private static final String OVERRIDE_GET_SERVICE_METHOD_NAME = "getService";
    private static final String OVERRIDE_GET_SERVICE_WITH_PORT_METHOD_NAME = "getServiceByPort";
    private static final String OVERRIDE_GET_SERVICE_PORT_METHOD_NAME = "getServicePort";

    private static final String INIT_METHOD_NAME = "init";

    private final ProcessingEnvironment processingEnv;
    private final Map<Name, MethodSpec.Builder> methodSpecBuilderCache = new HashMap<>();
    private final Map<Name, TypeSpec.Builder> typeSpecBuilderCache = new HashMap<>();

    public ALSProcessorHelper(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    private void createTypeSpecBuilder(TypeElement typeElement) {
        Name key = typeElement.getQualifiedName();
        TypeSpec.Builder builder = typeSpecBuilderCache.get(key);
        if (builder == null) {
            String simpleName = typeElement.getSimpleName().toString();
            String className = AUTO_CREATE_CLASS_PREFIX + simpleName;
            String name = key.toString();
            String packageName = name.substring(0, name.lastIndexOf("."));
            ServicePort servicePort = typeElement.getAnnotation(ServicePort.class);
            int port = servicePort.port();
            builder = TypeSpec.classBuilder(className)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .superclass(ClassName.get(packageName, simpleName))
                    .addSuperinterface(ClassName.get(I_SERVICE_PACKAGE_NAME, I_SERVICE_CLASS_NAME))
                    .addField(FieldSpec.builder(ClassName.get(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME), SERVICE_FIELD_NAME)
                            .addModifiers(Modifier.PRIVATE)
                            .build()
                    )
                    .addField(FieldSpec.builder(TypeName.INT, SERVICE_FIELD_NAME_PORT)
                            .addModifiers(Modifier.PRIVATE)
                            .initializer("$N", String.valueOf(port))
                            .build()
                    );
            builder.addMethod(createInitMethod())
                    .addMethod(getOverrideGetServiceMethod())
                    .addMethod(getOverrideGetServiceByPortMethod())
                    .addMethod(getOverrideGetServicePortMethod());
            typeSpecBuilderCache.put(key, builder);
        }
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
                        .addParameter(String.class, FIRST_PARAM_NAME)
                        .addParameter(String.class, AFTER_PARAM_NAME)
                        .addParameter(Map.class, REQUEST_HEADER_NAME)
                        .addParameter(Map.class, SECOND_PARAM_NAME)
                        /*.addStatement("return $N($N, $N)", METHOD_NAME, FIRST_PARAM_NAME, SECOND_PARAM_NAME)*/
                        .addStatement("return $N($N, $N, $N, $N)", METHOD_NAME, FIRST_PARAM_NAME, AFTER_PARAM_NAME, REQUEST_HEADER_NAME, SECOND_PARAM_NAME)
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

    /**
     * 创建下边的方法
     * <p>
     * //public Response handleRequest(String action, Map paramsMap) {
     * public Response handleRequest(String action, String contentType, Map<String, String> headersMap, Map<String, String> paramsMap) {
     * Map<String, String> tempParamsMap = paramsMap;
     * }
     */
    private MethodSpec.Builder createOrGetMethodSpecBuilder(TypeElement typeElement) {
        Name key = typeElement.getQualifiedName();
        MethodSpec.Builder builder = methodSpecBuilderCache.get(key);
        if (builder != null) return builder;
        builder = MethodSpec.methodBuilder(METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(METHOD_RETURN_TYPE_PACKAGE_NAME, METHOD_RETURN_TYPE))
                .addParameter(String.class, FIRST_PARAM_NAME)
                .addParameter(String.class, AFTER_PARAM_NAME)
                .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), REQUEST_HEADER_NAME)
                .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), SECOND_PARAM_NAME);

        methodSpecBuilderCache.put(key, builder);
        return builder;
    }

    public void createClassFile() {
        for (Name name : typeSpecBuilderCache.keySet()) {
            TypeSpec.Builder typeSpecBuilder = typeSpecBuilderCache.get(name);
            MethodSpec.Builder methodBuilder = methodSpecBuilderCache.get(name);
            if (methodBuilder != null) {
                methodBuilder.addStatement("return realService.errorPath()");
                typeSpecBuilder.addMethod(methodBuilder.build());
            }
            //自动生成的类放在自己创建类同一个包里边
            String packageName = name.toString().substring(0, name.toString().lastIndexOf("."));
            JavaFile javaFile = JavaFile.builder(packageName, typeSpecBuilder.build()).build();
            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void processService(TypeElement element) {
        createTypeSpecBuilder(element);
        createOrGetMethodSpecBuilder(element);
    }

    public void processPage(ExecutableElement element) {
        TypeElement typeElement = (TypeElement) element.getEnclosingElement();
        createTypeSpecBuilder(typeElement);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(typeElement);

        String methodName = element.getSimpleName().toString();
        log("《Page注解》修饰的方法名称：" + methodName);
        Page page = element.getAnnotation(Page.class);
        String value = page.value();
        log("《Page注解》内的值：" + value);
        builder.beginControlFlow("if (action.equals($S))", value);
        builder.addStatement("return realService.fileSuccess($N$N)", methodName, "()");
        builder.endControlFlow();
    }

    public void processRequest(ExecutableElement element) {
        TypeElement typeElement = (TypeElement) element.getEnclosingElement();
        createTypeSpecBuilder(typeElement);
        MethodSpec.Builder builder = createOrGetMethodSpecBuilder(typeElement);
        String methodName = element.getSimpleName().toString();
        Request request = element.getAnnotation(Request.class);
        String value = request.value();
        builder.beginControlFlow("if (action.equals($S))", value);

        log("《Get注解》修饰的方法名：" + methodName);
        log("《Get注解》内的值：" + value);
        log("《Get注解》修饰的方法的返回值类型：" + element.getReturnType());
        // 进程安全
        StringBuffer stringBuffer = new StringBuffer();
        //拼接方法参数
        stringBuffer.append("(");
        int index = 0;
        // 取到方法内的参数
        List<? extends VariableElement> params = element.getParameters();

        if (!params.isEmpty()) {
            for (VariableElement variableElement : params) {
                String paramKey = variableElement.getSimpleName().toString();
                TypeMirror paramType = variableElement.asType();
                log("《Get注解》Param参数 key = " + paramKey + "-----paramType = " + paramType);

                RequestHeader headerAnnotation = variableElement.getAnnotation(RequestHeader.class);
                if (headerAnnotation != null && variableElement.getKind() == ElementKind.PARAMETER) {
                    // 有header 获取header没有就捕获取
                    builder.addStatement("$T $N = $N", paramType, paramKey, REQUEST_HEADER_NAME);
                } else {
                    builder.addStatement("String $N = $N.get($S)", paramKey, SECOND_PARAM_NAME, paramKey);

                    UpJson jsonAnnotation = variableElement.getAnnotation(UpJson.class);
                    if (jsonAnnotation != null && variableElement.getKind() == ElementKind.PARAMETER) {
                        // 找到被@UpJson注解的参数 //参数名 paramKey
                        builder.beginControlFlow("if (contentType.contains($S))", APPLICATION_JSON)
                                .addStatement("$N = $N.get($S)", paramKey, SECOND_PARAM_NAME, "json")
                                .endControlFlow();
//                if (contentType.eques(APPLICATION_JSON)) {
//                    $N = paramKey.get("json");
//                }
                    }
                    UpXml xmlAnnotation = variableElement.getAnnotation(UpXml.class);
                    if (xmlAnnotation != null && variableElement.getKind() == ElementKind.PARAMETER) {
                        // 找到被@UpXml注解的参数 //参数名 paramKey
                        builder.beginControlFlow("if (contentType.contains($S))", APPLICATION_XML)
                                .addStatement("$N = $N.get($S)", paramKey, SECOND_PARAM_NAME, "xml")
                                .endControlFlow();
                    }

                    builder.beginControlFlow("if ($N == null)", paramKey)
                            .addStatement("$N = $S", paramKey, ALSUtils.getDefaultValueByParamTypeWhenNull(paramKey, paramType))
                            .endControlFlow();
                }
                String realValue = ALSUtils.getParamStatementByParamType(paramKey, paramType);

                if (index > 0) {
                    stringBuffer.append(", ").append(realValue);
                } else {
                    stringBuffer.append(realValue);
                }
                index++;
            }
        }
        stringBuffer.append(")");
        //- 添加try catch
        builder.beginControlFlow("try");
        //- 添加try catch
        if (element.getReturnType() instanceof NoType) {// 没有返回值的
            //method(xx,xxx,);
            builder.addStatement("$N$N", methodName, stringBuffer.toString());
            builder.addStatement("return realService.successEmpty()");
        } else { // 有返回值的
            String paramAll = stringBuffer.toString();
            // 这里是没有@UpFile注解的情况
            builder.addStatement("$T result = $N$N", element.getReturnType(), methodName, paramAll);
            builder.addStatement("return realService.success(result)");
        }
        //- 添加try catch
        builder.nextControlFlow("catch (Exception e)")
                .addStatement("return realService.customResponse(e)")
                .endControlFlow();
        //- 添加try catch
        builder.endControlFlow();
    }

    public void processParam(VariableElement element) {
        // TODO: 暂未使用param这个注解
        //  测试中发现针对ExecutableElement通过element.getParameters()直接可以取到方法内的参数
        String paramType = element.asType().toString();
        String paramName = element.getSimpleName().toString();
        String methodName = element.getEnclosingElement().getSimpleName().toString();
        log("《Param注解》所在的方法是" + methodName + "  参数名称：" + paramName + "  参数类型：" + paramType);
    }

    private void log(CharSequence msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
    }
}
