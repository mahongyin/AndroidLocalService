# als(android local service)
-keep class com.android.local.service.core.** { *; }
-keep class **.ALS_* { *; }
-keep @com.android.local.service.annotation.* class *

-keep class javax.servlet.** { *; }
-keep interface javax.servlet.** { *; }
-dontwarn javax.servlet.**
-dontwarn javax.portlet.ActionRequest
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**

-keep class org.apache.commons.** { *; }
-keep class org.nanohttpd.** { *; }

