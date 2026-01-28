#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include "brisque.h"
#include <iostream>
#include <fstream>
#include <iostream>
#include <fstream>
#include <future>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <thread>

#define LOG_TAG "NativeCode"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define BUFFER_SIZE 256

static std::mutex mtx;

float rescale_vector[36][2];
AAssetManager *mgr = nullptr;

std::string jstring2string(JNIEnv *env, jstring jStr) {
    if (!jStr)
        return "";

    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes,
                                                                       env->NewStringUTF("UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte *pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *) pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

static char *readline(AAsset *asset, char *buffer, int buffer_size) {
    int len = 0;
    char c;
    while (len < buffer_size - 1 && AAsset_read(asset, &c, 1) > 0 && c != '\n') {
        buffer[len++] = c;
    }
    buffer[len] = '\0';
    return (len > 0) ? buffer : nullptr;
}

//
int read_range_file(JNIEnv *env) {
    char buff[BUFFER_SIZE];
    int i;
    std::string range_fname = "allrange";

    // Obtain the AAssetManager instance from the assetManager Java object
//    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to obtain AAssetManager from Java object");
        return 1;
    }

    // Open the asset file
    AAsset *range_asset = AAssetManager_open(mgr, range_fname.c_str(), AASSET_MODE_STREAMING);
    if (range_asset == nullptr) {
        LOGE("Asset %s not found", range_fname.c_str());
        return 1;
    }

    // Read and process each line
    for (i = 0; i < 36; ++i) {
        if (readline(range_asset, buff, BUFFER_SIZE) == nullptr) {
            LOGE("Error reading asset or end of asset reached");
            break; // Break if we reach the end of the asset or read fails
        }
        LOGI("Read line: %s", buff);

        // Parse the line
        std::istringstream lineStream(buff);
        float a, b, c;
        lineStream >> a >> b >> c;
        rescale_vector[i][0] = b;
        rescale_vector[i][1] = c;
        LOGI("Read values: %f %f %f", a, b, c);
    }

    // Close the asset
    AAsset_close(range_asset);
    LOGI("Asset closed");

    return 0;
}

int read_range_file(string path) {
    //check if file exists
    char buff[100];
    int i;
    string range_fname = "path";
    FILE *range_file = fopen(range_fname.c_str(), "r");
    if (range_file == NULL) return 1;
    //assume standard file format for this program
    fgets(buff, 100, range_file);
    fgets(buff, 100, range_file);
    //now we can fill the array
    for (i = 0; i < 36; ++i) {
        float a, b, c;
        fscanf(range_file, "%f %f %f", &a, &b, &c);
        rescale_vector[i][0] = b;
        rescale_vector[i][1] = c;
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_upjao_scanassure_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_upjao_scanassure_MainActivity_getQualityScore(JNIEnv *env, jobject thiz,
                                                       jstring image_path,
                                                       jstring asset_path, jstring range_asset_path,
                                                       jstring savedPath,
                                                       jstring basePath,
                                                       jint x1,
                                                       jint x2,
                                                       jint x3,
                                                       jint x4,
                                                       jint y1,
                                                       jint y2,
                                                       jint y3,
                                                       jint y4
) {
    std::string assetPath = jstring2string(env, range_asset_path);
    if (
            read_range_file(env)
            == 1) {
        cout << "unable to open allrange file" <<
             endl;
        return env->NewStringUTF("can't read range file");
    }

//    std::string imagePath = jstring2string(env, image_path);
//
//    cv::Mat image = cv::imread(imagePath);
    float qualityscore;
    std::string path = jstring2string(env, image_path);
    std::string modelPath = jstring2string(env, asset_path);

    std::string iPath = jstring2string(env, savedPath);
    std::string base64Path = jstring2string(env, basePath);
    qualityscore = computescore(path, mgr, modelPath, iPath,base64Path, x1, x2, x3, x4, y1, y2, y3, y4);

    std::string score = std::to_string(qualityscore);
    cout << "Quality Score: " << qualityscore <<
         endl;
    return env->
            NewStringUTF(score.c_str()

    );
}
extern "C"
JNIEXPORT void JNICALL
Java_com_upjao_scanassure_MainActivity_initAssetManager(JNIEnv *env, jobject thiz,
                                                        jobject asset_manager) {
    mgr = AAssetManager_fromJava(env, asset_manager);

}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_upjao_scanassure_MyNativeService_getQualityScoreNative(JNIEnv *env, jobject thiz,
                                                                jstring image_path,
                                                                jstring asset_path) {
    if (read_range_file(env) == 1) {
        cout << "unable to open allrange file" << endl;
        return env->NewStringUTF("can't read range file");
    }

    float qualityscore;
    std::string path = jstring2string(env, image_path);
    std::string modelPath = jstring2string(env, asset_path);
//    qualityscore = computescore(path, mgr, modelPath);
    std::string score = std::to_string(qualityscore);
    cout << "Quality Score: " << qualityscore << endl;
    return env->NewStringUTF(score.c_str());
}
extern "C"
JNIEXPORT void JNICALL
Java_com_upjao_scanassure_MyNativeService_initAssetManager(JNIEnv *env, jobject thiz,
                                                           jobject asset_manager) {
    mgr = AAssetManager_fromJava(env, asset_manager);
}
