package com.android.local.service.core.service;

import android.content.res.AssetManager;
import android.util.Log;
import com.android.local.service.core.ALSHelper;
import com.android.local.service.core.i.RequestListener;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ALSService extends NanoHTTPD {

    private static final String TAG = ALSService.class.getSimpleName();

    private RequestListener requestListener;
    private FileUpload upload;

    public ALSService(int port) {
        super(port);
    }

    private void initUpFile() {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        upload = new FileUpload(factory);
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

        String contentType = "text/plain";
        if (session.getHeaders().containsKey("content-type")) {
            contentType = session.getHeaders().get("content-type").toLowerCase(Locale.getDefault());
        }

        if (Method.POST.equals(method)) {
            try {
                if (contentType.contains("multipart/form-data")) {
                    params = handleMultipartData(session);
                } else if (contentType.contains("application/json")) {
                    String body = getRequestBody(session);
                    Log.d("JSON Body", body);
                    params = new HashMap<>();
                    params.put("json", body);
                } else {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    params = session.getParms();
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
            response = requestListener.onRequest(contentType, action, params != null ? params : new HashMap<>());
        } else {
            throw new RuntimeException("setRequestListener方法没有设置");
        }

        return wrapResponse(session, response);
    }

    private String getRequestBody(IHTTPSession session) {
        int contentLength = session.getHeaders().containsKey("content-length") ?
                Integer.parseInt(session.getHeaders().get("content-length")) : 0;
        byte[] buffer = new byte[contentLength];
        try {
            session.getInputStream().read(buffer, 0, contentLength);
            return new String(buffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private Map<String, String> handleMultipartData(IHTTPSession session) {
        Map<String, String> result = new HashMap<>();
        Map<String, List<Map<String, Object>>> filesInfo = new HashMap<>();
        Map<String, String> formFields = new HashMap<>();

        try {
            RequestContext request = new RequestContext() {
                @Override
                public String getCharacterEncoding() {
                    String contentTypeHeader = session.getHeaders().get("content-type");
                    if (contentTypeHeader != null && contentTypeHeader.contains(";")) {
                        String[] parts = contentTypeHeader.trim().split(";");
                        if (parts.length > 1 && parts[1].contains("=")) {
                            return parts[1].split("=")[1];
                        }
                    }
                    return "UTF-8";
                }

                @Override
                public String getContentType() {
                    return session.getHeaders().get("content-type") != null ?
                            session.getHeaders().get("content-type") : "text/plain";
                }

                @Override
                public int getContentLength() {
                    String contentLength = session.getHeaders().get("content-length");
                    return contentLength != null ? Integer.parseInt(contentLength) : 0;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return session.getInputStream();
                }
            };

            if (upload == null) {
                initUpFile();
            }

            List<FileItem> items = upload.parseRequest(request);
            List<Map<String, Object>> listFile = new ArrayList<>();

            for (FileItem item : items) {
                if (item.isFormField()) {
                    String value = item.getString("UTF-8");
                    formFields.put(item.getFieldName(), value);
                } else {
                    String fileName = item.getName();
                    String fieldName = item.getFieldName();
                    String contentTypeItem = item.getContentType();
                    long fileSize = item.getSize();

                    android.content.Context context = ALSHelper.getContext();
                    File cacheDir = context.getExternalCacheDir() != null ?
                            context.getExternalCacheDir() : context.getCacheDir();
                    File uploadDir = new File(cacheDir, "uploads");
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs();
                    }

                    File uploadedFile = new File(uploadDir,
                            fileName != null ? fileName : "upload_" + System.currentTimeMillis());
                    item.write(uploadedFile);

                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("path", uploadedFile.getAbsolutePath());
                    fileInfo.put("filename", fileName != null ? fileName : "");
                    fileInfo.put("content_type", contentTypeItem != null ? contentTypeItem : "");
                    fileInfo.put("size", fileSize);
                    fileInfo.put("field_name", fieldName);

                    listFile.add(fileInfo);
                }
            }

            result.putAll(formFields);

            for (Map<String, Object> file : listFile) {
                String fieldName = (String) file.get("field_name");
                if (filesInfo.containsKey(fieldName) && filesInfo.get(fieldName) != null) {
                    filesInfo.get(fieldName).add(file);
                } else {
                    List<Map<String, Object>> newList = new ArrayList<>();
                    newList.add(file);
                    filesInfo.put(fieldName, newList);
                }
            }
            for (Map.Entry<String, List<Map<String, Object>>> entry : filesInfo.entrySet()) {
                result.put(entry.getKey(), listToJSONArray(entry.getValue()).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error handling multipart data: " + e.getMessage());
        }
        return result;
    }

    private Response wrapResponse(IHTTPSession session, Response response) {
        String allowHeaders = "Content-Type";
        if (session.getHeaders() != null) {
            String requestHeaders = session.getHeaders().get("access-control-request-headers");
            if (requestHeaders != null) {
                allowHeaders = requestHeaders;
            }
        }

        response.addHeader("Access-Control-Allow-Headers", allowHeaders);
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Max-Age", String.valueOf(42 * 60 * 60));
        return response;
    }

    private String getFileExtensionName(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault());
    }

    private Response jsonResponse(int code, String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message);
        if (data != null) {
            result.put("data", data);
        }
        return Response.newFixedLengthResponse(
                Status.OK,
                mimeTypes().get("json"),
                mapToJsonString(result)
        );
    }

    private Response fileResponse(String fileName) {
        android.content.Context context = ALSHelper.getContext();
        if (context == null) {
            return fileNotFoundResponse();
        }
        AssetManager assetManager = context.getAssets();
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

    private Map<String, String> jsonStringToMap(String json) throws JSONException {
        Map<String, String> map = new HashMap<>();
        JSONObject jsonObject = new JSONObject(json);
        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            String key = it.next();
            map.put(key, jsonObject.getString(key));
        }
        return map;
    }

    private Map<String, Object> jsonToMap(JSONObject jsonObject) throws JSONException {
        Map<String, Object> map = new HashMap<>();
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
        return map;
    }

    private List<Object> jsonArrayToList(JSONArray jsonArray) throws JSONException {
        List<Object> list = new ArrayList<>();
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
        return list;
    }

    private String mapToJsonString(Map<String, Object> map) {
        JSONObject jsonObject = new JSONObject(map);
        return jsonObject.toString();
    }

    private String mapToJsonString2(Map<String, Object> map) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }
        return jsonObject.toString();
    }

    private JSONObject mapToJSONObject(Map<String, Object> map) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                jsonObject.put(entry.getKey(), mapToJSONObject((Map<String, Object>) value));
            } else if (value instanceof List) {
                jsonObject.put(entry.getKey(), listToJSONArray((List<?>) value));
            } else {
                jsonObject.put(entry.getKey(), value);
            }
        }
        return jsonObject;
    }

    private JSONArray listToJSONArray(List<?> list) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (Object item : list) {
            if (item instanceof Map) {
                jsonArray.put(mapToJSONObject((Map<String, Object>) item));
            } else if (item instanceof List) {
                jsonArray.put(listToJSONArray((List<?>) item));
            } else {
                jsonArray.put(item);
            }
        }
        return jsonArray;
    }
}
