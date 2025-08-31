package com.android.local.service.core.data;


/**
 * Created By Mahongyin
 * Date    2025/8/6 13:04
 */
public class ServiceConfig {

    private Class<?> serviceClass;
    private int port = 0;

    public ServiceConfig(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    public ServiceConfig(Class<?> serviceClass, int port) {
        this.serviceClass = serviceClass;
        this.port = port;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "ServiceConfig{" +
                "serviceClass=" + serviceClass +
                ", port=" + port +
                '}';
    }

    public ServiceInfo toServiceInfo() {
        Class<?> serviceClass = this.serviceClass;
        String serviceName = serviceClass.getSimpleName();
        String createServiceName = "ALS_" + serviceName;
        String fullClassName = serviceClass.getName();
        if (fullClassName == null) {
            fullClassName = "";
        }
        String packageName = fullClassName.substring(0, fullClassName.toString().lastIndexOf("."));
        return new ServiceInfo(serviceName, this.port, createServiceName, packageName);
    }
}
