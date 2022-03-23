package com.example.deblurdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class ResultView extends View {

    private Paint mPaintRectangle;
    private int m_h_patchnum;
    private int m_w_patchnum;
    private int m_image_h;
    private int m_image_w;
    private int m_h_overlap;
    private int m_w_overlap;
    private int m_patchsize;

    private int[] m_level_list;

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
    }

    public void setGrids(int h_patchnum, int w_patchnum, int image_h, int image_w,
                         int h_overlap, int w_overlap, int patchsize){
        m_h_patchnum = h_patchnum;
        m_w_patchnum = w_patchnum;
        m_image_h = image_h;
        m_image_w = image_w;
        m_h_overlap = h_overlap;
        m_w_overlap = w_overlap;
        m_patchsize = patchsize;
        m_level_list = new int[m_h_patchnum * m_w_patchnum];
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaintRectangle.setStyle(Paint.Style.FILL_AND_STROKE);
        for (int i = 0; i < m_w_patchnum * m_h_patchnum; i ++) {
            switch (m_level_list[i]) {
                case 2:
                    mPaintRectangle.setColor(Color.argb(128, 255, 255, 0));
                    break;
                case 1:
                    mPaintRectangle.setColor(Color.argb(128, 255, 165, 0));
                    break;
                case 0:
                    mPaintRectangle.setColor(Color.argb(128, 255, 0, 0));
                    break;
                default:
                    mPaintRectangle.setColor(Color.GREEN);
                    break;
            }
            canvas.drawRect(
                    new Rect(
                            i / m_h_patchnum * (m_patchsize - m_w_overlap),
                            i % m_h_patchnum * (m_patchsize - m_h_overlap),
                            i / m_h_patchnum * (m_patchsize - m_w_overlap) + m_patchsize,
                            i % m_h_patchnum * (m_patchsize - m_h_overlap) + m_patchsize
                    ),
                    mPaintRectangle
            );
        }
    }

    public void setResults(int[] levels){

        for (int i = 0; i < m_w_patchnum * m_h_patchnum; i ++){
            m_level_list[i] = levels[i];
        }
    }

}
