package com.android.local.service.processor;

import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;

/**
 * KSP 通过 META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider 发现处理器
 */
public class ALSProcessorProvider implements SymbolProcessorProvider {

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new ALSProcessor(environment);
    }
}
