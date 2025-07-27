package com.android.local.service.core.i

import org.nanohttpd.protocols.http.response.Response

interface RequestListener {
    fun onRequest(/*contentType: String, */action: String, params: Map<String, String>): Response
}