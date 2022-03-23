package com.example.deblurdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements Runnable{

    private ImageView mImageView;
    final private int mImageView_h = 1280;
    final private int mImageView_w = 720;

    final private int mImagePatchSize = 272;
    final private int mImage_w_overlap = 48;
    final private int mImage_h_overlap = 20;
    final private int mImage_w_patchnum = 3;
    final private int mImage_h_patchnum = 5;

    private int mMockedLevel = 0;

    private float[] mImageBuffer = new float[3 * mImageView_w * mImageView_h];
    private float[] mLinearGrad_h = new float[mImage_h_overlap];
    private float[] mLinearGrad_w = new float[mImage_w_overlap];
    private int[] mImageLevel = new int[mImage_h_patchnum * mImage_w_patchnum];

    //private FloatBuffer mPatchBuffer = Tensor.allocateFloatBuffer(3 * mImagePatchSize * mImagePatchSize);

    private ProgressBar mProgressbar;
    private Button mGallery;
    private Button mCamera;
    private Button mDeblur;
    private Button mGrid;
    private Button mMock;
    private ProcessView mProcessViewer;
    private ResultView mResultViewer;

    private Bitmap mBitmap = null;
    private Bitmap mDeblurBitmap = null;
    private String mCurrentPhotoPath = "blur.png";
    private String mDeblurPhotoPath = "sharp.png";

    final private String mJudgerNetPath = "uxjudge.ptl";
    final private String mNetdep1Path = "uxdep_1.ptl";
    final private String mNetdep2Path = "uxdep_2.ptl";
    final private String mNetdep3Path = "uxdep_3.ptl";
    final private String mNetdep4Path = "uxdep_4.ptl";
    final private String mNetdep5Path = "uxdep_5.ptl";
    final private String mNetdep6Path = "uxdep_6.ptl";


    private Module mModule = null;

    private Module mBlurJudger = null;
    private Module mModule_1 = null;
    private Module mModule_2 = null;
    private Module mModule_3 = null;
    private Module mModule_4 = null;
    private Module mModule_5 = null;
    private Module mModule_6 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        askPermissions();

        for (int grad_index = 0; grad_index < mImage_h_overlap; grad_index ++){
            mLinearGrad_h[grad_index] = 1.0f / (mImage_h_overlap - 1) * grad_index;
        }
        for (int grad_index = 0; grad_index < mImage_w_overlap; grad_index ++){
            mLinearGrad_w[grad_index] = 1.0f / (mImage_w_overlap - 1) * grad_index;
        }
        for (int grad_index = 0; grad_index < mImage_h_patchnum * mImage_w_patchnum; grad_index ++){
            mImageLevel[grad_index] = 0;
        }


        try {
            mBitmap = BitmapFactory.decodeStream(getAssets().open(mCurrentPhotoPath));
            float scale_w = ((float) mImageView_w) / mBitmap.getWidth();
            float scale_h = ((float) mImageView_h) / mBitmap.getHeight();
            Matrix matrix = new Matrix();
            matrix.postScale(scale_w, scale_h);
            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);

            mDeblurBitmap = BitmapFactory.decodeStream(getAssets().open(mDeblurPhotoPath));
            scale_w = ((float) mImageView_w) / mDeblurBitmap.getWidth();
            scale_h = ((float) mImageView_h) / mDeblurBitmap.getHeight();
            matrix.postScale(scale_w, scale_h);
            mDeblurBitmap = Bitmap.createBitmap(mDeblurBitmap, 0, 0, mDeblurBitmap.getWidth(), mDeblurBitmap.getHeight(), matrix, true);

        } catch (IOException e) {
            e.printStackTrace();
        }


        mImageView = findViewById(R.id.mCanvas);

        // mImageView.setImageBitmap(mBitmap);
        mProgressbar = findViewById(R.id.mProgress);
        mProcessViewer = findViewById(R.id.mProcessView);
        mResultViewer = findViewById(R.id.mResultView);

        mProcessViewer.setGrids(
                mImage_h_patchnum, mImage_w_patchnum, mImageView_h, mImageView_w,
                mImage_h_overlap, mImage_w_overlap, mImagePatchSize
        );
        mProcessViewer.setVisibility(View.INVISIBLE);
        mResultViewer.setGrids(
                mImage_h_patchnum, mImage_w_patchnum, mImageView_h, mImageView_w,
                mImage_h_overlap, mImage_w_overlap, mImagePatchSize
        );
        mResultViewer.setVisibility(View.INVISIBLE);

        mGallery = findViewById(R.id.mGallery);
        mGallery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, 1);
            }
        });

        mMock = findViewById(R.id.mMock);
        mMock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                mMockedLevel += 1;
                if (mMockedLevel > 2){
                    mMockedLevel = 0;
                }
                mMock.setText(String.valueOf(mMockedLevel));
            }
        });

        mGrid = findViewById(R.id.mGrid);
        mGrid.setOnTouchListener(new View.OnTouchListener() {
             public boolean onTouch(View view, MotionEvent event) {
                 switch (event.getAction()) {
                     case MotionEvent.ACTION_DOWN:
                         try {
                             mResultViewer.setResults(mImageLevel);
                             mResultViewer.invalidate();
                             mResultViewer.setVisibility(View.VISIBLE);
                         } catch (Exception e) {
                             e.printStackTrace();
                         }
                         break;
                     case MotionEvent.ACTION_MOVE:
                         break;
                     case MotionEvent.ACTION_UP:
                         try {
                             mResultViewer.setVisibility(View.INVISIBLE);
                         } catch (Exception e) {
                             e.printStackTrace();
                         }
                         break;
                 }
                 return true;
             }
        });

        mDeblur = findViewById(R.id.mDeblur);
        mDeblur.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        try {
                            // mBitmap = BitmapFactory.decodeStream(getAssets().open(mDeblurPhotoPath));
                            // Matrix matrix = new Matrix();
                            // mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                            mImageView.setImageBitmap(mDeblurBitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        try {
                            mImageView.setImageBitmap(mBitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                }
                return true;
            }
        });


        mCamera = findViewById(R.id.mCamera);
        mCamera.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent takePhoto = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePhoto.resolveActivity(getPackageManager()) != null){

                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String imageFileName = "JPEG_" + timeStamp + "_";
                    File storageDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES);
                    File image = null;
                    try {
                        image = File.createTempFile(
                                imageFileName,  // prefix
                                ".jpg",         // suffix
                                storageDir      // directory
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Save a file: path for use with ACTION_VIEW intents
                    mCurrentPhotoPath = "file:" + image.getAbsolutePath();
                    if (image !=null) {
                        takePhoto.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                        startActivityForResult(takePhoto, 0);
                    }

                }

            }
        });

        try {
            // mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "unet.ptl"));
            mBlurJudger = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mJudgerNetPath));
            mModule_1 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep1Path));
            mModule_2 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep2Path));
            mModule_3 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep3Path));
            mModule_4 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep4Path));
            mModule_5 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep5Path));
            mModule_6 = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), mNetdep6Path));

        } catch (IOException e) {
            Log.e("Deblur Demo", "Error reading assets", e);
            finish();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK) {
                        try {
                            mBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(mCurrentPhotoPath));
                            float scale_w = ((float) mImageView_w) / mBitmap.getWidth();
                            float scale_h = ((float) mImageView_h) / mBitmap.getHeight();
                            Matrix matrix = new Matrix();
                            matrix.postScale(scale_w, scale_h);
                            //matrix.postRotate(90.0f);
                            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                            mImageView.setImageBitmap(mBitmap);

                            Thread thread = new Thread(MainActivity.this);
                            thread.start();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mCurrentPhotoPath = picturePath;
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                float scale_w = ((float) mImageView_w) / mBitmap.getWidth();
                                float scale_h = ((float) mImageView_h) / mBitmap.getHeight();
                                Matrix matrix = new Matrix();
                                matrix.postScale(scale_w, scale_h);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();

                                Thread thread = new Thread(MainActivity.this);
                                thread.start();

                            }
                        }
                    }
                    break;
            }
        }
    }

    protected void askPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    public void run(){

        runOnUiThread(() ->{
            mGallery.setEnabled(false);
            mCamera.setEnabled(false);
            mDeblur.setEnabled(false);
            mProgressbar.setVisibility(ProgressBar.VISIBLE);
        });

        for(int buffer_index = 0; buffer_index < 3 * mImageView_w * mImageView_h; buffer_index ++){
            mImageBuffer[buffer_index] = 0.0f;
        }
        // img split
        boolean l_grad = false;
        boolean r_grad = false;
        boolean t_grad = false;
        boolean b_grad = false;
        int image_count = 0;
        for (int w_index = 0; w_index < mImage_w_patchnum; w_index ++){
            for (int h_index = 0; h_index < mImage_h_patchnum; h_index ++){

                mProcessViewer.setResult(h_index, w_index, -1);
                runOnUiThread(() -> {
                    mProcessViewer.invalidate();
                    mProcessViewer.setVisibility(View.VISIBLE);
                });
                if(w_index == 0){ l_grad = false;r_grad = true; }
                else if (w_index == mImage_w_patchnum - 1){ l_grad = true;r_grad = false; }
                else{ l_grad = true;r_grad = true; }

                if(h_index == 0){t_grad = false;b_grad = true; }
                else if (h_index == mImage_h_patchnum - 1){ t_grad = true;b_grad = false; }
                else{t_grad = true;b_grad = true; }

                int start_h = h_index * (mImagePatchSize - mImage_h_overlap);
                int start_w = w_index * (mImagePatchSize - mImage_w_overlap);

                final IValue inputPatch = IValue.from(
                        TensorImageUtils.bitmapToFloat32Tensor(mBitmap, start_w, start_h, mImagePatchSize, mImagePatchSize, new float[] {0.0f, 0.0f, 0.0f}, new float[] {1.0f, 1.0f, 1.0f})
                );
                int maxScoreIdx = mMockedLevel;

                // level network forward
                final Tensor judgeTensor = mBlurJudger.forward(inputPatch).toTensor();
                final float[] scores = judgeTensor.getDataAsFloatArray();

                // searching for the index with maximum score
                float maxScore = -Float.MAX_VALUE;

                for (int i = 0; i < scores.length; i++) {
                    if (scores[i] > maxScore) {
                        maxScore = scores[i];
                        maxScoreIdx = i;
                    }
                }

                // maxScoreIdx = mMockedLevel; // mock one

                mImageLevel[image_count] = maxScoreIdx;
                // ----------------------

                // common encoder forward
                IValue[] outputTuple = mModule_1.forward(inputPatch).toTuple();
                final IValue x1Tensor = outputTuple[0];
                final IValue x2Tensor = outputTuple[1];
                final IValue x3Tensor = outputTuple[2];
                IValue x4Tensor = null;
                IValue x5Tensor = null;

                IValue output = null;
                // ----------------------

                switch (maxScoreIdx) {
                    case 2:
                        output = mModule_4.forward(x3Tensor, x2Tensor, x1Tensor, inputPatch);
                        break;
                    case 1:
                        x4Tensor = mModule_2.forward(x3Tensor);
                        output = mModule_5.forward(x4Tensor, x3Tensor, x2Tensor, x1Tensor, inputPatch);
                        break;
                    case 0:
                        x4Tensor = mModule_2.forward(x3Tensor);
                        x5Tensor = mModule_3.forward(x4Tensor);
                        output = mModule_6.forward(x5Tensor, x4Tensor, x3Tensor, x2Tensor, x1Tensor, inputPatch);
                        break;
                }

                // IValue output = mModule.forward(inputPatch);

                final float[] outputs = output.toTensor().getDataAsFloatArray();

                // final float[] outputs = inputPatch.toTensor().getDataAsFloatArray();
                int patch_count = 0;
                for (int i_h_index = start_h; i_h_index < start_h + mImagePatchSize; i_h_index ++) {
                    for (int i_w_index = start_w; i_w_index < start_w + mImagePatchSize; i_w_index++) {
                        int image_index = i_h_index * mImageView_w + i_w_index;

                        int p_w_index = patch_count % mImagePatchSize;
                        int p_h_index = patch_count / mImagePatchSize;

                        float r = outputs[patch_count];
                        float g = outputs[patch_count + mImagePatchSize*mImagePatchSize];
                        float b = outputs[patch_count + 2 * mImagePatchSize*mImagePatchSize];


                        if (p_w_index < mImage_w_overlap && l_grad) {
                            r = r * mLinearGrad_w[p_w_index];
                            g = g * mLinearGrad_w[p_w_index];
                            b = b * mLinearGrad_w[p_w_index];
                        }
                        if (p_w_index >= mImagePatchSize - mImage_w_overlap && r_grad) {
                            r = r * (mLinearGrad_w[mImagePatchSize - p_w_index - 1]);
                            g = g * (mLinearGrad_w[mImagePatchSize - p_w_index - 1]);
                            b = b * (mLinearGrad_w[mImagePatchSize - p_w_index - 1]);
                        }
                        if (p_h_index < mImage_h_overlap && t_grad) {
                            r = r * mLinearGrad_h[p_h_index];
                            g = g * mLinearGrad_h[p_h_index];
                            b = b * mLinearGrad_h[p_h_index];
                        }
                        if (p_h_index >= mImagePatchSize - mImage_h_overlap && b_grad) {
                            r = r * mLinearGrad_h[mImagePatchSize - p_h_index - 1];
                            g = g * mLinearGrad_h[mImagePatchSize - p_h_index - 1];
                            b = b * mLinearGrad_h[mImagePatchSize - p_h_index - 1];
                        }

                        mImageBuffer[image_index] += r;
                        mImageBuffer[image_index + mImageView_h * mImageView_w] += g;
                        mImageBuffer[image_index + 2 * mImageView_h * mImageView_w] += b;

                        patch_count ++;
                    }
                }
                image_count ++;
            }
        }
        mDeblurBitmap = floatArrayToGrayScaleBitmap(mImageBuffer);
        /*
        // run model
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(mBitmap, new float[] {0.0f, 0.0f, 0.0f}, new float[] {1.0f, 1.0f, 1.0f});
        final Tensor outTensor = mModule.forward(IValue.from(inputTensor)).toTensor();
        final float[] outputs = outTensor.getDataAsFloatArray();
        mDeblurBitmap = floatArrayToGrayScaleBitmap(outputs);
        //
        */

        runOnUiThread(() ->{
            mGallery.setEnabled(true);
            mCamera.setEnabled(true);
            mDeblur.setEnabled(true);
            mProgressbar.setVisibility(ProgressBar.INVISIBLE);
            mProcessViewer.setVisibility(ProgressBar.INVISIBLE);
        });
    }

    protected Bitmap floatArrayToGrayScaleBitmap(float[] floatArray) {
        Bitmap bmp = Bitmap.createBitmap(mImageView_w, mImageView_h, Bitmap.Config.ARGB_8888);
        ByteBuffer byteBuffer = ByteBuffer.allocate(mImageView_w * mImageView_h * 4);

        for (int i = 0; i < mImageView_w * mImageView_h; i++){

            double r = (floatArray[i] < 0.0 ? 0.0 : floatArray[i]);
            byte br = (byte)(int)((Math.min(r, 1.0)) * 255);

            double g = (floatArray[i+mImageView_w*mImageView_h] < 0.0 ? 0.0 : floatArray[i+mImageView_w*mImageView_h]);
            byte bg = (byte)(int)((Math.min(g, 1.0)) * 255);

            double b = (floatArray[i+2*mImageView_w*mImageView_h] < 0.0 ? 0.0 : floatArray[i+2*mImageView_w*mImageView_h]);
            byte bb = (byte)(int)((Math.min(b, 1.0)) * 255);


            byteBuffer.put(i * 4 + 0, br);//(int)();
            byteBuffer.put(i * 4 + 1, bg);//(int)(floatArray[i+mImageView_w*mImageView_h] * 255);
            byteBuffer.put(i * 4 + 2, bb); //(int)(floatArray[i+2*mImageView_w*mImageView_h] * 255);
            byteBuffer.put(i * 4 + 3, (byte)(int)(-1));
        }
        bmp.copyPixelsFromBuffer(byteBuffer);

        return bmp;
    }

}