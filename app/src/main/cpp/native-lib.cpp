#include <jni.h>
#include <string>

struct Param{
    FILE* mFile;
    unsigned char* mBuffer;
    int mBufferSize;

    Param(FILE* file, int bufferSize) {
        mFile = file;
        mBufferSize = bufferSize;
        mBuffer = new unsigned char[bufferSize];
    }

    ~Param() {
        delete(mBuffer);
    }
};

extern "C"
JNIEXPORT jlong

JNICALL
Java_ornithopter_myapplication_IOTest_prepareNative(
    JNIEnv *env,
    jobject /* this */,
    jstring jFilePath,
    jint bufferSize) {

    const char* filePath = env->GetStringUTFChars(jFilePath, NULL);
    FILE *file = fopen(filePath, "r");
    env->ReleaseStringUTFChars(jFilePath, filePath);

    return (jlong) new Param(file, bufferSize);
}

extern "C"
JNIEXPORT void

JNICALL
Java_ornithopter_myapplication_IOTest_teardownNative(
    JNIEnv *env,
    jobject /* this */,
    jlong paramPointer) {

    Param * p = (Param*)paramPointer;
    delete(p);
}

extern "C"
JNIEXPORT jlong

JNICALL
Java_ornithopter_myapplication_IOTest_startNative(
    JNIEnv *,
    jobject /* this */,
    jlong paramPointer) {

    Param * p = (Param*)paramPointer;
    while (fread(p->mBuffer, (size_t) p->mBufferSize, sizeof(unsigned char), p->mFile) > 0) {

    }

    return 0;
}