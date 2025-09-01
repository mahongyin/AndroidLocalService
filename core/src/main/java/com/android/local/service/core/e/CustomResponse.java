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

    @Override
    public String getMessage() {
        return super.getMessage();
    }
}
