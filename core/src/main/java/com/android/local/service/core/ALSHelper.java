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
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
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


    public static Map<String, Object> jsonObjectToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                String key = it.next();
                Object value = jsonObject.get(key);
                if (value instanceof JSONObject) {
                    map.put(key, jsonObjectToMap((JSONObject) value));
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
                    list.add(jsonObjectToMap((JSONObject) value));
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

    public static String toJsonString(Object object) {
        //JSONObject/JSONArray 的value包含实体类的没办法转换成json。。。。 用gson
        return new Gson().toJson(object);
    }

    //实体类转 map
    public static Map<String, Object> beanToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = obj.getClass();

        // 获取所有字段（包括私有字段）
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true); // 允许访问私有字段
                Object value = field.get(obj);
                map.put(field.getName(), value);
            } catch (IllegalAccessException e) {
                // 处理异常
                e.printStackTrace();
            }
        }
        return map;
    }

    public static String mapToXml(Map<String, Object> map) {
        return convertJsonToXml(toJsonString(map));
    }

    public static String listToXml(Collection<?> list) {
        return convertJsonToXml(toJsonString(list));
    }

    /**
     * 将JSON字符串转换为XML字符串
     *
     * @param jsonString JSON字符串
     * @return XML字符串
     * @throws JSONException JSON解析异常
     * @throws IOException   IO异常
     */
    public static String convertJsonToXml(String jsonString) {
        try {
            // 解析JSON
            Object jsonObject = parseJson(jsonString);

            // 创建XML序列化器
            StringWriter writer = new StringWriter();
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

            // 序列化
            if (jsonObject instanceof JSONObject) {
                serializeObject((JSONObject) jsonObject, "root", serializer);
            } else if (jsonObject instanceof JSONArray) {
                serializeArray((JSONArray) jsonObject, "root", serializer);
            }

            serializer.endDocument();
            return writer.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Object parseJson(String jsonString) throws JSONException {
        jsonString = jsonString.trim();
        if (jsonString.startsWith("{")) {
            return new JSONObject(jsonString);
        } else if (jsonString.startsWith("[")) {
            return new JSONArray(jsonString);
        } else {
            throw new JSONException("Invalid JSON format");
        }
    }

    private static void serializeObject(JSONObject jsonObject, String tagName, XmlSerializer serializer) {
        try {
            serializer.startTag("", sanitizeTagName(tagName));
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                serializeValue(value, key, serializer);
            }
            serializer.endTag("", sanitizeTagName(tagName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void serializeArray(JSONArray jsonArray, String tagName, XmlSerializer serializer) {
        try {
            serializer.startTag("", sanitizeTagName(tagName));

            for (int i = 0; i < jsonArray.length(); i++) {
                Object item = jsonArray.get(i);
                serializeValue(item, "item", serializer);
            }
            serializer.endTag("", sanitizeTagName(tagName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void serializeValue(Object value, String tagName, XmlSerializer serializer) {
        try {
            if (value instanceof JSONObject) {
                serializeObject((JSONObject) value, tagName, serializer);
            } else if (value instanceof JSONArray) {
                serializeArray((JSONArray) value, tagName, serializer);
            } else if (value != null) {
                serializer.startTag("", sanitizeTagName(tagName));
                serializer.text(value.toString());
                serializer.endTag("", sanitizeTagName(tagName));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sanitizeTagName(String tagName) {
        // 处理XML标签名规则
        if (tagName == null || tagName.isEmpty()) {
            return "node";
        }

        // 不能以数字开头
        if (Character.isDigit(tagName.charAt(0))) {
            tagName = "n" + tagName;
        }

        // 替换非法字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tagName.length(); i++) {
            char c = tagName.charAt(i);
            if (i == 0 && Character.isDigit(c)) {
                sb.append(c);
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }
}

