package com.android.local.service.core.i;


import com.android.local.service.core.service.ALSService;

public interface IService {
    ALSService getService();
    ALSService getServiceByPort(int port);
    int getServicePort();
}
