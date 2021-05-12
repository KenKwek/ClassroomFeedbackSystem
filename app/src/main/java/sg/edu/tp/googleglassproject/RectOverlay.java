package sg.edu.tp.googleglassproject;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class RectOverlay extends GraphicOverlay.Graphic{
    // Bounding Box Colour
    private int RECT_COLOR = Color.RED;
    // Bounding Box Thickness
    private float strokeWidth = 2.0f;
    private Paint rectPaint;
    private Rect rect;
    private String predictedName;
    private Bitmap statusBitmap;
    private GraphicOverlay graphicOverlay;

    public RectOverlay(GraphicOverlay graphicOverlay, Rect rect, String predictedName, Bitmap bitmap) {
        super(graphicOverlay);
        this.graphicOverlay = graphicOverlay;
        this.rect = rect;
        this.predictedName = predictedName;
        this.statusBitmap = bitmap;

        rectPaint = new Paint();
        rectPaint.setColor(RECT_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(strokeWidth);
        rectPaint.setTextSize(15);
        postInvalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        /* Draw Rectangle Box for the Bounding Box */
        RectF rectF = new RectF(rect);
        rectF.left = translateX(rectF.left);
        rectF.right = translateX(rectF.right);
        rectF.top = translateY(rectF.top);
        rectF.bottom = translateY(rectF.bottom);
        canvas.drawRect(rectF, rectPaint);

        /* Draw Text above the Bounding Box */
        canvas.drawText(predictedName, rectF.left, rectF.top, rectPaint);

        /* Draw Status Image above Bounding Box */
        // Get the Height & Width of the Status Bitmap Image
        int height = statusBitmap.getHeight();
        int width = statusBitmap.getWidth();

        float statusLeft = rectF.right - width;
        float statusTop = rectF.top - height;
        canvas.drawBitmap(statusBitmap, statusLeft, statusTop, rectPaint);

    }

}
