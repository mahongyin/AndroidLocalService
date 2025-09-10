package com.android.local.service.core.e;


import static org.nanohttpd.protocols.http.NanoHTTPD.mimeTypes;

import android.text.TextUtils;

import com.android.local.service.core.ALSHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created By Mahongyin
 * Date    2025/9/1 9:38
 * 主动抛出异常的方式实现自定义返回内容
 */
public class CustomResponse extends Exception {
    private String contentType = "application/json";
    private Map<String, String> headers = new HashMap<>();

    private CustomResponse() {
    }

    public CustomResponse(JSONObject response) {
        super(response != null ? response.toString() : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }

    public CustomResponse(JSONArray response) {
        super(response != null ? response.toString() : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }

    public CustomResponse(Map<String, Object> response) {
        super(response != null ? ALSHelper.toJsonString(response) : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }

    public CustomResponse(List<?> response) {
        super(response != null ? ALSHelper.toJsonString(response) : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }

    public CustomResponse(String response) {
        super(response);
    }

    public CustomResponse(Collection<?> response, String contentType) {
        super(getResponse(response, contentType));
        this.contentType = contentType;
    }

    public CustomResponse(Map<String, Object> response, String contentType) {
        super(getResponse(response, contentType));
        this.contentType = contentType;
    }

    public CustomResponse(String response, String contentType) {
        super(getResponse(response, contentType));
        this.contentType = contentType;
    }

    public String getContentType() {
//        1优先 header 没有header就取contentType
        if (headers.containsKey("Content-Type")) {//会被header覆盖
            contentType = headers.get("Content-Type");
        }
//        2优先 contentType
//        if (TextUtils.isEmpty(contentType)){
//            contentType = headers.get("Content-Type");
//        }
//        3默认 contentType没填 则给默认值
        if (TextUtils.isEmpty(contentType)) {
            contentType = mimeTypes().get("json");
        }
        return contentType;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    private static String getResponse(Collection<?> response, String contentType) {
        String res = "{\"message\":\"实现自定义返回内容。但response为空\"}";
        if (contentType != null && contentType.contains("/xml")) {
            return response != null ? ALSHelper.listToXml(response) : "<message>实现自定义返回内容。但response为空</message>";
        } else {
            return response != null ? ALSHelper.toJsonString(response) : res;
        }
    }
    private static String getResponse(Map<String, Object> response, String contentType) {
        String res = "{\"message\":\"实现自定义返回内容。但response为空\"}";
        if (contentType != null && contentType.contains("/xml")) {
            return response != null ? ALSHelper.mapToXml(response) : "<message>实现自定义返回内容。但response为空</message>";
        } else {
            return response != null ? ALSHelper.toJsonString(response) : res;
        }
    }
    private static String getResponse(String response, String contentType) {
        String res = "{\"message\":\"实现自定义返回内容。但response为空\"}";
        if (contentType != null && contentType.contains("/xml")) {
            // response 自身为xml格式
            return response != null ? response : "<message>实现自定义返回内容。但response为空</message>";
        } else {
            return response != null ? response : res;
        }
    }
}
