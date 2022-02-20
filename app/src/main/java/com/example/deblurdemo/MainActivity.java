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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Runnable{

    private ImageView mImageView;
    private int mImageView_h = 1280;
    private int mImageView_w = 720;
    private ProgressBar mProgressbar;
    private Button mGallery;
    private Button mCamera;
    private Button mDeblur;
    private Bitmap mBitmap = null;
    private Bitmap mDeblurBitmap = null;
    private String mCurrentPhotoPath = "blur.png";
    private String mDeblurPhotoPath = "sharp.png";

    private Module mModule = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        askPermissions();

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

        mGallery = findViewById(R.id.mGallery);
        mGallery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, 1);
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
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "denoise.ptl"));
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

        // run model
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(mBitmap, new float[] {0.0f, 0.0f, 0.0f}, new float[] {1.0f, 1.0f, 1.0f});
        final Tensor outTensor = mModule.forward(IValue.from(inputTensor)).toTensor();
        final float[] outputs = outTensor.getDataAsFloatArray();
        mDeblurBitmap = floatArrayToGrayScaleBitmap(outputs);
        //

        runOnUiThread(() ->{
            mGallery.setEnabled(true);
            mCamera.setEnabled(true);
            mDeblur.setEnabled(true);
            mProgressbar.setVisibility(ProgressBar.INVISIBLE);
        });
    }

    protected Bitmap floatArrayToGrayScaleBitmap(float[] floatArray) {
        Bitmap bmp = Bitmap.createBitmap(mImageView_w, mImageView_h, Bitmap.Config.ARGB_8888);
        ByteBuffer byteBuffer = ByteBuffer.allocate(mImageView_w * mImageView_h * 4);

        for (int i = 0; i < mImageView_w * mImageView_h; i++){
            byteBuffer.put(i * 4 + 0, (byte)(int)(
                    (floatArray[i] > 1.0 ? 1.0 : floatArray[i]) * 255)
            );//(int)();
            byteBuffer.put(i * 4 + 1, (byte)(int)(
                    (floatArray[i+mImageView_w*mImageView_h] > 1.0 ? 1.0 : floatArray[i+mImageView_w*mImageView_h]) * 255)
            );//(int)(floatArray[i+mImageView_w*mImageView_h] * 255);
            byteBuffer.put(i * 4 + 2, (byte)(int)(
                    (floatArray[i+2*mImageView_w*mImageView_h] > 1.0 ? 1.0 : floatArray[i+2*mImageView_w*mImageView_h]) * 255)
            ); //(int)(floatArray[i+2*mImageView_w*mImageView_h] * 255);
            byteBuffer.put(i * 4 + 3, (byte)(int)(-1));
        }
        bmp.copyPixelsFromBuffer(byteBuffer);

        return bmp;
    }

}