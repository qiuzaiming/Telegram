package org.telegram.messenger.voip;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;

@TargetApi(18)
public class VideoCapturerDevice {

    private static final int CAPTURE_FPS = 30;

    public static EglBase eglBase;

    public static Intent mediaProjectionPermissionResultData;

    private VideoCapturer videoCapturer;

    private HandlerThread thread;
    private Handler handler;
    private int currentWidth;
    private int currentHeight;

    private static VideoCapturerDevice[] instance = new VideoCapturerDevice[2];

    public VideoCapturerDevice(boolean screencast) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
        Logging.d("VideoCapturerDevice", "device model = " + Build.MANUFACTURER + Build.MODEL);
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
            }
            instance[screencast ? 1 : 0] = this;
            thread = new HandlerThread("CallThread");
            thread.start();
            handler = new Handler(thread.getLooper());
        });
    }

    public static void checkScreenCapturerSize() {
        if (instance[1] == null) {
            return;
        }
        Point size = getScreenCaptureSize();
        if (instance[1].currentWidth != size.x || instance[1].currentHeight != size.y) {
            instance[1].currentWidth = size.x;
            instance[1].currentHeight = size.y;
            VideoCapturerDevice device = instance[1];
            instance[1].handler.post(() -> {
                if (device.videoCapturer != null) {
                    device.videoCapturer.changeCaptureFormat(size.x, size.y, CAPTURE_FPS);
                }
            });
        }
    }

    private static Point getScreenCaptureSize() {
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        float aspect;
        if (size.x > size.y) {
            aspect = size.y / (float) size.x;
        } else {
            aspect = size.x / (float) size.y;
        }
        int dx = -1;
        int dy = -1;
        for (int a = 1; a <= 100; a++) {
            float val = a * aspect;
            if (val == (int) val) {
                if (size.x > size.y) {
                    dx = a;
                    dy = (int) (a * aspect);
                } else {
                    dy = a;
                    dx = (int) (a * aspect);
                }
                break;
            }
        }
        if (dx != -1 && aspect != 1) {
            while (size.x > 1000 || size.y > 1000 || size.x % 4 != 0 || size.y % 4 != 0) {
                size.x -= dx;
                size.y -= dy;
                if (size.x < 800 && size.y < 800) {
                    dx = -1;
                    break;
                }
            }
        }
        if (dx == -1 || aspect == 1) {
            float scale = Math.max(size.x / 970.0f, size.y / 970.0f);
            size.x = (int) Math.ceil((size.x / scale) / 4.0f) * 4;
            size.y = (int) Math.ceil((size.y / scale) / 4.0f) * 4;
        }
        return size;
    }

    public static MediaProjection getMediaProjection() {
        if (instance[1] == null) {
            return null;
        }
        return ((ScreenCapturerAndroid) instance[1].videoCapturer).getMediaProjection();
    }

    public static EglBase getEglBase() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase;
    }

    private static native CapturerObserver nativeGetJavaVideoCapturerObserver(long ptr);
}
