package com.android.local.service.core.data;


/**
 * Created By Mahongyin
 * Date    2025/8/6 13:05
 */
public class ServiceInfo {

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getCreateServiceName() {
        return createServiceName;
    }

    public void setCreateServiceName(String createServiceName) {
        this.createServiceName = createServiceName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    private String serviceName;//自己创建的类名：XXXService
    private int port;//端口号
    private String createServiceName;//根据自己创建的类自动生成的类名: ALS_XXXService
    private String packageName;//类所在的包名

    public ServiceInfo(String serviceName, int port, String createServiceName, String packageName) {
        this.serviceName = serviceName;
        this.port = port;
        this.createServiceName = createServiceName;
        this.packageName = packageName;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceName='" + serviceName + '\'' +
                ", port=" + port +
                ", createServiceName='" + createServiceName + '\'' +
                ", packageName='" + packageName + '\'' +
                '}';
    }


   public String originFullClassName() {
        return packageName + "." + serviceName;
    }

   public String createFullClassName() {
        return packageName + "." + createServiceName;
    }
}
