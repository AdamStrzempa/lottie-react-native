package com.airbnb.android.react.lottie;

import android.animation.Animator;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import android.widget.ImageView;
import android.view.View.OnAttachStateChangeListener;
import android.view.View;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.util.Map;
import java.util.WeakHashMap;

class LottieAnimationViewManager extends SimpleViewManager<LottieAnimationView> {
  private static final String TAG = LottieAnimationViewManager.class.getSimpleName();

  private static final String REACT_CLASS = "LottieAnimationView";
  private static final int VERSION = 1;
  private static final int COMMAND_PLAY = 1;
  private static final int COMMAND_RESET = 2;
  private static final int COMMAND_PAUSE = 3;
  private static final int COMMAND_RESUME = 4;

  private Map<LottieAnimationView, LottieAnimationViewPropertyManager> propManagersMap = new WeakHashMap<>();

  @Override public Map<String, Object> getExportedViewConstants() {
    return MapBuilder.<String, Object>builder()
        .put("VERSION", VERSION)
        .build();
  }

  @NonNull
  @Override public String getName() {
    return REACT_CLASS;
  }

  @Override public LottieAnimationView createViewInstance(ThemedReactContext context) {
    final LottieAnimationView view = new LottieAnimationView(context);
    view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    view.addAnimatorListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {}

      @Override
      public void onAnimationEnd(Animator animation) {
        sendOnAnimationFinishEvent(view, false);
      }

      @Override
      public void onAnimationCancel(Animator animation) {
        sendOnAnimationFinishEvent(view, true);
      }

      @Override
      public void onAnimationRepeat(Animator animation) {}
    });
    return view;
  }

  private void sendOnAnimationFinishEvent(final LottieAnimationView view, boolean isCancelled) {
    WritableMap event = Arguments.createMap();
    event.putBoolean("isCancelled", isCancelled);
    Context ctx = view.getContext();
    ReactContext reactContext = null;
    while (ctx instanceof ContextWrapper) {
      if (ctx instanceof ReactContext) {
        reactContext = (ReactContext)ctx;
        break;
      }
      ctx = ((ContextWrapper)ctx).getBaseContext();
    }
    if (reactContext != null) {
      reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
          view.getId(),
          "animationFinish",
          event);
    }
  }

  @Nullable
  @Override
  public Map<String, Object> getExportedCustomBubblingEventTypeConstants() {
    return MapBuilder.of(
            "animationFinish",
            MapBuilder.of(
                "phasedRegistrationNames",
                MapBuilder.of("bubbled", "onAnimationFinish")));
  }

  @Override public Map<String, Integer> getCommandsMap() {
    return MapBuilder.of(
        "play", COMMAND_PLAY,
        "reset", COMMAND_RESET,
        "pause", COMMAND_PAUSE,
        "resume", COMMAND_RESUME
    );
  }

  @Override
  public void receiveCommand(final LottieAnimationView view, String commandId, final ReadableArray args) {
    switch (Integer.parseInt(commandId)) {
      case COMMAND_PLAY: {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override public void run() {
            int startFrame = args.getInt(0);
            int endFrame = args.getInt(1);
            if (startFrame != -1 && endFrame != -1) {
               if(startFrame > endFrame){
                view.setMinAndMaxFrame(endFrame, startFrame);
                view.reverseAnimationSpeed();
              } else {
                view.setMinAndMaxFrame(startFrame, endFrame);
              }
            }
            if (ViewCompat.isAttachedToWindow(view)) {
              view.setProgress(0f);
              view.playAnimation();
            } else {
              view.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                   @Override
                   public void onViewAttachedToWindow(View v) {
                      LottieAnimationView view = (LottieAnimationView)v;
                      view.setProgress(0f);
                      view.playAnimation();
                      view.removeOnAttachStateChangeListener(this);
                   }

                   @Override
                   public void onViewDetachedFromWindow(View v) {
                      view.removeOnAttachStateChangeListener(this);
                   }
               });
            }
          }
        });
      }
      break;
      case COMMAND_RESET: {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override public void run() {
            if (ViewCompat.isAttachedToWindow(view)) {
              view.cancelAnimation();
              view.setProgress(0f);
            }
          }
        });
      }
      break;
      case COMMAND_PAUSE: {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
            if (ViewCompat.isAttachedToWindow(view)) {
                view.pauseAnimation();
            }
            }
        });
      }
      break;
      case COMMAND_RESUME: {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            if (ViewCompat.isAttachedToWindow(view)) {
              view.resumeAnimation();
            }
          }
        });
      }
      break;
    }
  }

  @ReactProp(name = "sourceName")
  public void setSourceName(LottieAnimationView view, String name) {
    // To match the behaviour on iOS we expect the source name to be
    // extensionless. This means "myAnimation" corresponds to a file
    // named `myAnimation.json` in `main/assets`. To maintain backwards
    // compatibility we only add the .json extension if no extension is
    // passed.
    if (!name.contains(".")) {
      name = name + ".json";
    }
    getOrCreatePropertyManager(view).setAnimationName(name);
  }

  @ReactProp(name = "sourceJson")
  public void setSourceJson(LottieAnimationView view, String json) {
    getOrCreatePropertyManager(view).setAnimationJson(json);
  }

  @ReactProp(name = "resizeMode")
  public void setResizeMode(LottieAnimationView view, String resizeMode) {
    ImageView.ScaleType mode = null;
    if ("cover".equals(resizeMode)) {
      mode = ImageView.ScaleType.CENTER_CROP;
    } else if ("contain".equals(resizeMode)) {
      mode = ImageView.ScaleType.CENTER_INSIDE;
    } else if ("center".equals(resizeMode)) {
      mode = ImageView.ScaleType.CENTER;
    }
    getOrCreatePropertyManager(view).setScaleType(mode);
  }

  @ReactProp(name = "renderMode")
  public void setRenderMode(LottieAnimationView view, String renderMode) {
    RenderMode mode = null;
    if ("AUTOMATIC".equals(renderMode) ){
      mode = RenderMode.AUTOMATIC;
    }else if ("HARDWARE".equals(renderMode)){
      mode = RenderMode.HARDWARE;
    }else if ("SOFTWARE".equals(renderMode)){
      mode = RenderMode.SOFTWARE;
    }
    getOrCreatePropertyManager(view).setRenderMode(mode);
  }

  @ReactProp(name = "progress")
  public void setProgress(LottieAnimationView view, float progress) {
    getOrCreatePropertyManager(view).setProgress(progress);
  }

  @ReactProp(name = "speed")
  public void setSpeed(LottieAnimationView view, double speed) {
    getOrCreatePropertyManager(view).setSpeed((float)speed);
  }

  @ReactProp(name = "loop")
  public void setLoop(LottieAnimationView view, boolean loop) {
    getOrCreatePropertyManager(view).setLoop(loop);
  }

  @ReactProp(name = "imageAssetsFolder")
  public void setImageAssetsFolder(LottieAnimationView view, String imageAssetsFolder) {
    getOrCreatePropertyManager(view).setImageAssetsFolder(imageAssetsFolder);
  }

  @ReactProp(name = "enableMergePathsAndroidForKitKatAndAbove")
  public void setEnableMergePaths(LottieAnimationView view, boolean enableMergePaths) {
    getOrCreatePropertyManager(view).setEnableMergePaths(enableMergePaths);
  }

  @ReactProp(name = "colorFilters")
  public void setColorFilters(LottieAnimationView view, ReadableArray colorFilters) {
    getOrCreatePropertyManager(view).setColorFilters(colorFilters);
  }

  @Override
  protected void onAfterUpdateTransaction(LottieAnimationView view) {
    super.onAfterUpdateTransaction(view);
    getOrCreatePropertyManager(view).commitChanges();
  }

  private LottieAnimationViewPropertyManager getOrCreatePropertyManager(LottieAnimationView view) {
    LottieAnimationViewPropertyManager result = propManagersMap.get(view);
    if (result == null) {
      result = new LottieAnimationViewPropertyManager(view);
      propManagersMap.put(view, result);
    }
    return result;
  }
}
