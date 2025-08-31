package com.android.local.service.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.android.local.service.core.data.ServiceConfig;
import com.android.local.service.core.data.ServiceInfo;
import com.android.local.service.core.i.IService;

import org.nanohttpd.protocols.http.NanoHTTPD;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("StaticFieldLeak")
public class ALSHelper {
    private static final String TAG = "ALSHelper";

    private static final Map<String, IService> serviceInstanceMaps = new HashMap<>();
    private static Context context;
    private static final List<ServiceInfo> serviceList = new ArrayList<>();

    public static void init(Context appContext) {
        ALSHelper.context = appContext;
    }

    public static File getCacheDir() {
        if (context == null) throw new RuntimeException("请先调用init方法");
        return context.getExternalCacheDir() != null ?
                context.getExternalCacheDir() : context.getCacheDir();
    }

    public static AssetManager getAssetManager() {
        if (context == null) return null;
        return context.getAssets();
    }

    public static List<ServiceInfo> getServiceList() {
        return serviceList;
    }

    public static void startService(ServiceConfig serviceConfig) {
        ServiceInfo serviceInfo = serviceConfig.toServiceInfo();
        instanceService(serviceInfo);
    }

    public static void startServices(List<ServiceConfig> list) {
        for (ServiceConfig serviceConfig : list) {
            startService(serviceConfig);
        }
    }

    public static void stopService(ServiceConfig serviceConfig) {
        ServiceInfo info = serviceConfig.toServiceInfo();
        try {
            for (Map.Entry<String, IService> entry : serviceInstanceMaps.entrySet()) {
                String fullClassName = info.originFullClassName();
                if (entry.getKey().equals(fullClassName)) {
                    entry.getValue().getService().stop();
                    Log.d(TAG, "stopService: 《" + info.getServiceName() + "》服务已停止");
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "stopService：《" + info.getServiceName() + "》：" + e.getMessage());
        }
    }

    public static void stopServices(List<ServiceConfig> list) {
        for (ServiceConfig serviceConfig : list) {
            stopService(serviceConfig);
        }
    }

    public static void stopAllServices() {
        for (Map.Entry<String, IService> entry : serviceInstanceMaps.entrySet()) {
            try {
                entry.getValue().getService().stop();
                Log.d(TAG, "stopService: 《" + entry.getKey() + "》服务已停止");
            } catch (Exception e) {
                Log.d(TAG, "stopService：《" + entry.getKey() + "》 " + e.getMessage());
            }
        }
    }

    /**
     * 通过反射区实例化service去启动本地服务，免去手动实例化启动服务的繁琐步骤
     */
    private static void instanceService(ServiceInfo serviceInfo) {
        String key = serviceInfo.originFullClassName();
        IService serviceWrapper;

        if (serviceInstanceMaps.containsKey(key)) {
            serviceWrapper = serviceInstanceMaps.get(key);
        } else {
            try {
                String fullClassName = serviceInfo.createFullClassName();
                Class<?> cls = Class.forName(fullClassName);
                serviceWrapper = (IService) cls.newInstance();
                serviceInstanceMaps.put(key, serviceWrapper);
            } catch (Exception e) {
                Log.d(TAG, "instanceService实例化服务类失败");
                serviceWrapper = null;
            }
        }
        start(serviceWrapper, serviceInfo);
    }

    /**
     * 启动服务
     */
    private static void start(IService serviceWrapper, ServiceInfo serviceInfo) {
        if (serviceWrapper == null) return;

        int port = serviceInfo.getPort();
        NanoHTTPD service;
        if (port > 0) {
            service = serviceWrapper.getServiceByPort(port);
        } else {
            service = serviceWrapper.getService();
        }

        int servicePort = serviceWrapper.getServicePort();
        serviceInfo.setPort(servicePort);
        serviceList.add(serviceInfo);

        try {
            if (service.wasStarted()) {
                Log.d(TAG, "initService《" + serviceInfo.getServiceName() + "》发现已开启过服务，要先关闭");
                service.stop();
                Log.d(TAG, "initService《" + serviceInfo.getServiceName() + "》服务已关闭");
            }
            service.start();
            Log.d(TAG, "initService《" + serviceInfo.getServiceName() + "》服务已《《启动》》，端口号：" + servicePort);
        } catch (Exception e) {
            Log.d(TAG, "initService《" + serviceInfo.getServiceName() + "》服务启动《《失败》》，端口号：" +
                    servicePort + "  失败原因是：" + e.getMessage());
        }
    }
}

