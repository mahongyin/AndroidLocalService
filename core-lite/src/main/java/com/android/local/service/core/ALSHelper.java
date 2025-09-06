package com.android.local.service.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.Xml;

import com.android.local.service.core.data.ServiceConfig;
import com.android.local.service.core.data.ServiceInfo;
import com.android.local.service.core.i.IService;
import com.android.local.service.core.service.ALSService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    /**
     * 配置返回数据结构字段名，可空【即没有这个字段】 int code, string message, object data
     */
    public static void configResponse(String codeName, String messageName, String dataName) {
        ALSService.CODE_NAME = codeName;
        ALSService.MESSAGE_NAME = messageName;
        ALSService.DATA_NAME = dataName;
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


    public static Map<String, Object> jsonToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                String key = it.next();
                Object value = jsonObject.get(key);
                if (value instanceof JSONObject) {
                    map.put(key, jsonToMap((JSONObject) value));
                } else if (value instanceof JSONArray) {
                    map.put(key, jsonArrayToList((JSONArray) value));
                } else {
                    map.put(key, value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return map;
    }

    public static List<Object> jsonArrayToList(JSONArray jsonArray) {
        List<Object> list = new ArrayList<>();
        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                Object value = jsonArray.get(i);
                if (value instanceof JSONObject) {
                    list.add(jsonToMap((JSONObject) value));
                } else if (value instanceof JSONArray) {
                    list.add(jsonArrayToList((JSONArray) value));
                } else {
                    list.add(value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static <T> String mapToJsonString(Map<String, T> map) {
        JSONObject jsonObject = new JSONObject(map);
        return jsonObject.toString();
    }

    public static <T> JSONObject mapToJSONObject(Map<String, T> map) {
        JSONObject jsonObject = new JSONObject();
        try {
            for (Map.Entry<String, T> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    jsonObject.put(entry.getKey(), mapToJSONObject((Map<String, Object>) value));
                } else if (value instanceof Collection) {
                    jsonObject.put(entry.getKey(), listToJSONArray((Collection<?>) value));
                } else if (value.getClass().isArray()) {
                    jsonObject.put(entry.getKey(), listToJSONArray(Collections.singletonList(value)));
                } else {
                    jsonObject.put(entry.getKey(), value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public static JSONArray listToJSONArray(Collection<?> list) {
        JSONArray jsonArray = new JSONArray();
        for (Object item : list) {
            if (item instanceof Map) {
                jsonArray.put(mapToJSONObject((Map<String, Object>) item));
            } else if (item instanceof Collection) {
                jsonArray.put(listToJSONArray((Collection<?>) item));
            } else if (item.getClass().isArray()) {
                jsonArray.put(listToJSONArray(Collections.singletonList(item)));
            } else {
                jsonArray.put(item);
            }
        }
        return jsonArray;
    }

    public static <T> String mapToXml(Map<String, T> map) {
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        //String rootElement = "root";
        try {
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            //serializer.startTag("", rootElement);
            serializeMap(serializer, map);
            //serializer.endTag("", rootElement);
            serializer.endDocument();
        } catch (Exception e) {
            Log.e(TAG, "mapToXml error", e);
            return ""; // 或者抛出异常
        }
        return writer.toString();
    }

    private static <T> void serializeMap(XmlSerializer serializer, Map<String, T> map) throws Exception {
        for (Map.Entry<String, T> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null) continue;
            serializer.startTag("", key);
            if (value != null) {
                if (value instanceof Map) {
                    // 递归处理嵌套Map
                    serializeMap(serializer, (Map<String, Object>) value);
                } else if (value instanceof Collection) {
                    // 处理List
                    serializeList(serializer, key, (Collection<?>) value);
                } else if (value.getClass().isArray()) {
                    // 处理Array
                    serializeList(serializer, key, Collections.singletonList(value));
                } else {
                    serializer.text(value.toString());
                }
            }
            serializer.endTag("", key);
        }
    }

    private static void serializeList(XmlSerializer serializer, String tagName, Collection<?> list) throws Exception {
        for (Object item : list) {
            serializer.startTag("", tagName);
            if (item != null) {
                if (item instanceof Map) {
                    serializeMap(serializer, (Map<String, Object>) item);
                } else if (item instanceof Collection) {
                    serializeList(serializer, tagName, (Collection<?>) item);
                } else if (item.getClass().isArray()) {
                    serializeList(serializer, tagName, Collections.singletonList(item));
                } else {
                    serializer.text(item.toString());
                }
            }
            serializer.endTag("", tagName);
        }
    }

}

