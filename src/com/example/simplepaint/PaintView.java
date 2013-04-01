/*
 * Copyright 2011 YAMAZAKI Makoto<makoto1975@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.simplepaint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Checkable;
import android.widget.FrameLayout;

/**
 * お絵かき用の {@link View} です。 {@link View} がもともと持っている背景色、commit
 * されたストローク用のオフスクリーンビットマップ、 現在描いている途中のストロークを別々に保持し、描画時に({@link #onDraw(Canvas)}
 * で)合成します。
 */
public class PaintView extends View {

    private static final String TAG = PaintView.class.getSimpleName();

    // android.view.MotionEvent.ACTION_POINTER_INDEX_MASK;
    private static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    // android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    private static final int ACTION_POINTER_INDEX_SHIFT = 8;

    /**
     * サポートする最大のポインタ数。
     */
    private static final int MAX_POINTERS = 20;

    /*
     * for stroke
     */
    private static final float TOUCH_TOLERANCE = 2;

    private static final int DEFAULT_PEN_COLOR = Color.BLACK;

    private final Paint mPaintForPen;

    private int mCurrentMaxPointerCount = 0;

    /**
     * パスの配列。
     *
     * <p>
     * 配列の長さは {@link #MAX_POINTERS} で初期化されます。
     * PointerId を配列のインデックスとして使用します。ストローク中の Pointer は
     * {@code non-null}, ストロークが無いときは {@code null} です。
     * </p>
     */
    private final Path[] mPath;

    /**
     * 各ポインタの未確定パス座標列。
     *
     * <p>
     * 配列の長さは {@link #MAX_POINTERS} で初期化されます。
     * </p>
     */
    private final float[][] mPathCoordinates;

    /**
     * 各ポインタの未確定パス座標列にいくつの座標を保持しているか。
     *
     * <p>
     * 配列の長さは {@link #MAX_POINTERS} で初期化されます。
     * </p>
     */
    private final int[] mPathCoordinateCounts;

    /**
     * 確定したストローク配列。古いストロークから順に並んでいます
     */
    private List<Stroke> mHistory;

    /*
     * for off-screen
     */
    private final Paint mOffScreenPaint;

    private Bitmap mOffScreenBitmap;

    private Canvas mOffScreenCanvas;

    /**
     * 背景色(AARRGGBB)
     */
    private int mBgColor;

    public PaintView(Context c, AttributeSet attrs) {
        super(c, attrs);

        mPaintForPen = new Paint();
        mPaintForPen.setColor(Color.BLACK);
        mPaintForPen.setAntiAlias(true);
        mPaintForPen.setDither(true);
        mPaintForPen.setColor(DEFAULT_PEN_COLOR);
        mPaintForPen.setStyle(Paint.Style.STROKE);
        mPaintForPen.setStrokeJoin(Paint.Join.ROUND);
        mPaintForPen.setStrokeCap(Paint.Cap.ROUND);
        mPaintForPen.setStrokeWidth(12.0F);

        mOffScreenPaint = new Paint(Paint.DITHER_FLAG);
        mOffScreenBitmap = null;
        mOffScreenCanvas = null;

        mPath = new Path[MAX_POINTERS];
        mPathCoordinates = new float[MAX_POINTERS][];
        mPathCoordinateCounts = new int[MAX_POINTERS];
        mHistory = new ArrayList<PaintView.Stroke>();
        clearAllPaths();

        setBackgroundColor(Color.WHITE);
    }

    public Bitmap getBitmap() {
        return mOffScreenBitmap;
    }

    public void setBitmap(Bitmap bmp) {
        mOffScreenBitmap = bmp;
        mOffScreenCanvas = new Canvas(mOffScreenBitmap);
    }

    /**
     * ペンの色をセットします。
     * 
     * @param argb ペンの色(AARRGGBB)。
     */
    public void setPenColor(int argb) {
        mPaintForPen.setColor(argb);
    }

    /**
     * ペンのサイズをセットします。
     *
     * @param size ペンのサイズ。
     */
    public void setPenSize(float size) {
        mPaintForPen.setStrokeWidth(size);
    }

    /**
     * 背景色をセットします。
     * 
     * @param argb 背景色(AARRGGBB)。
     */
    @Override
    public void setBackgroundColor(int argb) {
        mBgColor = argb;
        super.setBackgroundColor(argb);
    }

