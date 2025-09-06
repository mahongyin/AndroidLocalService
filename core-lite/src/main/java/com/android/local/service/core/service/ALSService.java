package com.android.local.service.core.service;

import android.content.res.AssetManager;
import android.util.Log;

import com.android.local.service.core.ALSHelper;
import com.android.local.service.core.e.CustomResponse;
import com.android.local.service.core.i.RequestListener;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ALSService extends NanoHTTPD {

    private static final String TAG = ALSService.class.getSimpleName();

    private RequestListener requestListener;
    // 统一响应字段
    public static String CODE_NAME = "code";
    public static String MESSAGE_NAME = "message";
    public static String DATA_NAME = "data";

    public ALSService(int port) {
        super(port);
    }

    public void setRequestListener(RequestListener listener) {
        this.requestListener = listener;
    }

    @Override
    @Deprecated
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

//        session.getParameters();
//        session.getCookies();
//        session.getInputStream();
//        session.getRemoteIpAddress();
//        session.getQueryParameterString();

        String contentType = "text/plain";
        String tmpType = session.getHeaders().get("content-type");
        if (tmpType == null) {
            tmpType = session.getHeaders().get("Content-Type");
        }
        if (tmpType == null) {
            tmpType = session.getHeaders().get("CONTENT-TYPE");
        }
        if (tmpType != null) {
            contentType = tmpType.toLowerCase(Locale.getDefault());
        }
        // 接收请求类型
        if (Method.POST.equals(method) || Method.PUT.equals(method) || Method.NOTIFY.equals(method)) {
            try {
                if (contentType.contains("multipart/form-data")) {
                    params = handleMultipartData(session);
                } else if (contentType.contains("application/json")) {//请求体中的数据是 JSON 格式
//                    String body = getRequestBody(session);
//                    Log.d("JSON Body", body);
//                    params = new HashMap<>();
//                    params.put("json", body);

                    Map<String, String> body = new HashMap<>();
                    session.parseBody(body);
                    Map<String, String> newParams = new HashMap<>();
                    newParams.put("json", body.get("postData"));
                    params = newParams;
                } else if (contentType.contains("/xml")) {//请求体中的数据是 XML 格式
                    if (contentType.contains("application/xml")) {
                        Map<String, String> body = new HashMap<>();
                        session.parseBody(body);
                        Map<String, String> newParams = new HashMap<>();
                        newParams.put("xml", body.get("postData"));
                        params = newParams;
                    } else {// 都用下面这个也行
                        String body = getRequestBody(session);
                        //Log.d("XML Body", body);
                        params = new HashMap<>();
                        params.put("xml", body);
                    }
                } else {
//                    String queryString = session.getQueryParameterString();
//                    Map<String, List<String>> parameters = session.getParameters();
//                    Log.d(TAG, "queryString = " + queryString);
//                    Log.d(TAG, "parameters = " + ALSHelper.mapToJsonString(parameters));
//                    Log.d(TAG, "params = " + ALSHelper.mapToJsonString(params));

                    Map<String, String> body = new HashMap<>();
                    session.parseBody(body);
                    params = session.getParms();

//                    String queryString2 = session.getQueryParameterString();
//                    Map<String, List<String>> parameters2 = session.getParameters();
//                    Log.d(TAG, "queryString2 = " + queryString2);
//                    Log.d(TAG, "parameters2 = " + ALSHelper.mapToJsonString(parameters2));
//                    Log.d(TAG, "params2 = " + ALSHelper.mapToJsonString(params));
//                    Log.d(TAG, "body3 = " + ALSHelper.mapToJsonString(body));

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String action = uri != null ? uri.substring(1) : null;
        if (action == null) {
            return wrapResponse(session, fileNotFoundResponse());
        }

        Log.d(TAG, "uri = " + uri + "   method = " + method + "   params = " + params);

        Response response;
        if (requestListener != null) {
            Map<String, String> headerMap = session.getHeaders();
            Map<String, String> bodyMap = params != null ? params : new HashMap<>();
            response = requestListener.onRequest(action, contentType, headerMap, bodyMap);
        } else {
            throw new RuntimeException("setRequestListener 方法没有设置");
        }

        return wrapResponse(session, response);
    }

    private String getRequestBody(IHTTPSession session) {
        int contentLength = 0;
        try {
            if (session.getHeaders().containsKey("content-length")) {
                String contentLengthStr = session.getHeaders().get("content-length");
                if (contentLengthStr != null) {
                    contentLength = Integer.parseInt(contentLengthStr);
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        if (contentLength <= 0) {
            return "";
        }
        byte[] buffer = new byte[contentLength];
        try {
            int r = session.getInputStream().read(buffer, 0, contentLength);
            if (r <= 0) {
                return "";
            }
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private Map<String, String> handleMultipartData(IHTTPSession session) {
        try {
//            String queryString = session.getQueryParameterString();
//            Map<String, List<String>> parameters = session.getParameters();
//            Log.d(TAG, "queryString = " + queryString);
//            Log.d(TAG, "parameters = " + ALSHelper.mapToJsonString(parameters));

            Map<String, String> body = new HashMap<>();
            session.parseBody(body);
            Map<String, String> params = session.getParms();

//            String queryString2 = session.getQueryParameterString();
//            Map<String, List<String>> parameters2 = session.getParameters();
//            Log.d(TAG, "queryString2 = " + queryString2);
//            Log.d(TAG, "parameters2 = " + ALSHelper.mapToJsonString(parameters2));
//            Log.d(TAG, "params2 = " + ALSHelper.mapToJsonString(params));
//            Log.d(TAG, "body2 = " + ALSHelper.mapToJsonString(body));

            return params;
        } catch (Exception e) {
            Map<String, String> result = new HashMap<>();
            Log.e(TAG, "lib 'core-lite' is not support upload files, Please use lib 'core'");
            return result;
        }
    }

    private Response wrapResponse(IHTTPSession session, Response response) {
        String allowHeaders = "Content-Type";
        if (session.getHeaders() != null) {
            String requestHeaders = session.getHeaders().get("access-control-request-headers");
            if (requestHeaders != null) {
                allowHeaders = requestHeaders;
            }
        }
        if (response != null) {
            response.addHeader("Access-Control-Allow-Headers", allowHeaders);
            //[GET, POST, PUT, DELETE, HEAD"]
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, PATCH, SUBSCRIBE, UNSUBSCRIBE, NOTIFY");
            response.addHeader("Access-Control-Allow-Credentials", "true");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Max-Age", String.valueOf(42 * 60 * 60));
        }
        return response;
    }

    private String getFileExtensionName(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault());
    }

    /**
     * 自定义响应
     */
    public Response customResponse(Exception response) {
        String mimeType = response instanceof CustomResponse ? ((CustomResponse) response).getContentType() : mimeTypes().get("json");
        Response res = Response.newFixedLengthResponse(Status.OK, mimeType, response.getMessage());
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, OPTIONS, TRACE, PATCH, SUBSCRIBE, UNSUBSCRIBE, NOTIFY");
        res.addHeader("Access-Control-Allow-Credentials", "true");
        res.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        res.addHeader("Access-Control-Max-Age", String.valueOf(42 * 60 * 60));
        if (response instanceof CustomResponse) {
            for (Map.Entry<String, String> entry : ((CustomResponse) response).getHeaders().entrySet()) {
                res.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return res;
    }

    /**
     * 返回JSON响应  字段 code message data
     */
    private Response jsonResponse(int code, String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put(CODE_NAME, code);
        result.put(MESSAGE_NAME, message);
        if (data != null) {
            result.put(DATA_NAME, data);
        }
        return Response.newFixedLengthResponse(
                Status.OK,
                mimeTypes().get("json"),
                ALSHelper.mapToJsonString(result)
        );
    }

    private Response fileResponse(String fileName) {
        AssetManager assetManager = ALSHelper.getAssetManager();
        if (assetManager == null) {
            return fileNotFoundResponse();
        }
        try {
            InputStream stream = assetManager.open(fileName);
            String extension = getFileExtensionName(fileName);
            return Response.newChunkedResponse(Status.OK, mimeTypes().get(extension), stream);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "File not exist. " + fileName);
            return fileError();
        }
    }

    private Response fileNotFoundResponse() {
        return fileNotFoundResponse("Error 404, file not found.");
    }

    private Response fileNotFoundResponse(String error) {
        return Response.newFixedLengthResponse(
                Status.NOT_FOUND,
                MIME_PLAINTEXT,
                error
        );
    }

    public Response success(Object data) {
        return jsonResponse(200, "success", data);
    }

    public Response successEmpty() {
        return jsonResponse(200, "success", null);
    }

    public Response error(String message) {
        return jsonResponse(500, message, null);
    }

    public Response errorPath() {
        return jsonResponse(500, "unknow request path, please check it", null);
    }

    public Response fileSuccess(String fileName) {
        return fileResponse(fileName);
    }

    public Response fileError() {
        return fileNotFoundResponse();
    }

}
