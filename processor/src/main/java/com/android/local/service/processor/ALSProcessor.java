package com.android.local.service.processor;

import com.android.local.service.annotation.Page;
import com.android.local.service.annotation.PageFile;
import com.android.local.service.annotation.Request;
import com.android.local.service.annotation.RequestHeader;
import com.android.local.service.annotation.RequestSSE;
import com.android.local.service.annotation.ServicePort;
import com.android.local.service.annotation.UpFile;
import com.android.local.service.annotation.UpJson;
import com.android.local.service.annotation.UpXml;
import com.android.local.service.processor.helper.ALSProcessorHelper;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

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
        annotationTypes.add(PageFile.class.getCanonicalName());
        annotationTypes.add(RequestSSE.class.getCanonicalName());
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
                    } else if (PageFile.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processFile((ExecutableElement) element);
                    } else if (RequestSSE.class.getName().equals(annotationClassName)) {
                        alsProcessorHelper.processSSE((ExecutableElement) element);
                    }
                }
                // 参数注解的校验放在 processRequest / processSSE 遍历参数时进行：
                // kapt 对 Kotlin 源码的参数注解，getElementsAnnotatedWith 往往取不到参数元素
            }
        }
        alsProcessorHelper.createClassFile();
        return true;
    }
}
