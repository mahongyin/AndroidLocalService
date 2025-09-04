package com.android.local.service.core.e;


import com.android.local.service.core.ALSHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Created By Mahongyin
 * Date    2025/9/1 9:38
 * 主动抛出异常的方式实现自定义返回内容
 */
public class CustomResponse extends Exception {
    private String contentType = "application/json";

    private CustomResponse() {
    }

    public CustomResponse(JSONObject response) {
        super(response != null ? response.toString() : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }
    public CustomResponse(JSONArray response) {
        super(response != null ? response.toString() : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }
    public CustomResponse(Map<String, Object> response) {
        super(response != null ? ALSHelper.mapToJsonString(response) : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }
    public CustomResponse(List<?> response) {
        super(response != null ? ALSHelper.listToJSONArray(response).toString() : "{\"message\":\"实现自定义返回内容。但response为空\"}");
    }
    public CustomResponse(String response) {
        super(response);
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
        return contentType;
    }

    private static String getResponse(Map<String, Object> response, String contentType) {
        String res = "{\"message\":\"实现自定义返回内容。但response为空\"}";
        if (contentType != null && contentType.contains("/xml")) {
            return response != null ? ALSHelper.mapToXml(response) : "<message>实现自定义返回内容。但response为空</message>";
        } else {
            return response != null ? ALSHelper.mapToJsonString(response) : res;
        }
    }
    private static String getResponse(String response, String contentType) {
        String res = "{\"message\":\"实现自定义返回内容。但response为空\"}";
        if (contentType != null && contentType.contains("/xml")) {
            return response != null ? response : "<message>实现自定义返回内容。但response为空</message>";
        } else {
            return response != null ? response : res;
        }
    }
}
