## Build Servicce Jar #################################

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := GlassUtilsAPI

LOCAL_SRC_FILES := src/com/ingenic/glass/camera/util/StorageSpaceUtil.java

include $(BUILD_STATIC_JAVA_LIBRARY)


## Build Apk #################################

include $(CLEAR_VARS)
LOCAL_PROGUARD_ENABLED := disabled 
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
	src/com/ingenic/glass/incall/aidl/IInCallService.aidl \
	src/com/ingenic/glass/incall/aidl/IInCallListener.aidl

LOCAL_STATIC_JAVA_LIBRARIES := cn.ingenic.glasssync.services sync_framework VoiceRecognizerAPI GlassUtilsAPI
LOCAL_PACKAGE_NAME := HanLangSight
#LOCAL_SDK_VERSION := current

# LOCAL_JNI_SHARED_LIBRARIES := libjni_mosaic

# LOCAL_REQUIRED_MODULES := libjni_mosaic

include $(BUILD_PACKAGE)
