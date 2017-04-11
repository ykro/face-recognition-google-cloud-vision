package com.bitandik.labs.facedetectiongcv.utils;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.Landmark;
import com.google.api.services.vision.v1.model.Position;
import com.google.api.services.vision.v1.model.Vertex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by ykro.
 */

public class CloudVisionUtils {
  private String APIKey;
  private final static int MAX_FACES = 5;
  private final static int MAX_LABELS = 20;
  private final static String LANDMARK_TYPE = "type";
  private enum Likelihood {
    LIKELY,  VERY_LIKELY
  }
  private enum LandmarkType {
    MOUTH_LEFT, MOUTH_RIGHT, UPPER_LIP, LOWER_LIP
  }
  private enum Detection {
    FACE_DETECTION, LABEL_DETECTION
  }
  private static CloudVisionUtils utils;

  public CloudVisionUtils() {
  }

  public void setAPIKey(String APIKey) {
    this.APIKey = APIKey;
  }

  public static CloudVisionUtils init(String APIKey) {
    if (utils == null) {
      utils = new CloudVisionUtils();
      utils.setAPIKey(APIKey);
    }
    return utils;
  }

  public BatchAnnotateImagesResponse getAnnotations(final Bitmap bitmap)
                                                      throws IOException {
    BatchAnnotateImagesResponse response = null;
    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
    builder.setVisionRequestInitializer(
        new VisionRequestInitializer(APIKey));

    Vision vision = builder.build();
    BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
      AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

      Image base64EncodedImage = new Image();
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
      byte[] imageBytes = byteArrayOutputStream.toByteArray();

      base64EncodedImage.encodeContent(imageBytes);
      annotateImageRequest.setImage(base64EncodedImage);

      annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
          Feature labelDetection = new Feature();
          labelDetection.setType(String.valueOf(Detection.LABEL_DETECTION));
          labelDetection.setMaxResults(MAX_LABELS);
          add(labelDetection);

          Feature faceDetection = new Feature();
          faceDetection.setType(String.valueOf(Detection.FACE_DETECTION));
          faceDetection.setMaxResults(MAX_FACES);
          add(faceDetection);
      }});

      add(annotateImageRequest);
    }});

    Vision.Images.Annotate annotateRequest =
        vision.images().annotate(batchAnnotateImagesRequest);

    // Due to a bug: requests to Vision API containing large images fail when GZipped.
    annotateRequest.setDisableGZipContent(true);
    response = annotateRequest.execute();

    return response;
  }

  public String convertResponseToString(BatchAnnotateImagesResponse response) {
    String message = "I found these things:\n\n";

    if (response != null &&
        response.getResponses() != null &&
        response.getResponses().size() > 0) {
      List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
      if (labels != null) {
        for (EntityAnnotation label : labels) {
          message += String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription());
          message += "\n";
        }
      } else {
        message += "nothing";
      }
    }
    return message;
  }

  public ArrayList<RectF> getFacesRectangles(BatchAnnotateImagesResponse response) {
    ArrayList<RectF> facesRectangles = new ArrayList<RectF>();
    if (response != null &&
        response.getResponses() != null &&
        response.getResponses().size() > 0) {
      List<FaceAnnotation> faces = response.getResponses().get(0).getFaceAnnotations();

      if (faces != null) {
        for (FaceAnnotation currentFace : faces) {
          List<Vertex> vertexBounds = currentFace.getFdBoundingPoly().getVertices();
          RectF faceRectangle = new RectF(vertexBounds.get(0).getX(),
                                             vertexBounds.get(0).getY(),
                                             vertexBounds.get(2).getX(),
                                             vertexBounds.get(2).getY());
          facesRectangles.add(faceRectangle);

          String joyLikelihood = currentFace.getJoyLikelihood();
          if (joyLikelihood.equals(String.valueOf(Likelihood.LIKELY)) ||
              joyLikelihood.equals(String.valueOf(Likelihood.VERY_LIKELY))) {
              Position mouthLeft = null, mouthRight = null, upperLip = null, lowerLip = null;
              for (Landmark currentLandmark : currentFace.getLandmarks()) {
                if (currentLandmark.get(LANDMARK_TYPE).equals(String.valueOf(LandmarkType.MOUTH_LEFT))) {
                  mouthLeft = currentLandmark.getPosition();
                } else if (currentLandmark.get(LANDMARK_TYPE).equals(String.valueOf(LandmarkType.MOUTH_RIGHT))) {
                  mouthRight = currentLandmark.getPosition();
                } else if (currentLandmark.get(LANDMARK_TYPE).equals(String.valueOf(LandmarkType.UPPER_LIP))) {
                  upperLip = currentLandmark.getPosition();
                } else if (currentLandmark.get(LANDMARK_TYPE).equals(String.valueOf(LandmarkType.LOWER_LIP))) {
                  lowerLip = currentLandmark.getPosition();
                }
              }
              if (mouthLeft != null && mouthRight != null &&
                   lowerLip != null && upperLip != null) {
                RectF mouthRectangle = new RectF(mouthLeft.getX(),
                                                upperLip.getY(),
                                                mouthRight.getX(),
                                                lowerLip.getY());
                facesRectangles.add(mouthRectangle);
              }
          }


        }
      }
    }
    return facesRectangles;
  }
}
