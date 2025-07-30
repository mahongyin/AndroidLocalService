package com.android.local.service.processor;

import com.android.local.service.annotation.Get;
import com.android.local.service.annotation.Page;
import com.android.local.service.annotation.Service;
import com.android.local.service.annotation.UpFile;
import com.android.local.service.annotation.UpJson;
import com.android.local.service.processor.helper.ALSProcessorHelper;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class ALSProcessor extends AbstractProcessor {

    private ALSProcessorHelper alsProcessorHelper;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        alsProcessorHelper = new ALSProcessorHelper(processingEnv);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(Get.class.getCanonicalName());
        annotationTypes.add(Page.class.getCanonicalName());
        annotationTypes.add(Service.class.getCanonicalName());
        annotationTypes.add(UpFile.class.getCanonicalName());
        return annotationTypes;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) return false;
        for (TypeElement annotation : annotations) {
            String annotationClassName = annotation.getQualifiedName().toString();
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {//类
                    if (Service.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processService((TypeElement) element);
                    }
                } else if (element instanceof ExecutableElement) {//方法
                    if (Page.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processPage((ExecutableElement) element);
                    } else if (Get.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processGet((ExecutableElement) element);
                    }
                } else if (element instanceof VariableElement) {//参数
                    //alsProcessorHelper.processParam((VariableElement) element);
                    if (element.getKind() == ElementKind.PARAMETER) {
                        // 处理带有@UpFile注解的参数
                        UpFile fileAnnotation = element.getAnnotation(UpFile.class);
                        if (fileAnnotation != null) {
                            String type = element.asType().toString();
//                            System.out.println("paramType:" + String.class.getCanonicalName());
//                            System.out.println("paramType:" + String.class.getName());
                            if (!type.equals(TypeName.get(String.class).toString())) {
                                throw new IllegalArgumentException(fileAnnotation.message());
                            }
                        }
                        UpJson jsonAnnotation = element.getAnnotation(UpJson.class);
                        if (jsonAnnotation != null) {
                            String type = element.asType().toString();
                            if (!type.equals(TypeName.get(String.class).toString())) {
                                throw new IllegalArgumentException(jsonAnnotation.message());
                            }
                        }
                    }
                }
            }
        }
        alsProcessorHelper.createClassFile();
        return true;
    }












  // 所有注解@UpFile的字段 都给赋值 "哈哈哈"
    public boolean process2(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(UpFile.class);

        if (elements.isEmpty()) {
            return true;
        }

        // 按类分组处理
        Map<TypeElement, List<ExecutableElement>> methodsByClass = new HashMap<>();
        //所有 注解的 @UpFile 的参数
        for (Element element : elements) {
            if (element.getKind() == ElementKind.PARAMETER) {
                VariableElement parameter = (VariableElement) element;
                ExecutableElement method = (ExecutableElement) parameter.getEnclosingElement();
                TypeElement clazz = (TypeElement) method.getEnclosingElement();

                methodsByClass.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
            }
        }

        // 为每个类生成实现
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {
            generateImplementation(entry.getKey(), entry.getValue());
        }

        return true;
    }

    private void generateImplementation(TypeElement interfaceElement, List<ExecutableElement> methods) {
        try {
            // 生成实现类名
            String className = interfaceElement.getSimpleName() + "GeneratedImpl";
            TypeName interfaceType = ClassName.get(interfaceElement);

            // 创建实现类
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(interfaceType);

            // 为每个方法生成实现
            for (ExecutableElement method : methods) {
                MethodSpec methodImpl = generateMethodImpl(method);
                classBuilder.addMethod(methodImpl);
            }

            // 构建Java文件
            String packageName = processingEnv.getElementUtils()
                    .getPackageOf(interfaceElement)
                    .getQualifiedName()
                    .toString();

            JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                    .build();

            // 写入文件
            javaFile.writeTo(processingEnv.getFiler());

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Generated implementation: " + className);

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate implementation: " + e.getMessage());
        }
    }

    private MethodSpec generateMethodImpl(ExecutableElement method) {
        // 构建方法签名
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(method.getReturnType()));

        // 处理方法参数
        List<? extends VariableElement> parameters = method.getParameters();
        for (VariableElement param : parameters) {
            TypeName paramType = TypeName.get(param.asType());
            String paramName = param.getSimpleName().toString();
            methodBuilder.addParameter(paramType, paramName);
        }

        // 生成方法体 - 处理@File注解
        generateMethodBody(methodBuilder, parameters, method.getReturnType());

        return methodBuilder.build();
    }

    private void generateMethodBody(MethodSpec.Builder methodBuilder,
                                    List<? extends VariableElement> parameters,
                                    TypeMirror returnType) {
        // 处理每个带有@UpFile注解的参数
        for (VariableElement param : parameters) {
            UpFile fileAnnotation = param.getAnnotation(UpFile.class);
            if (fileAnnotation != null) {
                String paramName = param.getSimpleName().toString();
                TypeName paramType = TypeName.get(param.asType());

                // 根据参数类型生成赋值语句
                if (paramType.equals(TypeName.get(String.class))) {
                    // String类型参数赋值为"哈哈哈"
                    methodBuilder.addStatement("$N = $S", paramName, "哈哈哈");
                } else if (paramType.equals(TypeName.get(java.io.File.class))) {
                    // File类型参数赋值
                    methodBuilder.addStatement("$N = new $T($S)", paramName,
                            java.io.File.class, "哈哈哈");
                }
                // 可以添加更多类型处理
            }
        }

        // 根据返回类型添加返回语句
        if (returnType.getKind() != TypeKind.VOID) {
            if (returnType.getKind().isPrimitive()) {
                // 基本类型返回默认值
                if (returnType.getKind() == TypeKind.BOOLEAN) {
                    methodBuilder.addStatement("return false");
                } else if (returnType.getKind() == TypeKind.INT ||
                        returnType.getKind() == TypeKind.LONG ||
                        returnType.getKind() == TypeKind.SHORT ||
                        returnType.getKind() == TypeKind.BYTE) {
                    methodBuilder.addStatement("return 0");
                } else {
                    methodBuilder.addStatement("return null");
                }
            } else {
                methodBuilder.addStatement("return null");
            }
        }
    }
}