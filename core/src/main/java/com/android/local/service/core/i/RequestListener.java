package com.android.local.service.core.i;


import org.nanohttpd.protocols.http.response.Response;

import java.util.Map;

public interface RequestListener {
    Response onRequest(String contentType, String action, Map<String, String> params);
}