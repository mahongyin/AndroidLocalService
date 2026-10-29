package com.android.local.service.processor.helper;

import com.android.local.service.annotation.Page;
import com.android.local.service.annotation.PageFile;
import com.android.local.service.annotation.Request;
import com.android.local.service.annotation.RequestHeader;
import com.android.local.service.annotation.RequestSSE;
import com.android.local.service.annotation.ServicePort;
import com.android.local.service.annotation.UpFile;
import com.android.local.service.annotation.UpJson;
import com.android.local.service.annotation.UpXml;

/**
 * 本框架用到的注解全限定名，全部直接取自注解类的 {@code Class#getName()}。
 * <p>
 * 之所以能这样写，是因为 annotation 模块是处理器的 <b>implementation 依赖</b>
 * （运行期 classpath 上存在 annotation.jar）——这一点很关键：
 * 若把 annotation 改成 {@code compileOnly}，处理器加载时会因为
 * {@code ServicePort.class} 触发类初始化而抛 {@code NoClassDefFoundError}。
 * <p>
 * 收益：注解类一旦改名或换包，<b>本模块编译期就会失败</b>，
 * 不会出现"注解名改了但处理器没同步"的静默失效。
 */
public final class ALSAnnotation {

    public static final String SERVICE_PORT = ServicePort.class.getName();
    public static final String REQUEST = Request.class.getName();
    public static final String REQUEST_SSE = RequestSSE.class.getName();
    public static final String PAGE = Page.class.getName();
    public static final String PAGE_FILE = PageFile.class.getName();
    public static final String REQUEST_HEADER = RequestHeader.class.getName();
    public static final String UP_FILE = UpFile.class.getName();
    public static final String UP_JSON = UpJson.class.getName();
    public static final String UP_XML = UpXml.class.getName();

    private ALSAnnotation() {
    }
}
