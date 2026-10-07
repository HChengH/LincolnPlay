#include <jni.h>
#include <stdint.h>
#include <opus.h>

/* Minimal JNI surface over the bundled libopus for the CarPlay microphone uplink.
 * Kotlin binds through com.shilapi.xcertplay.media.NativeOpus; errors return as
 * negative Opus codes so the caller can log them without exceptions. */

#define JNI_METHOD(name) Java_com_shilapi_xcertplay_media_NativeOpusEncoder_##name

JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(
        JNIEnv *env, jobject thiz, jint sampleRate, jint channels, jint application, jint bitrate) {
    (void) env;
    (void) thiz;
    int error = OPUS_OK;
    OpusEncoder *encoder = opus_encoder_create(
        (opus_int32) sampleRate, (int) channels, (int) application, &error);
    if (error != OPUS_OK || encoder == NULL) return 0;
    if (bitrate > 0) {
        opus_encoder_ctl(encoder, OPUS_SET_BITRATE((opus_int32) bitrate));
    }
    /* Voice traffic: a modest complexity keeps 20 ms frames well inside the capture
     * thread's budget on the i.MX8 cores; DTX and inband FEC stay off for simplicity. */
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(4));
    opus_encoder_ctl(encoder, OPUS_SET_DTX(0));
    opus_encoder_ctl(encoder, OPUS_SET_INBAND_FEC(0));
    return (jlong) (intptr_t) encoder;
}

JNIEXPORT jint JNICALL JNI_METHOD(nativeEncode)(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray pcm, jint sampleCount,
        jbyteArray output) {
    (void) thiz;
    if (handle == 0) return OPUS_BAD_ARG;
    OpusEncoder *encoder = (OpusEncoder *) (intptr_t) handle;
    jbyte *pcmBytes = (*env)->GetByteArrayElements(env, pcm, NULL);
    if (pcmBytes == NULL) return OPUS_ALLOC_FAIL;
    jbyte *outBytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (outBytes == NULL) {
        (*env)->ReleaseByteArrayElements(env, pcm, pcmBytes, JNI_ABORT);
        return OPUS_ALLOC_FAIL;
    }
    int written = opus_encode(
        encoder,
        (const opus_int16 *) pcmBytes,
        (opus_int32) sampleCount,
        (unsigned char *) outBytes,
        (opus_int32) (*env)->GetArrayLength(env, output));
    (*env)->ReleaseByteArrayElements(env, pcm, pcmBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, outBytes, written > 0 ? 0 : JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL JNI_METHOD(nativeDestroy)(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    if (handle != 0) opus_encoder_destroy((OpusEncoder *) (intptr_t) handle);
}
