package org.telegram.ui.Profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.text.MeasuredText;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.BlurSettingsBottomSheet;

import java.util.ArrayList;

public class LiquidLayout extends FrameLayout {

    private int fadeButtonsHeight = AndroidUtilities.dp(63);
    private int buttonsPanelHeight = AndroidUtilities.dp(61.66f);
    private final int avatarSmallSizeDp = 42;

    public boolean DRAW_USING_RENDERNODE() {
        return Build.VERSION.SDK_INT >= 31 && SharedConfig.useNewBlur;
    }

    //blur variables
    public boolean blurIsRunningAvatarCircle;
    public boolean blurGeneratingTuskIsRunningAvatarCircle;
    BlurBitmap currentBitmapAvatarCircle;
    BlurBitmap prevBitmapAvatarCircle;
    public ArrayList<BlurBitmap> unusedBitmapsAvatarCircle = new ArrayList<>(10);
    public ArrayList<View> blurBehindViewsAvatarCircle = new ArrayList<>();

    Matrix matrixAvatarCircle = new Matrix();
    Matrix matrix2AvatarCircle = new Matrix();
    public Paint blurPaintTopAvatarCircle = new Paint();
    public Paint blurPaintTop2AvatarCircle = new Paint();
    private Paint selectedBlurPaintAvatarCircle;
    private Paint selectedBlurPaint2AvatarCircle;

    public float blurCrossfadeProgressAvatarCircle;
    private final float DOWN_SCALEAvatarCircle = 12f;
    private static DispatchQueue blurQueue;
    ValueAnimator blurCrossfadeAvatarCircle;
    public boolean invalidateBlurAvatarCircle = true;
    int countAvatarCircle;
    int timesAvatarCircle;
    int count2AvatarCircle;
    int times2AvatarCircle;
    final BlurBackgroundTaskAvatarCircle blurBackgroundTaskAvatarCircle = new BlurBackgroundTaskAvatarCircle();

    private RenderNode[] blurNodesAvatarCircle;
    private boolean[] blurNodeInvalidatedThisFrameAvatarCircle = new boolean[3];
    private boolean[] blurNodeInvalidatedAvatarCircle = new boolean[3];
    public static boolean drawingBlurAvatarCircle;

    public void startBlurAvatarCircle() {
        if (!blurIsRunningAvatarCircle || blurGeneratingTuskIsRunningAvatarCircle || !invalidateBlurAvatarCircle || DRAW_USING_RENDERNODE()) {
            return;
        }

        int lastW = AndroidUtilities.dp(avatarSmallSizeDp);
        int lastH = AndroidUtilities.dp(avatarSmallSizeDp);
        if (lastW == 0 || lastH == 0) {
            return;
        }

        //invalidateBlurAvatarCircle = false;
        blurGeneratingTuskIsRunningAvatarCircle = true;

        float scale = 1f;//lerp(normalize(collapseProgress, 0.33f, 1f), 1f, DOWN_SCALEAvatarCircle);
        int bitmapH = (int) (lastH / scale);
        int bitmapW = (int) (lastW / scale);

        long time = System.currentTimeMillis();
        BlurBitmap bitmap = null;
        if (unusedBitmapsAvatarCircle.size() > 0) {
            bitmap = unusedBitmapsAvatarCircle.remove(unusedBitmapsAvatarCircle.size() - 1);
        }

        if (bitmap == null) {
            bitmap = new BlurBitmap();
            bitmap.topBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.topCanvas = new SimplerCanvas(bitmap.topBitmap);
        } else {
            bitmap.topBitmap.eraseColor(Color.TRANSPARENT);
        }

        BlurBitmap finalBitmap = bitmap;

        float sX = (float) finalBitmap.topBitmap.getWidth() / (float) lastW;
        float sY = (float) finalBitmap.topBitmap.getHeight() / (float) lastH;
        int saveCount = finalBitmap.topCanvas.save();
        finalBitmap.pixelFixOffset = 0;

        finalBitmap.topCanvas.clipRect(0, 0, finalBitmap.topBitmap.getWidth(), finalBitmap.topBitmap.getHeight());
        finalBitmap.topCanvas.scale(sX, sY);
        //finalBitmap.topCanvas.translate(0, 0);

        finalBitmap.topScaleX = 1f / sX;
        finalBitmap.topScaleY = 1f / sY;

        if (avatarImage != null) {
            avatarImage.draw(finalBitmap.topCanvas);
        }

        try {
            finalBitmap.topCanvas.restoreToCount(saveCount);
        } catch (Exception e) {
            FileLog.e(e);
        }

        times2AvatarCircle += System.currentTimeMillis() - time;
        count2AvatarCircle++;
        if (count2AvatarCircle >= 20) {
            count2AvatarCircle = 0;
            times2AvatarCircle = 0;
        }

        if (blurQueue == null) {
            blurQueue = new DispatchQueue("BlurQueue");
        }
        blurBackgroundTaskAvatarCircle.radius = (int) lerp(0, 25, normalize(collapseProgress, 0.33f, 1f));
        blurBackgroundTaskAvatarCircle.finalBitmap = finalBitmap;
        blurQueue.postRunnable(blurBackgroundTaskAvatarCircle);
    }