    /**
     * すべてのストロークを消去します。
     */
    public void clearCanvas() {
        if (mOffScreenBitmap != null) {
            mOffScreenBitmap.eraseColor(0); // 透明に戻す
        }
        clearAllPaths();
        invalidate();
    }

    /**
     * 現在の画像を PNG ファイルとして書き出し、書きだしたファイルを {@link Uri} で返します。
     * 
     * @return 書きだしたファイルの Uri。書き出しが正常に行えなかった場合は {@code null} を返します。
     */
    public Uri saveImageAsPng() {
        final File baseDir = prepareImageBaseDir();
        if (baseDir == null) {
            return null;
        }
        final File imageFile = createImageFileForNew(baseDir, "image-", "png");
        final OutputStream os = openImageFile(imageFile);
        if (os == null) {
            return null;
        }
        try {
            final Bitmap bitmap = createScaledBitmap(1.0f);
            try {
                if (!bitmap.compress(CompressFormat.PNG, 100, os)) {
                    Log.e(TAG,
                            "failed to create image file: "
                                    + imageFile.getPath());
                    return null;
                }
                updateMediaDatabase(imageFile);
                return Uri.fromFile(imageFile);
            } finally {
                bitmap.recycle();
            }
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                Log.e(TAG,
                        "failed to create image file: " + imageFile.getPath());
                return null;
            }
        }
    }

    /**
     * 現在描かれているものをファイルとして保存します。
     *
     * @return 保存したファイルの情報。
     */
    public Files save() {
        final File thumbnailFile = createThumbnailFile();
        if (thumbnailFile == null) {
            return null;
        }
        final File strokeFile = createStrokeFile(thumbnailFile);
        if (strokeFile == null) {
            return null;
        }
        return new Files(strokeFile, thumbnailFile);
    }

    /**
     * 今描かれているストロークを文字列化したものを返します(実際には背景の情報も含まれます)。
     * @return ストローク文字列。
     */
    public String getStrokeString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(mBgColor).append('\n');
        Stroke.listToString(sb, mHistory);
        return sb.toString();
    }

    /**
     * ストローク文字列から画を復元します。今描かれているものは消去されます。
     * 
     * @param strokeString ストローク文字列。
     */
    public void restore(String strokeString) {
        clearCanvas();
        assert mHistory.isEmpty();

        final int delimiterIndex = strokeString.indexOf('\n');
        if (delimiterIndex < 0) {
            return;
        }
        final int bgColor = Integer.parseInt(strokeString.substring(0, delimiterIndex));
        setBackgroundColor(bgColor);

        final List<Stroke> strokes = Stroke.fromListString(strokeString
                .substring(delimiterIndex + 1));
        mHistory.addAll(strokes);

        drawHistoryToOffScreen();
    }

    private void drawHistoryToOffScreen() {
        if (mOffScreenCanvas == null) {
            return;
        }

        // 渡されたストロークをオフスクリーンへ描画する
        final Paint paint = new Paint(mPaintForPen);
        final Path path = new Path();
        for (Stroke stroke : mHistory) {
            paint.setColor(stroke.mColor);
            paint.setStrokeWidth(stroke.mSize);
            float prevX = 0f;
            float prevY = 0f;
            for (int index = 0; index < stroke.mCoordinates.length - 1; index += 2) {
                final float x = stroke.mCoordinates[index];
                final float y = stroke.mCoordinates[index + 1];
                if (index == 0) {
                    // first coordinate
                    path.moveTo(x, y);
                    path.lineTo(x + 1, y);
                    prevX = x;
                    prevY = y;
                } else if (index == stroke.mCoordinates.length - 2) {
                    // last coordinate
                    path.moveTo(x, y);
                } else {
                    path.quadTo(prevX, prevY, (prevX + x) / 2, (prevY + y) / 2);
                    prevX = x;
                    prevY = y;
                }
            }
            mOffScreenCanvas.drawPath(path, paint);
            path.reset();
        }
    }

    public static File[] listStrokeFiles(Context appContext) {
        File baseDir = PaintView.prepareImageBaseDir(appContext);
        File[] strokeFiles = baseDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".stroke");
            }
        });
        return strokeFiles;
    }

    public static void deleteImage(File strokeFile) {
        strokeFile.delete();
        new File(strokePathToThumbnailPath(strokeFile.getPath())).delete();
    }

    /**
     * 渡された {@link Intent} から {@code extraKey} で {@link Serializable} な extra を
     * 取り出し、それが {@link File}オブジェクトであればそのファイルからストローク文字列を読み込みます。
     *
     * @param intent
     * @param extraKey
     * @return
     */
    public static String readStrokeFile(Intent intent, String extraKey) {
        if (intent == null || extraKey == null) {
            return null;
        }
        final Serializable serializable = intent.getSerializableExtra(extraKey);
        if (serializable == null) {
            return null;
        }
        if (!(serializable instanceof File)) {
            return null;
        }
        final File strokeFile = (File) serializable;
        Reader reader = null;
        try {
            final char[] buf = new char[8192];
            final StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new FileReader(strokeFile));
            for (int read = reader.read(buf); 0 <= read; read = reader.read(buf)) {
                sb.append(buf, 0, read);
            }
            return sb.toString();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found.", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "failed to read file", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0 || mOffScreenBitmap != null) {
            return;
        }
        setBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
        drawHistoryToOffScreen();
    }

    /**
     * {@link View} の中身を描画します。親クラスで描画した背景の上にコミット済みのストローク画像を コピーし、最後に
     * {@link #mPath} が保持する未コミットのストロークを描画します。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(mOffScreenBitmap, 0.0F, 0.0F, mOffScreenPaint);
        for (int i = 0; i < mCurrentMaxPointerCount; i++) {
            final Path path = mPath[i];
            if (path == null) {
                continue;
            }
            canvas.drawPath(path, mPaintForPen);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        final int pointerCount = event.getPointerCount();
        for (int pIndex = 0; pIndex < pointerCount; pIndex++) {

            float currentX = event.getX(pIndex);
            float currentY = event.getY(pIndex);

            final int pointerId = event.getPointerId(pIndex);
            if (MAX_POINTERS <= pointerId) {
                Log.i(TAG, "too many pointers(PointerId = " + pointerId + ").");
                return true;
            }
            mCurrentMaxPointerCount = Math.max(mCurrentMaxPointerCount,
                    pointerId + 1);

            switch (getActionMasked(event)) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (getActionIndex(event) != pIndex) {
                        continue;
                    }
                    // 現在の座標から描画開始
                    handleTouchStart(currentX, currentY, pointerId);
                    invalidate(); // 面倒なので View 全体を再描画要求
                    break;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        // 未処理の move イベントを反映させる。
                        handleTouchMove(event.getHistoricalX(pIndex, i),
                                event.getHistoricalY(pIndex, i), pointerId);
                    }
                    // 現在の座標を move として反映する。
                    handleTouchMove(currentX, currentY, pointerId);
                    invalidate(); // 面倒なので View 全体を再描画要求
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (getActionIndex(event) != pIndex) {
                        continue;
                    }
                    // 現在の座標をストローク完了として反映する。
                    handleTouchEnd(currentX, currentY, pointerId);
                    invalidate(); // 面倒なので View 全体を再描画要求
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * {@link MotionEvent} からアクションの種別を取得します。
     *
     * API Level 8 以降では {@code event.getActionMasked()} が使用できるが、
     * API Level 7 に対応するため独自に実装しています。
     * @param event モーションイベント。
     * @return マスクされたアクション。
     */
    private static int getActionMasked(MotionEvent event) {
        final int action = event.getAction();
        final int masked = action & MotionEvent.ACTION_MASK;
        return masked;
    }

    /**
     * {@link MotionEvent} から ポインタのインデックスを取得します。
     *
     * API Level 8 以降では {@code event.getActionIndex()} が使用できるが、
     * API Level 7 に対応するため独自に実装しています。
     * @param event モーションイベント。
     * @return インデックスの値。
     */
    private static int getActionIndex(MotionEvent event) {
        final int action = event.getAction();
        final int index = (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
        return index;
    }

    private File createThumbnailFile() {
        final File baseDir = prepareImageBaseDir();
        if (baseDir == null) {
            return null;
        }
        final File imageFile = createImageFileForNew(baseDir, "thumbnail-", "png");
        final OutputStream os = openImageFile(imageFile);
        if (os == null) {
            return null;
        }
        try {
            final float density = getContext().getResources().getDisplayMetrics().density;
            final float scale = calcScale(dpToPx(150, density), dpToPx(150, density));
            final Bitmap bitmap = createScaledBitmap(scale);
            try {
                if (!bitmap.compress(CompressFormat.PNG, 100, os)) {
                    Log.e(TAG,
                            "failed to create image file: "
                                    + imageFile.getPath());
                    return null;
                }
                return imageFile;
            } finally {
                bitmap.recycle();
            }
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                Log.e(TAG,
                        "failed to create image file: " + imageFile.getPath());
                return null;
            }
        }
    }

    private File createStrokeFile(File thumbnailFile) {
        final File strokeFile = new File(thumbnailFile.getParentFile(), thumbnailFile.getName()
                + ".stroke");
        try {
            FileWriter writer = new FileWriter(strokeFile);
            try {
                writer.write(getStrokeString());
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            Log.e(PaintView.class.getSimpleName(), "failed to write stroke file", e);
            return null;
        }
        return strokeFile;
    }

    private Bitmap createScaledBitmap(float scale) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);

        final Bitmap bitmap = Bitmap.createBitmap(
                (int) (mOffScreenBitmap.getWidth() * scale),
                (int) (mOffScreenBitmap.getHeight() * scale),
                Config.ARGB_8888);
        bitmap.eraseColor(mBgColor);
        final Canvas canvas = new Canvas(bitmap);
        final Matrix m = new Matrix();
        m.postScale(scale, scale);
        canvas.drawBitmap(mOffScreenBitmap, m, paint);

        return bitmap;
    }

    private float calcScale(int maxWidth, int maxHeight) {
        final float width = mOffScreenBitmap.getWidth();
        final float height = mOffScreenBitmap.getHeight();

        final float scaleX = maxWidth / width;
        final float scaleY = maxHeight / height;

        return Math.min(scaleX, scaleY);
    }

    private static int dpToPx(int dp, float density) {
        return Math.round(dp * density);
    }

    public static Bitmap getThumbnailBitmap(File strokeFile) {
        final File thumbnailFile = new File(PaintView.strokePathToThumbnailPath(strokeFile
                .getPath()));

        if (!thumbnailFile.exists()) {
            return null;
        }
        InputStream is = null;
        try {
            is = new FileInputStream(thumbnailFile);
            final Bitmap thumbnail = BitmapFactory.decodeStream(is);
            return thumbnail;
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static String strokePathToThumbnailPath(String strokePath) {
        assert strokePath.endsWith(".stroke");
        return strokePath.substring(0, strokePath.length() - ".stroke".length());
    }

    private void handleTouchStart(float x, float y, int pointerId) {
        preparePath(pointerId);
        assert mPath[pointerId] != null;
        mPath[pointerId].moveTo(x, y);
        // タッチしただけで点が描かれるようにとりあえず１ドット線をひく
        mPath[pointerId].lineTo(x + 1, y);

        // mPath[pointerId] にセットした座標を記憶しておく
        mPathCoordinateCounts[pointerId] = 0;
        if (mPathCoordinates[pointerId] == null) {
            mPathCoordinates[pointerId] = new float[128];
        }
        appendCoordinate(mPathCoordinates, mPathCoordinateCounts, pointerId, x, y);
    }

    private void handleTouchMove(float x, float y, int pointerId) {
        final float[] coordinates = mPathCoordinates[pointerId];
        if (coordinates == null) {
            return;
        }

        final int baseIndex = mPathCoordinateCounts[pointerId] * 2;
        final float prevX = coordinates[baseIndex - 2];
        final float prevY = coordinates[baseIndex - 1];
        if (Math.abs(x - prevX) < TOUCH_TOLERANCE
                && Math.abs(y - prevY) < TOUCH_TOLERANCE) {
            return;
        }
        mPath[pointerId].quadTo(prevX, prevY, (prevX + x) / 2, (prevY + y) / 2);

        // mPath[pointerId] にセットした座標を記憶しておく
        appendCoordinate(mPathCoordinates, mPathCoordinateCounts, pointerId, x, y);
    }

    private void handleTouchEnd(float x, float y, int pointerId) {
        if (mPath[pointerId] == null) {
            return;
        }
        mPath[pointerId].lineTo(x, y);

        // mPath[pointerId] にセットした座標を記憶しておく
        appendCoordinate(mPathCoordinates, mPathCoordinateCounts, pointerId, x, y);
        mHistory.add(new Stroke(mPaintForPen.getColor(), mPaintForPen.getStrokeWidth(),
                mPathCoordinates[pointerId], mPathCoordinateCounts[pointerId]));

        // オフスクリーンにコミットしてパスをクリア
        mOffScreenCanvas.drawPath(mPath[pointerId], mPaintForPen);

        mPath[pointerId].close();
        mPath[pointerId] = null;
    }

    private void appendCoordinate(float[][] coordinates, int[] coordinatesInArray, int pointerId,
            float x, float y) {
        if (coordinates[pointerId] == null) {
            return;
        }

        final int baseIndex = coordinatesInArray[pointerId] * 2;
        coordinates[pointerId] = ensureArrayLength(coordinates[pointerId],
                baseIndex + 2);

        coordinates[pointerId][baseIndex] = x;
        coordinates[pointerId][baseIndex + 1] = y;
        coordinatesInArray[pointerId]++;
    }

    private float[] ensureArrayLength(float[] array, int expectingLength) {
        if (array == null) {
            return null;
        }
        if (expectingLength <= array.length) {
            return array;
        }
        int newLength = array.length;
        while (newLength < expectingLength) {
            if (Integer.MAX_VALUE / 2 <= array.length) {
                newLength = Integer.MAX_VALUE;
            } else {
                newLength = array.length * 2;
            }
        }
        final float[] newArray = new float[newLength];
        System.arraycopy(array, 0, newArray, 0, array.length);
        return newArray;
    }

    private void clearAllPaths() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (mPath[i] != null) {
                mPath[i].close();
            }
            mPath[i] = null;
            mPathCoordinateCounts[i] = 0;
        }
        mHistory.clear();
    }

    private void preparePath(int pointerId) {
        if (mPath[pointerId] == null) {
            mPath[pointerId] = new Path();
        }
        mPath[pointerId].reset();
        mPathCoordinateCounts[pointerId] = 0;
    }

    private File prepareImageBaseDir() {
        return prepareImageBaseDir(getContext().getApplicationContext());
    }

    public static File prepareImageBaseDir(Context appContext) {
        final String appPackage = appContext.getPackageName();
        final File baseDir = new File(
                Environment.getExternalStorageDirectory(), appPackage);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        if (!baseDir.isDirectory()) {
            Log.e(TAG, "not a directory: " + baseDir.getPath());
            return null;
        }
        return baseDir;
    }

    private File createImageFileForNew(File baseDir, String basename, String extention) {
        boolean interrupted = false;
        File imageFile = null;
        do {
            if (imageFile != null) {
                // ２回目以降は少し待つ
                try {
                    TimeUnit.MILLISECONDS.sleep(10L);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            imageFile = new File(baseDir, basename + System.currentTimeMillis()
                    + "." + extention);
        } while (imageFile.exists());

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return imageFile;
    }

    private FileOutputStream openImageFile(File f) {
        try {
            return new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "failed to create image file: " + f.getPath(), e);
            return null;
        }
    }

    /**
     * 画像ファイルがギャラリーに表示されるようにするため、データベースに追加します。
     * 
     * @param imageFile イメージファイル。
     */
    private void updateMediaDatabase(File imageFile) {
        final ContentValues values = new ContentValues();
        ContentResolver contentResolver = getContext().getContentResolver();
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.TITLE, imageFile.getName());
        values.put("_data", imageFile.getAbsolutePath());
        contentResolver.insert(Media.EXTERNAL_CONTENT_URI, values);
    }

    /**
     * 線の軌跡1つ分を保持する immutable なクラスです。
     */
    private static final class Stroke {
        final int mColor;
        final float mSize;
        final float[] mCoordinates;

        /**
         * 指定された色、太さ、座標情報から {@link Stroke} を構築します。
         * 
         * {@code coordinates} に渡された配列から、 {@code nCoordinates} 個分の座標情報を
         * コピーして保持します。
         * 
         * @param color 線の色。 ARGB です。
         * @param size 線の太さ。
         * @param coordinates 軌跡の座標情報。座標情報は x座標値, y座標値が交互に並んでいるものとして扱います。
         * @param nCoordinates {@code coordinates} が保持する有効な座標情報の数。
         */
        public Stroke(int color, float size, float[] coordinates, int nCoordinates) {
            super();
            mColor = color;
            mSize = size;
            mCoordinates = new float[nCoordinates * 2];
            System.arraycopy(coordinates, 0, mCoordinates, 0,
                    Math.min(coordinates.length, mCoordinates.length));
        }

        private Stroke(int color, float size, float[] coordinates) {
            super();
            mColor = color;
            mSize = size;
            mCoordinates = coordinates;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(mColor);
            sb.append(',').append(mSize);
            for (int i = 0; i < mCoordinates.length; i++) {
                sb.append(',').append(mCoordinates[i]);
            }
            return sb.toString();
        }

        @SuppressWarnings("unused")
        public static String listToString(List<Stroke> strokes) {
            final StringBuilder sb = new StringBuilder();
            listToString(sb, strokes);
            return sb.toString();
        }

        private static void listToString(StringBuilder sb, List<Stroke> strokes) {
            if (strokes == null) {
                return;
            }
            for (Stroke stroke : strokes) {
                if (stroke == null) {
                    continue;
                }
                sb.append(stroke.toString()).append('\n');
            }
        }

        public static Stroke fromString(String str) {
            final StringTokenizer tk = new StringTokenizer(str, ",");
            final int tokens = tk.countTokens();
            if (tokens < 2 || (tokens & 1) == 1) {
                return null;
            }
            final int color = Integer.parseInt(tk.nextToken());
            final float width = Float.parseFloat(tk.nextToken());
            final float[] coordinates = new float[tokens - 2];

            for (int index = 0; tk.hasMoreTokens(); index++) {
                coordinates[index] = Float.parseFloat(tk.nextToken());
            }
            return new Stroke(color, width, coordinates);
        }

        public static ArrayList<Stroke> fromListString(String str) {
            final ArrayList<PaintView.Stroke> result = new ArrayList<PaintView.Stroke>();
            if (str == null) {
                return result;
            }
            final StringTokenizer tk = new StringTokenizer(str, "\n");
            while (tk.hasMoreTokens()) {
                final String strokeStr = tk.nextToken();
                if (strokeStr.length() == 0) {
                    continue;
                }
                final Stroke stroke = Stroke.fromString(strokeStr);
                if (stroke == null) {
                    Log.e(Stroke.class.getSimpleName(), "invalid stroke string: " + strokeStr);
                    continue;
                }
                result.add(stroke);
            }
            return result;
        }
    }

    /**
     * ストローク情報とサムネイルのファイルを保持するクラス
     */
    public static final class Files {
        private final File mStrokeFile;
        private final File mThumbnailFile;

        public Files(File strokeFile, File thumbnailFile) {
            super();
            mStrokeFile = strokeFile;
            mThumbnailFile = thumbnailFile;
        }

        /**
         * ストローク文字列を保持するファイル。このファイルの中身を {@link PaintView#restore(String)}
         * に渡すことで描画されているものを復元することができます。
         *
         * @return ストロークファイル。
         */
        public File getStrokeFile() {
            return mStrokeFile;
        }

        /**
         * サムネイル画像のファイル。
         * @return サムネイル画像。
         */
        public File getThumbnailFile() {
            return mThumbnailFile;
        }
    }

    /**
     * checked かどうかに応じて背景色が変わる FrameLayout です。
     */
    public static final class CheckableLayout extends FrameLayout implements Checkable {
        private boolean mChecked;

        private static final ShapeDrawable BG_ON_CHECKED;
        static {
            final ShapeDrawable drawable = new ShapeDrawable();
            drawable.setShape(new RectShape());
            drawable.getPaint().setColor(0xff5757ff); // 青
            BG_ON_CHECKED = drawable;
        }

        public CheckableLayout(Context context) {
            super(context);
        }

        public void setChecked(boolean checked) {
            mChecked = checked;
            setBackgroundDrawable(checked ? BG_ON_CHECKED : null);
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void toggle() {
            setChecked(!mChecked);
        }
    }
}
