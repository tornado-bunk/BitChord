// macOS System Media Controls (MPNowPlayingInfoCenter & MPRemoteCommandCenter)
// for BitChord Desktop.

#include <jni.h>
#import <Cocoa/Cocoa.h>
#import <MediaPlayer/MediaPlayer.h>

#include <atomic>

namespace {

JavaVM* g_vm = nullptr;
std::atomic<bool> g_started{false};

void notifyButton(int button) {
    if (!g_vm) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    jint res = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    }
    if (env) {
        jclass type = env->FindClass("com/music/bitchord/desktop/DesktopMacMedia");
        if (type != nullptr) {
            jmethodID method = env->GetStaticMethodID(type, "onButton", "(I)V");
            if (method != nullptr) {
                env->CallStaticVoidMethod(type, method, static_cast<jint>(button));
            }
            env->DeleteLocalRef(type);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

NSString* toNSString(JNIEnv* env, jstring str) {
    if (!str) return @"";
    const char* utf = env->GetStringUTFChars(str, nullptr);
    NSString* res = [NSString stringWithUTF8String:utf];
    env->ReleaseStringUTFChars(str, utf);
    return res ?: @"";
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL Java_com_music_bitchord_desktop_DesktopMacMedia_nativeStart(
    JNIEnv*,
    jobject
) {
    if (g_started.load()) return JNI_TRUE;

    dispatch_async(dispatch_get_main_queue(), ^{
        MPRemoteCommandCenter* center = [MPRemoteCommandCenter sharedCommandCenter];

        [center.playCommand setEnabled:YES];
        [center.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(1);
            return MPRemoteCommandHandlerStatusSuccess;
        }];

        [center.pauseCommand setEnabled:YES];
        [center.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(2);
            return MPRemoteCommandHandlerStatusSuccess;
        }];

        [center.togglePlayPauseCommand setEnabled:YES];
        [center.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(1);
            return MPRemoteCommandHandlerStatusSuccess;
        }];

        [center.nextTrackCommand setEnabled:YES];
        [center.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(3);
            return MPRemoteCommandHandlerStatusSuccess;
        }];

        [center.previousTrackCommand setEnabled:YES];
        [center.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(4);
            return MPRemoteCommandHandlerStatusSuccess;
        }];

        [center.stopCommand setEnabled:YES];
        [center.stopCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent*) {
            notifyButton(5);
            return MPRemoteCommandHandlerStatusSuccess;
        }];
    });

    g_started.store(true);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_music_bitchord_desktop_DesktopMacMedia_nativeStop(
    JNIEnv*,
    jobject
) {
    if (!g_started.exchange(false)) return;

    dispatch_async(dispatch_get_main_queue(), ^{
        MPRemoteCommandCenter* center = [MPRemoteCommandCenter sharedCommandCenter];
        [center.playCommand removeTarget:nil];
        [center.pauseCommand removeTarget:nil];
        [center.togglePlayPauseCommand removeTarget:nil];
        [center.nextTrackCommand removeTarget:nil];
        [center.previousTrackCommand removeTarget:nil];
        [center.stopCommand removeTarget:nil];

        MPNowPlayingInfoCenter* infoCenter = [MPNowPlayingInfoCenter defaultCenter];
        infoCenter.nowPlayingInfo = nil;
        infoCenter.playbackState = MPNowPlayingPlaybackStateStopped;
    });
}

JNIEXPORT void JNICALL Java_com_music_bitchord_desktop_DesktopMacMedia_nativeUpdate(
    JNIEnv* env,
    jobject,
    jstring title,
    jstring artist,
    jstring album,
    jstring artUrl,
    jboolean playing,
    jlong positionMs,
    jlong durationMs
) {
    if (!g_started.load()) return;

    NSString* nsTitle = toNSString(env, title);
    NSString* nsArtist = toNSString(env, artist);
    NSString* nsAlbum = toNSString(env, album);
    BOOL isPlaying = playing == JNI_TRUE;
    double posSec = static_cast<double>(positionMs) / 1000.0;
    double durSec = static_cast<double>(durationMs) / 1000.0;

    dispatch_async(dispatch_get_main_queue(), ^{
        NSMutableDictionary* songInfo = [NSMutableDictionary dictionary];
        if (nsTitle.length > 0) songInfo[MPMediaItemPropertyTitle] = nsTitle;
        if (nsArtist.length > 0) songInfo[MPMediaItemPropertyArtist] = nsArtist;
        if (nsAlbum.length > 0) songInfo[MPMediaItemPropertyAlbumTitle] = nsAlbum;
        if (durSec > 0) songInfo[MPMediaItemPropertyPlaybackDuration] = @(durSec);
        songInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(posSec);
        songInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? @(1.0) : @(0.0);

        MPNowPlayingInfoCenter* infoCenter = [MPNowPlayingInfoCenter defaultCenter];
        infoCenter.nowPlayingInfo = songInfo;
        infoCenter.playbackState = isPlaying ? MPNowPlayingPlaybackStatePlaying : MPNowPlayingPlaybackStatePaused;
    });
}

} // extern "C"
