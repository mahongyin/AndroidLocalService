package com.android.local.service.processor;

import com.android.local.service.processor.helper.ALSAnnotation;
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
 * <p>
 * 注解身份一律用全限定名（{@link ALSAnnotation}，直接来自注解类）比较，
 * 既避免同名注解串号，也保证注解改名时本模块编译失败而不是静默失效。
 */
public class ALSProcessor implements SymbolProcessor {

    private final SymbolProcessorEnvironment environment;
    private boolean generated = false;

    public ALSProcessor(SymbolProcessorEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public List<KSAnnotated> process(Resolver resolver) {
        // 一轮生成完即可，后续轮次不再重复处理
        if (!generated) {
            generated = true;
            ALSProcessorHelper helper = new ALSProcessorHelper(
                    environment.getCodeGenerator(), environment.getLogger(), resolver);

            forEachSourceSymbol(resolver, ALSAnnotation.SERVICE_PORT, symbol -> {
                if (symbol instanceof KSClassDeclaration) {
                    helper.processService((KSClassDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, ALSAnnotation.REQUEST, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processRequest((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, ALSAnnotation.REQUEST_SSE, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processSSE((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, ALSAnnotation.PAGE_FILE, symbol -> {
                if (symbol instanceof KSFunctionDeclaration) {
                    helper.processFile((KSFunctionDeclaration) symbol);
                }
            });

            forEachSourceSymbol(resolver, ALSAnnotation.PAGE, symbol -> {
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
    private void forEachSourceSymbol(Resolver resolver, String annotationQualifiedName, SymbolHandler handler) {
        java.util.Iterator<KSAnnotated> iterator =
                resolver.getSymbolsWithAnnotation(annotationQualifiedName, false).iterator();
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
