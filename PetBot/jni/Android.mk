LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := PetBot
LOCAL_SRC_FILES := PetBot.cpp

include $(BUILD_SHARED_LIBRARY)