    public float collapseProgress = 0f;
    private int insetStories = 0;

    public void setInsetStories(int inset) {
        insetStories = inset;
        invalidate();
    }

    public void invalidateBlurAvatarCircle() {
        invalidateBlurAvatarCircle = true;
        if (!blurIsRunningAvatarCircle || blurGeneratingTuskIsRunningAvatarCircle) {
            return;
        }
        invalidate();
    }

    private class BlurBackgroundTaskAvatarCircle implements Runnable {

        int radius;
        BlurBitmap finalBitmap;

        @Override
        public void run() {
            long time = System.currentTimeMillis();
            if (radius > 0) {
                if (collapseProgress > 0.33f) {
                    Canvas canvas = new Canvas(finalBitmap.topBitmap);
                    float cx = finalBitmap.topBitmap.getWidth() / 2f;
                    float cy = cx;
                    float circleRadius = cx;
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(AndroidUtilities.dp(16) * normalize(collapseProgress, 0.33f, 1f));
                    paint.setColor(Color.BLACK);
                    canvas.drawCircle(
                            cx,
                            cy,
                            circleRadius,
                            paint);

                    Paint fillPaint = new Paint();
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255f * normalize(collapseProgress, 0.33f, 1f))));
                    canvas.drawCircle(
                            cx,
                            cy,
                            circleRadius,
                            fillPaint
                    );
                }
                Utilities.stackBlurBitmap(finalBitmap.topBitmap, radius);
            }

            timesAvatarCircle += System.currentTimeMillis() - time;
            countAvatarCircle++;
            if (countAvatarCircle > 1000) {
                FileLog.d("chat blur generating average time" + (timesAvatarCircle / (float) countAvatarCircle));
                countAvatarCircle = 0;
                timesAvatarCircle = 0;
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (!blurIsRunningAvatarCircle) {
                    if (finalBitmap != null) {
                        finalBitmap.recycle();
                    }
                    blurGeneratingTuskIsRunningAvatarCircle = false;
                    return;
                }
                prevBitmapAvatarCircle = currentBitmapAvatarCircle;
                BlurBitmap oldBitmap = currentBitmapAvatarCircle;
                blurPaintTop2AvatarCircle.setShader(blurPaintTopAvatarCircle.getShader());

                BitmapShader bitmapShader = new BitmapShader(finalBitmap.topBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                blurPaintTopAvatarCircle.setShader(bitmapShader);

                if (blurCrossfadeAvatarCircle != null) {
                    blurCrossfadeAvatarCircle.cancel();
                }
                blurCrossfadeProgressAvatarCircle = 0;
                blurCrossfadeAvatarCircle = ValueAnimator.ofFloat(0, 1f);
                blurCrossfadeAvatarCircle.addUpdateListener(valueAnimator -> {
                    blurCrossfadeProgressAvatarCircle = (float) valueAnimator.getAnimatedValue();
                    invalidateBlurredViewsAvatarCircle();
                });
                blurCrossfadeAvatarCircle.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurCrossfadeProgressAvatarCircle = 1f;
                        unusedBitmapsAvatarCircle.add(oldBitmap);
                        blurPaintTop2AvatarCircle.setShader(null);
                        invalidateBlurredViewsAvatarCircle();
                        super.onAnimationEnd(animation);
                    }
                });
                blurCrossfadeAvatarCircle.setDuration(50);
                blurCrossfadeAvatarCircle.start();
                invalidateBlurredViewsAvatarCircle();
                currentBitmapAvatarCircle = finalBitmap;

                AndroidUtilities.runOnUIThread(() -> {
                    blurGeneratingTuskIsRunningAvatarCircle = false;
                    startBlurAvatarCircle();
                }, 16);
            });
        }
    }

    public void updateBlurContentAvatarCircle() {
        if (DRAW_USING_RENDERNODE()) {
            invalidateBlurredViewsAvatarCircle();
        }
    }

    public void invalidateBlurredViewsAvatarCircle() {
        blurNodeInvalidatedAvatarCircle[0] = true;
        blurNodeInvalidatedAvatarCircle[1] = true;
        for (int i = 0; i < blurBehindViewsAvatarCircle.size(); i++) {
            blurBehindViewsAvatarCircle.get(i).invalidate();
        }
    }

    private Paint blurAvatarPaint = new Paint();

    public void drawBlurAvatarCollapse(Canvas canvas, Rect rectTmp, float progress) {
        float cx = AndroidUtilities.dp(avatarSmallSizeDp)/2f;
        float cy = cx;
        float radius = cx;
        if (DRAW_USING_RENDERNODE()) {
            if (!canvas.isHardwareAccelerated()) {
                blurAvatarPaint.setStyle(Paint.Style.FILL);
                blurAvatarPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int)(255f * normalize(collapseProgress, 0.33f, 1f))));
                canvas.drawCircle(
                        cx,
                        cy,
                        radius,
                        blurAvatarPaint
                );
                return;
            }
            if (blurNodesAvatarCircle == null) {
                blurNodesAvatarCircle = new RenderNode[3];
            }
            final float scale = getRenderNodeScale();
            final int a = 1;
            if (blurNodesAvatarCircle[a] == null) {
            }
            blurNodesAvatarCircle[a] = new RenderNode("blurNode" + a);
            blurNodesAvatarCircle[a].setRenderEffect(
                    RenderEffect.createBlurEffect(
                            normalize(collapseProgress, 0.33f, 1f) * 12,
                            normalize(collapseProgress, 0.33f, 1f) * 12,
                            Shader.TileMode.CLAMP
                    )
            );
            int lastW = AndroidUtilities.dp(avatarSmallSizeDp);
            int lastH = AndroidUtilities.dp(avatarSmallSizeDp);
            blurNodesAvatarCircle[a].setPosition(
                    0,
                    0,
                    (int) (lastW  / scale),
                    (int) (lastH / scale )
            );
            blurNodesAvatarCircle[a].setClipToOutline(true);
            Outline outline = new Outline();
            outline.setRoundRect(
                    0,
                    0,
                    (int) (lastW  / scale),
                    (int) (lastH  / scale),
                    (int) (lastW / 2f)
            );
            blurNodesAvatarCircle[a].setOutline(outline);
            RecordingCanvas recordingCanvas = blurNodesAvatarCircle[a].beginRecording();
            drawingBlurAvatarCircle = true;
            recordingCanvas.scale(1f / scale, 1f / scale);
            if (avatarImage != null) {
                avatarImage.draw(recordingCanvas);
                if (collapseProgress > 0) {
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(AndroidUtilities.dp(16f) * normalize(collapseProgress, 0.33f, 1f));
                    paint.setColor(Color.BLACK); // Цвет границы
                    recordingCanvas.drawCircle(lastW/2f, lastH/2f, lastW / 2f, paint);

                    Paint fillPaint = new Paint();
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int)(255f * normalize(collapseProgress, 0.33f, 1f))));
                    recordingCanvas.drawCircle(lastW/2f, lastH/2f, lastW / 2f, fillPaint);
                }
            }
            drawingBlurAvatarCircle = false;
            blurNodesAvatarCircle[a].endRecording();
            blurNodeInvalidatedThisFrameAvatarCircle[a] = true;
            blurNodeInvalidatedAvatarCircle[a] = true;
            invalidateBlurredViewsAvatarCircle();

            canvas.save();
            canvas.scale(scale, scale);
            canvas.drawRenderNode(blurNodesAvatarCircle[a]);
            canvas.restore();
            return;
        }
        if (currentBitmapAvatarCircle == null) {
            blurAvatarPaint.setStyle(Paint.Style.FILL);
            blurAvatarPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int)(255f * normalize(collapseProgress, 0.33f, 1f))));
            canvas.drawCircle(
                    cx,
                    cy,
                    radius,
                    blurAvatarPaint
            );
            return;
        }

        updateBlurShaderPositionAvatarCircle();
        if (blurCrossfadeProgressAvatarCircle != 1f && selectedBlurPaint2AvatarCircle.getShader() != null) {
            canvas.drawCircle(
                    cx,
                    cy,
                    radius,
                    selectedBlurPaint2AvatarCircle
            );
            canvas.saveLayerAlpha(rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom, (int) (blurCrossfadeProgressAvatarCircle * 255), Canvas.ALL_SAVE_FLAG);
            canvas.drawCircle(
                    cx,
                    cy,
                    radius,
                    selectedBlurPaintAvatarCircle
            );
            canvas.restore();
        } else {
            canvas.drawCircle(
                    cx,
                    cy,
                    radius,
                    selectedBlurPaintAvatarCircle
            );
        }
    }

    private void updateBlurShaderPositionAvatarCircle() {
        selectedBlurPaintAvatarCircle = blurPaintTopAvatarCircle;
        selectedBlurPaint2AvatarCircle = blurPaintTop2AvatarCircle;

        if (selectedBlurPaintAvatarCircle.getShader() != null) {
            matrixAvatarCircle.reset();
            matrix2AvatarCircle.reset();
            matrixAvatarCircle.preScale(currentBitmapAvatarCircle.topScaleX, currentBitmapAvatarCircle.topScaleY);

            if (prevBitmapAvatarCircle != null) {
                matrix2AvatarCircle.preScale(prevBitmapAvatarCircle.topScaleX, prevBitmapAvatarCircle.topScaleY);
            }

            selectedBlurPaintAvatarCircle.getShader().setLocalMatrix(matrixAvatarCircle);
            if (selectedBlurPaint2AvatarCircle.getShader() != null) {
                selectedBlurPaint2AvatarCircle.getShader().setLocalMatrix(matrixAvatarCircle);
            }
        }
    }

    public boolean blurIsRunningAvatarFull;
    public boolean blurGeneratingTuskIsRunningAvatarFull;
    BlurBitmap currentBitmapAvatarFull;
    BlurBitmap prevBitmapAvatarFull;
    public ArrayList<BlurBitmap> unusedBitmapsAvatarFull = new ArrayList<>(10);
    public ArrayList<View> blurBehindViewsAvatarFull = new ArrayList<>();

    Matrix matrixAvatarFull = new Matrix();
    Matrix matrix2AvatarFull = new Matrix();
    public Paint blurPaintTopAvatarFull = new Paint();
    public Paint blurPaintTop2AvatarFull = new Paint();
    private Paint selectedBlurPaintAvatarFull;
    private Paint selectedBlurPaint2AvatarFull;

    public float blurCrossfadeProgressAvatarFull;
    private final float DOWN_SCALEAvatarFull = 12f;
    ValueAnimator blurCrossfadeAvatarFull;
    public boolean invalidateBlurAvatarFull = true;
    int countAvatarFull;
    int timesAvatarFull;
    int count2AvatarFull;
    int times2AvatarFull;
    final BlurBackgroundTaskAvatarFull blurBackgroundTaskAvatarFull = new BlurBackgroundTaskAvatarFull();

    private RenderNode[] blurNodesAvatarFull;
    private boolean[] blurNodeInvalidatedThisFrameAvatarFull = new boolean[3];
    private boolean[] blurNodeInvalidatedAvatarFull = new boolean[3];
    public static boolean drawingBlurAvatarFull;

    public void invalidateBlurAvatarFull() {
        invalidateBlurAvatarFull = true;
        if (!blurIsRunningAvatarFull || blurGeneratingTuskIsRunningAvatarFull) {
            return;
        }
        invalidate();
    }


    public void startBlurAvatarFull() {
        if (!blurIsRunningAvatarFull || blurGeneratingTuskIsRunningAvatarFull || !invalidateBlurAvatarFull || DRAW_USING_RENDERNODE()) {
            return;
        }

        int lastW = listView.getMeasuredWidth();
        int lastH = fadeButtonsHeight + buttonsPanelHeight;
        if (lastW == 0 || lastH == 0) return;

        //invalidateBlurAvatarFull = false;
        blurGeneratingTuskIsRunningAvatarFull = true;

        int bitmapH = (int) ((lastH) / DOWN_SCALEAvatarFull);
        int bitmapW = (int) ((lastW) / DOWN_SCALEAvatarFull);

        long time = System.currentTimeMillis();
        BlurBitmap bitmap = null;
        if (unusedBitmapsAvatarFull.size() > 0) {
            bitmap = unusedBitmapsAvatarFull.remove(unusedBitmapsAvatarFull.size() - 1);
        }

        if (bitmap == null) {
            bitmap = new BlurBitmap();
            bitmap.topBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.topCanvas = new SimplerCanvas(bitmap.topBitmap);
        } else {
            bitmap.topBitmap.eraseColor(Color.TRANSPARENT);
        }

        BlurBitmap finalBitmap = bitmap;

        float sX = (float) finalBitmap.topBitmap.getWidth() / (float) lastW;
        float sY = (float) finalBitmap.topBitmap.getHeight() / (float) lastH;
        int saveCount = finalBitmap.topCanvas.save();
        finalBitmap.pixelFixOffset = 0;

        finalBitmap.topCanvas.clipRect(0, 0, finalBitmap.topBitmap.getWidth(), finalBitmap.topBitmap.getHeight());
        finalBitmap.topCanvas.scale(sX, sY);
        //finalBitmap.topCanvas.translate(0, 0);

        finalBitmap.topScaleX = 1f / sX;
        finalBitmap.topScaleY = 1f / sY;

        if (viewPager != null) {
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            int saveCount2 = finalBitmap.topCanvas.save();
            finalBitmap.topCanvas.translate(0, -(listView.getMeasuredWidth() - fadeButtonsHeight));
            viewPager.draw(finalBitmap.topCanvas);

            // TODO Move to background thread
            Bitmap bitmap2 = Bitmap.createBitmap(getWidth(), 1, Bitmap.Config.ARGB_8888);
            Canvas tempCanvas = new Canvas(bitmap2);
            tempCanvas.translate(0, -listView.getMeasuredWidth()+1);
            viewPager.draw(tempCanvas);
            Paint paint2 = new Paint();
            paint2.setShader(new BitmapShader(
                    bitmap2,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.CLAMP
            ));
            finalBitmap.topCanvas.drawRect(
                    0, listView.getMeasuredWidth(),
                    listView.getMeasuredWidth(),
                    listView.getMeasuredWidth() + buttonsPanelHeight,
                    paint2
            );

            finalBitmap.topCanvas.restoreToCount(saveCount2);
        }

        try {
            finalBitmap.topCanvas.restoreToCount(saveCount);
        } catch (Exception e) {
            FileLog.e(e);
        }

        times2AvatarFull += System.currentTimeMillis() - time;
        count2AvatarFull++;
        if (count2AvatarFull >= 20) {
            count2AvatarFull = 0;
            times2AvatarFull = 0;
        }

        if (blurQueue == null) {
            blurQueue = new DispatchQueue("BlurQueue");
        }
        blurBackgroundTaskAvatarFull.radius = (int) ((int) (Math.max(6, Math.max(lastH, lastW) / 180) * 2.5f) * BlurSettingsBottomSheet.blurRadius);
        blurBackgroundTaskAvatarFull.finalBitmap = finalBitmap;
        blurQueue.postRunnable(blurBackgroundTaskAvatarFull);
    }

    private class BlurBackgroundTaskAvatarFull implements Runnable {

        int radius;
        BlurBitmap finalBitmap;

        @Override
        public void run() {
            long time = System.currentTimeMillis();

            Utilities.stackBlurBitmap(finalBitmap.topBitmap, radius);
            timesAvatarFull += System.currentTimeMillis() - time;
            countAvatarFull++;
            if (countAvatarFull > 1000) {
                FileLog.d("chat blur generating average time" + (timesAvatarFull / (float) countAvatarFull));
                countAvatarFull = 0;
                timesAvatarFull = 0;
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (!blurIsRunningAvatarFull) {
                    if (finalBitmap != null) {
                        finalBitmap.recycle();
                    }
                    blurGeneratingTuskIsRunningAvatarFull = false;
                    return;
                }
                prevBitmapAvatarFull = currentBitmapAvatarFull;
                BlurBitmap oldBitmap = currentBitmapAvatarFull;
                blurPaintTop2AvatarFull.setShader(blurPaintTopAvatarFull.getShader());

                Canvas canvas = new Canvas(finalBitmap.topBitmap);

                Paint maskPaint = new Paint();
                LinearGradient gradient = new LinearGradient(
                        0, (fadeButtonsHeight - AndroidUtilities.dp(16))/finalBitmap.topScaleY, 0, 0,
                        new int[]{Color.BLACK, Color.TRANSPARENT},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP
                );
                maskPaint.setShader(gradient);
                maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                canvas.drawRect(0, 0, listView.getMeasuredWidth(), fadeButtonsHeight, maskPaint);

                BitmapShader bitmapShader = new BitmapShader(finalBitmap.topBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                blurPaintTopAvatarFull.setShader(bitmapShader);

                if (blurCrossfadeAvatarFull != null) {
                    blurCrossfadeAvatarFull.cancel();
                }
                blurCrossfadeProgressAvatarFull = 0;
                blurCrossfadeAvatarFull = ValueAnimator.ofFloat(0, 1f);
                blurCrossfadeAvatarFull.addUpdateListener(valueAnimator -> {
                    blurCrossfadeProgressAvatarFull = (float) valueAnimator.getAnimatedValue();
                    invalidateBlurredViewsAvatarFull();
                });
                blurCrossfadeAvatarFull.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurCrossfadeProgressAvatarFull = 1f;
                        unusedBitmapsAvatarFull.add(oldBitmap);
                        blurPaintTop2AvatarFull.setShader(null);
                        invalidateBlurredViewsAvatarFull();
                        super.onAnimationEnd(animation);
                    }
                });
                blurCrossfadeAvatarFull.setDuration(50);
                blurCrossfadeAvatarFull.start();
                invalidateBlurredViewsAvatarFull();
                currentBitmapAvatarFull = finalBitmap;

                AndroidUtilities.runOnUIThread(() -> {
                    blurGeneratingTuskIsRunningAvatarFull = false;
                    startBlurAvatarFull();
                }, 16);
            });
        }
    }

    public void updateBlurContentAvatarFull() {
        if (DRAW_USING_RENDERNODE()) {
            invalidateBlurredViewsAvatarFull();
        }
    }

    public void invalidateBlurredViewsAvatarFull() {
        blurNodeInvalidatedAvatarFull[0] = true;
        blurNodeInvalidatedAvatarFull[1] = true;
        for (int i = 0; i < blurBehindViewsAvatarFull.size(); i++) {
            blurBehindViewsAvatarFull.get(i).invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!blurIsRunningAvatarFull) {
            blurIsRunningAvatarFull = true;
            invalidateBlurAvatarFull = true;
        }
        if (!blurIsRunningAvatarCircle) {
            blurIsRunningAvatarCircle = true;
            invalidateBlurAvatarCircle = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurPaintTopAvatarFull.setShader(null);
        blurPaintTop2AvatarFull.setShader(null);
        if (blurCrossfadeAvatarFull != null) {
            blurCrossfadeAvatarFull.cancel();
        }
        if (currentBitmapAvatarFull != null) {
            currentBitmapAvatarFull.recycle();
            currentBitmapAvatarFull = null;
        }
        for (int i = 0; i < unusedBitmapsAvatarFull.size(); i++) {
            if (unusedBitmapsAvatarFull.get(i) != null) {
                unusedBitmapsAvatarFull.get(i).recycle();
            }
        }
        unusedBitmapsAvatarFull.clear();
        blurIsRunningAvatarFull = false;

        blurPaintTopAvatarCircle.setShader(null);
        blurPaintTop2AvatarCircle.setShader(null);
        if (blurCrossfadeAvatarCircle != null) {
            blurCrossfadeAvatarCircle.cancel();
        }
        if (currentBitmapAvatarCircle != null) {
            currentBitmapAvatarCircle.recycle();
            currentBitmapAvatarCircle = null;
        }
        for (int i = 0; i < unusedBitmapsAvatarCircle.size(); i++) {
            if (unusedBitmapsAvatarCircle.get(i) != null) {
                unusedBitmapsAvatarCircle.get(i).recycle();
            }
        }
        unusedBitmapsAvatarCircle.clear();
        blurIsRunningAvatarCircle = false;

    }

    private float getRenderNodeScale() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return AndroidUtilities.density;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return dp(12);
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return dp(15);
        }
    }

    private float getBlurRadius() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 60;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 4;
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 3;
        }
    }


    public void drawBlurRectFull(Canvas canvas) {
        int lastW = listView.getMeasuredWidth();
        int lastH = fadeButtonsHeight + buttonsPanelHeight;
        Rect rectTmp = new Rect(0, lastW - fadeButtonsHeight, lastW, lastW + buttonsPanelHeight);
        Paint blurScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurScrimPaint.setColor(0x23000000);
        if (DRAW_USING_RENDERNODE()) {
            if (!canvas.isHardwareAccelerated()) {
                canvas.drawRect(rectTmp, blurScrimPaint);
                return;
            }
            if (blurNodesAvatarFull == null) {
                blurNodesAvatarFull = new RenderNode[3];
            }
            final float scale = getRenderNodeScale();
            final int a = 2;
            if (blurNodesAvatarFull[a] == null) {
            }
            blurNodesAvatarFull[a] = new RenderNode("blurNode" + a);

            LinearGradient gradient = new LinearGradient(
                    0, 0, 0, fadeButtonsHeight - AndroidUtilities.dp(16),
                    new int[]{Color.BLACK, Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP
            );

            blurNodesAvatarFull[a].setRenderEffect(
                    RenderEffect.createBlendModeEffect(
                            RenderEffect.createBlurEffect(100, 100, Shader.TileMode.MIRROR),
                            RenderEffect.createShaderEffect(gradient),
                            BlendMode.DST_OUT
                    )
            );
            blurNodesAvatarFull[a].setPosition(0, listView.getMeasuredWidth() - fadeButtonsHeight, (int) (lastW), (int) (listView.getMeasuredWidth() + buttonsPanelHeight));
            RecordingCanvas recordingCanvas = blurNodesAvatarFull[a].beginRecording();
            drawingBlurAvatarFull = true;
            if (viewPager != null) {
                int saveCount2 = recordingCanvas.save();
                recordingCanvas.translate(0, -(listView.getMeasuredWidth() - fadeButtonsHeight));
                viewPager.draw(recordingCanvas);
                Bitmap bitmap = Bitmap.createBitmap(getWidth(), 1, Bitmap.Config.ARGB_8888);
                Canvas tempCanvas = new Canvas(bitmap);
                tempCanvas.translate(0, -listView.getMeasuredWidth()+1);
                viewPager.draw(tempCanvas);
                Paint paint = new Paint();
                paint.setShader(new BitmapShader(
                        bitmap,
                        Shader.TileMode.REPEAT,
                        Shader.TileMode.CLAMP
                ));
                recordingCanvas.drawRect(
                        0, listView.getMeasuredWidth(),
                        listView.getMeasuredWidth(),
                        listView.getMeasuredWidth() + buttonsPanelHeight,
                        paint
                );

                recordingCanvas.restoreToCount(saveCount2);
            }
            drawingBlurAvatarFull = false;
            blurNodesAvatarFull[a].endRecording();

            blurNodeInvalidatedThisFrameAvatarFull[a] = true;
            blurNodeInvalidatedAvatarFull[a] = true;
            invalidateBlurredViewsAvatarFull();

            canvas.drawRenderNode(blurNodesAvatarFull[a]);
            return;
        }
        if (currentBitmapAvatarFull == null) {
            canvas.drawRect(rectTmp, blurScrimPaint);
            return;
        }

        updateBlurShaderPosition();
        if (blurCrossfadeProgressAvatarFull != 1f && selectedBlurPaint2AvatarFull.getShader() != null) {
            //canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaint2AvatarFull);
            canvas.saveLayerAlpha(rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom, (int) (blurCrossfadeProgressAvatarFull * 255), Canvas.ALL_SAVE_FLAG);
            //canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaintAvatarFull);
            canvas.restore();
        } else {
            //canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaintAvatarFull);
        }

        //blurScrimPaint.setColor(Color.BLUE);
        //blurScrimPaint.setAlpha(120);
        //canvas.drawRect(rectTmp, blurScrimPaint);
    }

    private void updateBlurShaderPosition() {
        int lastW = listView.getMeasuredWidth();
        int lastH = fadeButtonsHeight + buttonsPanelHeight;
        Rect rectTmp = new Rect(0, lastW - fadeButtonsHeight, lastW, lastW + buttonsPanelHeight);

        selectedBlurPaintAvatarFull = blurPaintTopAvatarFull;
        selectedBlurPaint2AvatarFull = blurPaintTop2AvatarFull;

        if (selectedBlurPaintAvatarFull.getShader() != null) {
            matrixAvatarFull.reset();
            matrix2AvatarFull.reset();
            matrixAvatarFull.setTranslate(0, rectTmp.top);
            matrixAvatarFull.preScale(currentBitmapAvatarFull.topScaleX, currentBitmapAvatarFull.topScaleY);

            if (prevBitmapAvatarFull != null) {
                matrix2AvatarFull.setTranslate(0, rectTmp.top);
                matrix2AvatarFull.preScale(prevBitmapAvatarFull.topScaleX, prevBitmapAvatarFull.topScaleY);
            }

            selectedBlurPaintAvatarFull.getShader().setLocalMatrix(matrixAvatarFull);
            if (selectedBlurPaint2AvatarFull.getShader() != null) {
                selectedBlurPaint2AvatarFull.getShader().setLocalMatrix(matrixAvatarFull);
            }
        }
    }
    public View viewPager = null;


    private static class BlurBitmap {
        int pixelFixOffset;
        Canvas topCanvas;
        Bitmap topBitmap;
        float topScaleX, topScaleY;

        public void recycle() {
            topBitmap.recycle();
        }
    }

    public static class SimplerCanvas extends Canvas {
        public SimplerCanvas(Bitmap bitmap) {
            super(bitmap);
        }

        // all calls to render text for blur should be replaced with drawRect using SpoilerEffect.layoutDrawMaybe

        @Override
        public void drawText(@NonNull char[] text, int index, int count, float x, float y, @NonNull Paint paint) {
            // NOP
//            super.drawText(text, index, count, x, y, paint);
        }

        @Override
        public void drawText(@NonNull String text, int start, int end, float x, float y, @NonNull Paint paint) {
            // NOP
//            super.drawText(text, start, end, x, y, paint);
        }

        @Override
        public void drawText(@NonNull String text, float x, float y, @NonNull Paint paint) {
            // NOP
//            super.drawText(text, x, y, paint);
        }

        @Override
        public void drawText(@NonNull CharSequence text, int start, int end, float x, float y, @NonNull Paint paint) {
            // NOP
//            super.drawText(text, start, end, x, y, paint);
        }

        @Override
        public void drawTextRun(@NonNull CharSequence text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, @NonNull Paint paint) {
            // NOP
//            super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
        }

        @Override
        public void drawTextRun(@NonNull MeasuredText text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, @NonNull Paint paint) {
            // NOP
//            super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
        }

        @Override
        public void drawTextRun(@NonNull char[] text, int index, int count, int contextIndex, int contextCount, float x, float y, boolean isRtl, @NonNull Paint paint) {
            // NOP
//            super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
        }

        @Override
        public void drawTextOnPath(@NonNull char[] text, int index, int count, @NonNull Path path, float hOffset, float vOffset, @NonNull Paint paint) {
            // NOP
//            super.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint);
        }

        @Override
        public void drawTextOnPath(@NonNull String text, @NonNull Path path, float hOffset, float vOffset, @NonNull Paint paint) {
            // NOP
//            super.drawTextOnPath(text, path, hOffset, vOffset, paint);
        }

        @Override
        public boolean clipPath(@NonNull Path path) {
            // NOP
            return false;
//            return super.clipPath(path);
        }

        @Override
        public boolean clipPath(@NonNull Path path, @NonNull Region.Op op) {
            // NOP
            return false;
//            return super.clipPath(path, op);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        blurNodeInvalidatedThisFrameAvatarFull[0] = false;
        blurNodeInvalidatedThisFrameAvatarFull[1] = false;
        blurNodeInvalidatedThisFrameAvatarFull[2] = false;
        if (blurIsRunningAvatarFull) {
            startBlurAvatarFull();
        }

        blurNodeInvalidatedThisFrameAvatarCircle[0] = false;
        blurNodeInvalidatedThisFrameAvatarCircle[1] = false;
        blurNodeInvalidatedThisFrameAvatarCircle[2] = false;
        if (blurIsRunningAvatarCircle) {
            startBlurAvatarCircle();
        }

        super.dispatchDraw(canvas);
    }


    @NonNull
    private final LiquidDrawable liquidDrawable;
    @NonNull
    private final ImageView pageIndicatorRenderView;

    @NonNull
    private final FrameLayout avatarContainer;
    @NonNull
    private final View listView;
    @Nullable
    public View avatarImage;

    public LiquidLayout(Context context, @NonNull FrameLayout avatarContainer, @NonNull View listView) {
        super(context);
        this.avatarContainer = avatarContainer;
        this.listView = listView;

        pageIndicatorRenderView = new ImageView(context);
        addView(pageIndicatorRenderView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        liquidDrawable = new LiquidDrawable();
        pageIndicatorRenderView.setImageDrawable(liquidDrawable);
    }
    
    public void setProgress(float progress) {
        liquidDrawable.progress = progress;
        liquidDrawable.calculateCoordinates();
    }

    public void setLiquidEnabled(boolean enabled) {
        pageIndicatorRenderView.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void invalidateSelf() {
        liquidDrawable.invalidateSelf();
    }

    public void setXY() {
        final float ax = avatarContainer.getX() - AndroidUtilities.dpf2(3.5f) * avatarContainer.getScaleX();
        final float ay = avatarContainer.getY() - AndroidUtilities.dpf2(3.5f) * avatarContainer.getScaleX();
        final float aw = (avatarContainer.getWidth()+ (int) 2 * AndroidUtilities.dpf2(3.5f)) * avatarContainer.getScaleX();
        final float ah = (avatarContainer.getHeight()+ (int) 2 * AndroidUtilities.dpf2(3.5f)) * avatarContainer.getScaleY();

        liquidDrawable.setDotSize((int) aw);
        liquidDrawable.calculateCoordinates();
    }

    private Paint createMetaBallsPaint() {
        Paint metaBallsPaint = new Paint();
        float[] colorMatrixArray = new float[] {
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 160f, -255 * 128f
        };
        metaBallsPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(colorMatrixArray)));
        return metaBallsPaint;
    }

    class LiquidDrawable extends Drawable {

        private int dotSize = 1;

        float progress = 0;
        private Paint paint;
        private Path path;

        public LiquidDrawable() {

            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(3);

            path = new Path();
        }

        public void setDotSize(int size) {
            dotSize = size;
            calculateCoordinates();
        }

        void calculateCoordinates() {
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            final float ax = avatarContainer.getX() - AndroidUtilities.dpf2(3.5f) * avatarContainer.getScaleX();
            final float ay = avatarContainer.getY() - AndroidUtilities.dpf2(3.5f) * avatarContainer.getScaleX();
            final float aw = (avatarContainer.getWidth() + (int) 2 * AndroidUtilities.dpf2(3.5f)) * avatarContainer.getScaleX();
            final float ah = (avatarContainer.getHeight() + (int) 2 * AndroidUtilities.dpf2(3.5f)) * avatarContainer.getScaleY();
            dotSize = (int)aw;
            drawDotScaled(canvas);
        }

        private void drawDotScaled(Canvas canvas) {
            final float aw = (avatarContainer.getWidth()) * avatarContainer.getScaleX();

            final float ax = avatarContainer.getX() ;
            final float ay = avatarContainer.getY();
            final float ah = (avatarContainer.getHeight()) * avatarContainer.getScaleY();
            float avatarCenterX = ax + aw / 2.0f;
            float avatarCenterY = ay + ah / 2.0f;

            // Параметры фигуры
            int rectWidth = avatarMediumSize;
            int dipWidth = avatarMediumSize;

            // Начальные координаты (центрируем)
            int startX = (listView.getMeasuredWidth() - rectWidth) / 2;
            int startY = 0;

            int endX = startX + avatarMediumSize;
            int endY = 0;

            float vertexY = avatarMediumSize / 2f;
            float avatarRadius = (avatarMediumSize - 2f * insetStories) / 2f;

            float controlX = (startX + endX) / 2f;
            float controlY = vertexY;
            Path path = new Path();

            float dipHeight = controlY;
            float dipStart = startX;
            float dipEnd = startX + dipWidth;

            float distanceToSelectedIndicator = Math.abs(avatarCenterY);

            if (collapseProgress > 0.33f) {
                float distancePercent;
                if (collapseProgress < 0.66f) {
                    distancePercent = normalize(collapseProgress, 0.33f, 0.66f);
                } else {
                    distancePercent = 1f - normalize(collapseProgress, 0.66f, 1f);
                }
                dipHeight = distancePercent * AndroidUtilities.dp(32);
            } else {
                dipHeight = 0;
            }

            path.reset();

            path.moveTo(startX, 0);
            path.cubicTo(
                    startX + 3f * avatarRadius / 4f, 0,
                    startX + avatarRadius / 4f, dipHeight,
                    controlX, dipHeight
            );
            path.cubicTo(
                    dipEnd - avatarRadius / 4f, dipHeight,
                    dipEnd - 3f * avatarRadius / 4f, 0,
                    dipEnd, 0
            );
            path.lineTo(startX, 0);
            path.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            canvas.drawPath(path, paint);


            float x = ax + aw / 2.0f;
            float y = ay + ah / 2.0f;

            Path path2 = new Path();
            Paint paint2 = new Paint();

            float startX2 = x + insetStories - aw / 2.0f;
            float startY2 = y;

            float endX2 = x + aw / 2.0f - insetStories;
            float endY2 = y;

            float controlX2 = x;
            float controlY2 = ay - dipHeight * 3;

            path2.moveTo(startX2, startY2);
            path2.quadTo(controlX2, controlY2, endX2, endY2);

            path2.lineTo(startX2, startY2);
            path2.close();

            paint2.setStyle(Paint.Style.FILL);
            paint2.setColor(Color.BLACK);
            canvas.drawPath(path2, paint2);

        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            calculateCoordinates();
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}
    }

    private int avatarSmallSize = 0;
    private int avatarMediumSize = 0;

    public void setAvatarSmallSize(int size) {
        avatarSmallSize = size;
    }

    public void setAvatarMediumSize(int size) {
        avatarMediumSize = size;
    }

    public static float normalize(float x, float a, float b) {
        return (x - a) / (b - a);
    }
}
