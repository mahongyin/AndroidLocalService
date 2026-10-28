package com.android.local.service.core.i;


import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;

import java.util.Map;

public interface RequestListener {
    Response onRequest(IHTTPSession session, String action, String contentType, Map<String, String> headers, Map<String, String> params);
}