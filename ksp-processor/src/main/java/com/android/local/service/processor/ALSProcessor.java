package com.android.local.service.processor;

import com.android.local.service.processor.helper.ALSProcessorHelper;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;

import java.util.Collections;
import java.util.List;

/**
 * KSP 注解处理器：扫描 @ServicePort / @Request / @RequestSSE / @Page / @PageFile，
 * 为对应的服务类生成 ALS_XXX 包装类。
 */
public class ALSProcessor implements SymbolProcessor {

    private static final String ANNOTATION_PACKAGE = "com.android.local.service.annotation.";

    private static final String SERVICE_PORT = ANNOTATION_PACKAGE + "ServicePort";
    private static final String REQUEST = ANNOTATION_PACKAGE + "Request";
    private static final String REQUEST_SSE = ANNOTATION_PACKAGE + "RequestSSE";
    private static final String PAGE_FILE = ANNOTATION_PACKAGE + "PageFile";
    private static final String PAGE = ANNOTATION_PACKAGE + "Page";

    private final ALSProcessorHelper helper;
    private boolean generated = false;

    public ALSProcessor(SymbolProcessorEnvironment environment) {
        this.helper = new ALSProcessorHelper(environment.getCodeGenerator(), environment.getLogger());
    }

    @Override
    public List<KSAnnotated> process(Resolver resolver) {
        // 一轮生成完即可，后续轮次不再重复处理
        if (!generated) {
            generated = true;

            forEachSourceSymbol(resolver, SERVICE_PORT, symbol -> {
                if (symbol instanceof KSClassDeclaration) {
                    helper.processService((KSClassDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, REQUEST, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processRequest((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, REQUEST_SSE, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processSSE((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, PAGE_FILE, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processFile((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, PAGE, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processPage((KSFunctionDeclaration) symbol);
                }
            });

            helper.createClassFiles();
        }
        return Collections.emptyList();
    }

    /**
     * 只处理当前模块源码中的符号；依赖库里的声明不生成代码
     */
    private void forEachSourceSymbol(Resolver resolver, String annotationName, SymbolHandler handler) {
        java.util.Iterator<KSAnnotated> iterator = resolver.getSymbolsWithAnnotation(annotationName, false).iterator();
        while (iterator.hasNext()) {
            KSAnnotated symbol = iterator.next();
            // 只处理当前模块源码中的声明，跳过 classpath 里依赖库的符号
            if (!(symbol instanceof KSDeclaration) || ((KSDeclaration) symbol).getContainingFile() == null) {
                continue;
            }
            handler.handle(symbol);
        }
    }

    @Override
    public void finish() {
    }

    @Override
    public void onError() {
    }

    private interface SymbolHandler {
        void handle(KSAnnotated symbol);
    }
}
