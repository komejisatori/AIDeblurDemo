package com.example.deblurdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class ProcessView extends View {

    private Paint mPaintRectangle;
    private int m_h_patchnum;
    private int m_w_patchnum;
    private int m_image_h;
    private int m_image_w;
    private int m_h_overlap;
    private int m_w_overlap;
    private int m_patchsize;

    private int m_pos_h = 0;
    private int m_pos_w = 0;
    private int m_level = 0;

    public ProcessView(Context context) {
        super(context);
    }

    public ProcessView(Context context, AttributeSet attrs){
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
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaintRectangle.setStrokeWidth(10);
        mPaintRectangle.setStyle(Paint.Style.STROKE);
        switch (m_level){
            case 0:
                mPaintRectangle.setColor(Color.GREEN);
                break;
            case 1:
                mPaintRectangle.setColor(Color.YELLOW);
                break;
            case 2:
                mPaintRectangle.setColor(Color.RED);
                break;
            default:
                mPaintRectangle.setColor(Color.GREEN);
                break;
        }
        canvas.drawRect(
                new Rect(m_pos_w, m_pos_h, m_pos_w + m_patchsize, m_pos_h + m_patchsize),
                mPaintRectangle
        );
    }

    public void setResult(int patch_h, int patch_w, int level){
        m_pos_h = patch_h * (m_patchsize - m_h_overlap);
        m_pos_w = patch_w * (m_patchsize - m_w_overlap);
        m_level = level;
    }

}
