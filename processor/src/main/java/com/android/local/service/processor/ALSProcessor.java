package com.android.local.service.processor;

import com.android.local.service.annotation.Page;
import com.android.local.service.annotation.Request;
import com.android.local.service.annotation.RequestHeader;
import com.android.local.service.annotation.ServicePort;
import com.android.local.service.annotation.UpFile;
import com.android.local.service.annotation.UpJson;
import com.android.local.service.annotation.UpXml;
import com.android.local.service.processor.helper.ALSProcessorHelper;
import com.squareup.javapoet.TypeName;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

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
        // 添加支持的注解类型
        annotationTypes.add(Request.class.getCanonicalName());
        annotationTypes.add(Page.class.getCanonicalName());
        annotationTypes.add(ServicePort.class.getCanonicalName());
        annotationTypes.add(UpFile.class.getCanonicalName());
        annotationTypes.add(UpJson.class.getCanonicalName());
        annotationTypes.add(UpXml.class.getCanonicalName());
        annotationTypes.add(RequestHeader.class.getCanonicalName());
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
                    if (ServicePort.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processService((TypeElement) element);
                    }
                } else if (element instanceof ExecutableElement) {//方法
                    if (Page.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processPage((ExecutableElement) element);
                    } else if (Request.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processRequest((ExecutableElement) element);
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
                        UpXml xmlAnnotation = element.getAnnotation(UpXml.class);
                        if (xmlAnnotation != null) {
                            String type = element.asType().toString();
                            if (!type.equals(TypeName.get(String.class).toString())) {
                                throw new IllegalArgumentException(xmlAnnotation.message());
                            }
                        }
                        RequestHeader headerAnnotation = element.getAnnotation(RequestHeader.class);
                        if (headerAnnotation != null) {
                            String type = element.asType().toString();
                            if (!type.equals("java.util.Map<java.lang.String,java.lang.String>")) {
                                throw new IllegalArgumentException(headerAnnotation.message());
                            }
                        }
                    }
                }
            }
        }
        alsProcessorHelper.createClassFile();
        return true;
    }
}