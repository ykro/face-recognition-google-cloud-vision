package com.bitandik.labs.facedetectiongcv;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bitandik.labs.facedetectiongcv.utils.CloudVisionUtils;
import com.bitandik.labs.facedetectiongcv.utils.PermissionUtils;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.bitandik.labs.facedetectiongcv.utils.PermissionUtils.CAMERA_PERMISSIONS;
import static com.bitandik.labs.facedetectiongcv.utils.PermissionUtils.GALLERY_PERMISSIONS;

public class MainActivity extends AppCompatActivity {
  public final static int CAMERA_REQUEST = 0;
  public final static int GALLERY_REQUEST = 1;
  private final static int BITMAP_MAX_DIMENSION = 600;

  @BindView(R.id.imgMain)
  ImageView imgMain;
  @BindView(R.id.txtMainLabels)
  TextView txtMainLabels;
  @BindView(R.id.progressBar)
  ProgressBar progressBar;
  @BindString(R.string.CLOUD_VISION_API_KEY) String APIKey;

  private String currentPhotoPath;
  private CloudVisionUtils cloudVisionUtils;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    cloudVisionUtils = CloudVisionUtils.init(APIKey);
    txtMainLabels.setMovementMethod(new ScrollingMovementMethod());
  }

  @OnClick(R.id.fab)
  public void fabClickHandle(){
    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
    builder
      .setMessage(R.string.dialog_select_prompt)
      .setPositiveButton(R.string.dialog_select_gallery, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          startGalleryChooser();
        }
      })
      .setNegativeButton(R.string.dialog_select_camera, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          startCamera();
        }
      })
    .show();
  }

  public void startGalleryChooser() {
    if (PermissionUtils.requestPermission(
        this,
        GALLERY_PERMISSIONS,
        Manifest.permission.READ_EXTERNAL_STORAGE)) {
      Intent intent = new Intent();
      intent.setType("image/*");
      intent.setAction(Intent.ACTION_GET_CONTENT);
      startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                             GALLERY_REQUEST);
    }
  }

  public void startCamera() {
    if (PermissionUtils.requestPermission(
        this,
        CAMERA_PERMISSIONS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA)) {

          Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
          Uri photoUri = FileProvider.getUriForFile(this,
                                                    getApplicationContext().getPackageName() + ".provider",
                                                    getCameraFile());
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, CAMERA_REQUEST);
    }
  }

  public File getCameraFile() {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = "IMG_" + timeStamp + ".jpg";
    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    File photoFile = new File(storageDir, imageFileName);
    currentPhotoPath = photoFile.getAbsolutePath();
    return photoFile;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == GALLERY_REQUEST && resultCode == RESULT_OK && data != null) {
      uploadImage(data.getData());
    } else if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
      File f = new File(currentPhotoPath);
      uploadImage(Uri.fromFile(f));
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case CAMERA_PERMISSIONS:
        if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS, grantResults)) {
          startCamera();
        }
        break;
      case GALLERY_PERMISSIONS:
        if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS, grantResults)) {
          startGalleryChooser();
        }
        break;
    }
  }

  public void uploadImage(Uri uri) {
    if (uri != null) {
      try {
        Bitmap bitmap =
            scaleBitmapDown(
                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                BITMAP_MAX_DIMENSION);

        analyzeImage(bitmap);
        imgMain.setImageBitmap(bitmap);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void analyzeImage(final Bitmap bitmap) throws IOException {
    new AsyncTask<Void, Void, BatchAnnotateImagesResponse>() {
      protected void onPreExecute() {
        progressBar.setVisibility(View.VISIBLE);
        imgMain.setVisibility(View.GONE);
        txtMainLabels.setVisibility(View.GONE);
      }

      @Override
      protected BatchAnnotateImagesResponse doInBackground(Void... params) {
        BatchAnnotateImagesResponse response = null;
        try {
          response = cloudVisionUtils.getAnnotations(bitmap);
        } catch (IOException e) {
          e.printStackTrace();
        }

        return response;
      }

      protected void onPostExecute(BatchAnnotateImagesResponse result) {
        drawRectanglesAroundFaces(result, bitmap);
        String txtResponse = cloudVisionUtils.convertResponseToString(result);
        txtMainLabels.setText(txtResponse);

        progressBar.setVisibility(View.GONE);
        imgMain.setVisibility(View.VISIBLE);
        txtMainLabels.setVisibility(View.VISIBLE);
      }
    }.execute();
  }

  public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

    int originalWidth = bitmap.getWidth();
    int originalHeight = bitmap.getHeight();
    int resizedWidth = maxDimension;
    int resizedHeight = maxDimension;

    if (originalHeight > originalWidth) {
      resizedHeight = maxDimension;
      resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
    } else if (originalWidth > originalHeight) {
      resizedWidth = maxDimension;
      resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
    } else if (originalHeight == originalWidth) {
      resizedHeight = maxDimension;
      resizedWidth = maxDimension;
    }
    return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
  }

  public void drawRectanglesAroundFaces(BatchAnnotateImagesResponse response, Bitmap bitmap) {
    ArrayList<RectF> rectangles = cloudVisionUtils.getFacesRectangles(response);
    Paint redPaint = new Paint();
    redPaint.setStrokeWidth(5);
    redPaint.setColor(Color.RED);
    redPaint.setStyle(Paint.Style.STROKE);

    Bitmap imageAndRectangles = Bitmap.createBitmap(bitmap.getWidth(),
                                               bitmap.getHeight(),
                                               Bitmap.Config.RGB_565);

    Canvas canvas = new Canvas(imageAndRectangles);
    canvas.drawBitmap(bitmap, 0, 0, null);
    for (RectF current : rectangles) {
      canvas.drawRoundRect(current, 2, 2, redPaint);
    }
    imgMain.setImageDrawable(new BitmapDrawable(getResources(), imageAndRectangles));
  }
}
