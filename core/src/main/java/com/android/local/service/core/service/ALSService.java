package com.android.local.service.core.service;

import android.content.res.AssetManager;
import android.util.Log;

import com.android.local.service.core.ALSHelper;
import com.android.local.service.core.i.RequestListener;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ALSService extends NanoHTTPD {

    private static final String TAG = ALSService.class.getSimpleName();
    // 统一响应字段
    public static String CODE_NAME = "code";
    public static String MESSAGE_NAME = "message";
    public static String DATA_NAME = "data";
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

//        session.getParameters();
//        session.getCookies();
//        session.getInputStream();
//        session.getRemoteIpAddress();
//        session.getQueryParameterString();

        String contentType = "text/plain";
        if (session.getHeaders().containsKey("content-type")) {
            String tmpType = session.getHeaders().get("content-type");
            if (tmpType != null) {
                contentType = tmpType.toLowerCase(Locale.getDefault());
            }
        }
        // 接收请求类型
        if (Method.POST.equals(method)) {
            try {
                if (contentType.contains("multipart/form-data")) {
                    params = handleMultipartData(session);
                } else if (contentType.contains("application/json")) {//请求体中的数据是 JSON 格式
                    String body = getRequestBody(session);
                    Log.d("JSON Body", body);
                    params = new HashMap<>();
                    params.put("json", body);
                } else if (contentType.contains("application/xml")) {//请求体中的数据是 XML 格式
                    String body = getRequestBody(session);
                    Log.d("XML Body", body);
                    params = new HashMap<>();
                    params.put("xml", body);
                } else {
                    Map<String, String> body = new HashMap<>();
                    session.parseBody(body);
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
            Map<String, String> headerMap = session.getHeaders();
            Map<String, String> bodyMap = params != null ? params : new HashMap<>();
            response = requestListener.onRequest(contentType, action, headerMap, bodyMap);
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

                    File cacheDir = ALSHelper.getCacheDir();
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
                result.put(entry.getKey(), ALSHelper.listToJSONArray(entry.getValue()).toString());
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
        if (response != null) {
            response.addHeader("Access-Control-Allow-Headers", allowHeaders);
            //[OPTIONS, GET, HEAD, POST, PUT, DELETE, TRACE, PATCH]
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
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
    public Response customResponse(String jsonResponse) {
        if (jsonResponse == null){
            jsonResponse = "{}";
        }
        return Response.newFixedLengthResponse(
                Status.OK,
                mimeTypes().get("json"),
                jsonResponse);
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
