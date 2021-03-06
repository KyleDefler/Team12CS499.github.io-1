/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.augmentedimage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.augmentedimage.rendering.AugmentedImageRenderer;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import handDetermination.Card;
import handDetermination.Hand;

import static java.lang.Integer.parseInt;

/** This app extends the HelloAR Java app to include image tracking functionality. */
//It also adds the ability to take images of the AR environment and store them in
public class AugmentedImageActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = AugmentedImageActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;
  private ImageView fitToScanView;
  private RequestManager glideRequestManager;

  private int mWidth;
  private int mHeight;
  private boolean capturePicture;
  private int capturePictureCount = 0;

  private handDetermination.Hand pokerHand;
  private ArrayList<handDetermination.Card> pokerCards;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final AugmentedImageRenderer augmentedImageRenderer = new AugmentedImageRenderer();

  private boolean shouldConfigureSession = false;

  // Augmented image configuration and rendering.
  // Load a single image (true) or a pre-generated image database (false).
  private final boolean useSingleImage = false;
  // Augmented image and its associated center pose anchor, keyed by index of the augmented image in
  // the
  // database.
  private final Map<Integer, Pair<AugmentedImage, Anchor>> augmentedImageMap = new HashMap<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    fitToScanView = findViewById(R.id.image_view_fit_to_scan);
    glideRequestManager = Glide.with(this);
    glideRequestManager
        .load(Uri.parse("file:///android_asset/fit_to_scan.png"))
        .into(fitToScanView);

    installRequested = false;
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        session = new Session(/* context = */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (Exception e) {
        message = "This device does not support AR";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      shouldConfigureSession = true;
    }

    if (shouldConfigureSession) {
      configureSession();
      shouldConfigureSession = false;
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
      session = null;
      return;
    }
    surfaceView.onResume();
    displayRotationHelper.onResume();

    fitToScanView.setVisibility(View.VISIBLE);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(
              this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(/*context=*/ this);
      augmentedImageRenderer.createOnGlThread(/*context=*/ this);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    mWidth = width;
    mHeight = height;
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      // Compute lighting from average intensity of the image.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // Visualize augmented images.
      drawAugmentedImages(frame, projmtx, viewmtx, colorCorrectionRgba);
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
    if (capturePictureCount > 100) { //This code checks for every 100th scene update
      try {                             //In order to take a picture of the AR Environment.
          capturePictureCount = 0;
          SavePicture();
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    else {
      capturePictureCount++;
    }
  }

  private void configureSession() {
    Config config = new Config(session);
    config.setFocusMode(Config.FocusMode.AUTO);
    if (!setupAugmentedImageDatabase(config)) {
      messageSnackbarHelper.showError(this, "Could not setup augmented image database");
    }
    session.configure(config);
  }

  private void drawAugmentedImages(
      Frame frame, float[] projmtx, float[] viewmtx, float[] colorCorrectionRgba) {
    Collection<AugmentedImage> updatedAugmentedImages =
        frame.getUpdatedTrackables(AugmentedImage.class);

    // Iterate to update augmentedImageMap, remove elements we cannot draw.
    for (AugmentedImage augmentedImage : updatedAugmentedImages) {
      switch (augmentedImage.getTrackingState()) {
        case PAUSED:
          // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
          // but not yet tracked.
          String text = String.format("Detected Image %d", augmentedImage.getIndex());
          messageSnackbarHelper.showMessage(this, text);
          break;

        case TRACKING:
          // Have to switch to UI Thread to update View.
          this.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  fitToScanView.setVisibility(View.GONE);
                }
              });

          // Create a new anchor for newly found images.
          if (!augmentedImageMap.containsKey(augmentedImage.getIndex())) {
            Anchor centerPoseAnchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
            augmentedImageMap.put(
                augmentedImage.getIndex(), Pair.create(augmentedImage, centerPoseAnchor));
          }
          break;

        case STOPPED:
          augmentedImageMap.remove(augmentedImage.getIndex());
          break;

        default:
          break;
      }
    }

    // Draw all images in augmentedImageMap
    for (Pair<AugmentedImage, Anchor> pair : augmentedImageMap.values()) {
      AugmentedImage augmentedImage = pair.first;
      Anchor centerAnchor = augmentedImageMap.get(augmentedImage.getIndex()).second;
      switch (augmentedImage.getTrackingState()) {
        case TRACKING:
            int cardType = 0;
            int tempIndex = augmentedImage.getIndex(); //This code detectes which Images are being augmented
            if (tempIndex >= 0 && tempIndex <= 8) {     //And when 5 cards have been detected and augmented
                cardType = 3;                           //It calls the hand recognition
                handDetermination.Card newCard = parseCard(augmentedImage); //method to determine what hand it is
                pokerCards.add(newCard);                                    //Once the hand has been determined
            }                                                               //The app will display a SnackBar message
            else if (tempIndex <= 14) {                                     //Displaying the type of hand!
                cardType = 2;
                handDetermination.Card newCard = parseCard(augmentedImage);
                pokerCards.add(newCard);
            }
            else if (tempIndex <= 17) {
                cardType = 1;
                handDetermination.Card newCard = parseCard(augmentedImage);
                pokerCards.add(newCard);
            }
            else {
                cardType = 4;
                handDetermination.Card newCard = parseCard(augmentedImage);
                pokerCards.add(newCard);
            }
          augmentedImageRenderer.draw(viewmtx, projmtx, augmentedImage, centerAnchor, colorCorrectionRgba, cardType);
            if (pokerCards.size() == 5) {
                Card[] temp = arrListToArr(pokerCards);
                pokerHand = new Hand(temp);
                messageSnackbarHelper.showMessage(this, pokerHand.rankToString());
            }
          break;
        default:
          break;
      }
    }


  }

  private boolean setupAugmentedImageDatabase(Config config) {
    AugmentedImageDatabase augmentedImageDatabase;

    // There are two ways to configure an AugmentedImageDatabase:
    // 1. Add Bitmap to DB directly
    // 2. Load a pre-built AugmentedImageDatabase
    // Option 2) has
    // * shorter setup time
    // * doesn't require images to be packaged in apk.
    if (useSingleImage) {
      Bitmap augmentedImageBitmap = loadAugmentedImageBitmap();
      if (augmentedImageBitmap == null) {
        return false;
      }

      augmentedImageDatabase = new AugmentedImageDatabase(session);
      augmentedImageDatabase.addImage("image_name", augmentedImageBitmap);
      // If the physical size of the image is known, you can instead use:
      //     augmentedImageDatabase.addImage("image_name", augmentedImageBitmap, widthInMeters);
      // This will improve the initial detection speed. ARCore will still actively estimate the
      // physical size of the image as it is viewed from multiple viewpoints.
    } else {
      // This is an alternative way to initialize an AugmentedImageDatabase instance,
      // load a pre-existing augmented image database.
      try (InputStream is = getAssets().open("Card_Pictures_Database.imgdb")) {
        augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, is);
      } catch (IOException e) {
        Log.e(TAG, "IO exception loading augmented image database.", e);
        return false;
      }
    }

    config.setAugmentedImageDatabase(augmentedImageDatabase);
    return true;
  }

  private Bitmap loadAugmentedImageBitmap() {
    try (InputStream is = getAssets().open("default.jpg")) {
      return BitmapFactory.decodeStream(is);
    } catch (IOException e) {
      Log.e(TAG, "IO exception loading augmented image bitmap.", e);
    }
    return null;
  }

  public void onSavePicture(View view) {
    capturePicture = true;
  }

  public void SavePicture() throws IOException {
      //Initialize the array for the image pixelData
    int pixelData[] = new int[mWidth * mHeight];

    // Read the pixels from the current GL frame.
    IntBuffer buf = IntBuffer.wrap(pixelData);
    buf.position(0);
    GLES20.glReadPixels(0, 0, mWidth, mHeight,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);

    // Create a file in the Pictures/PokerHands album.
    final File out = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES) + "/PokerHands", "Img" +
            Long.toHexString(System.currentTimeMillis()) + ".png");

    // Make sure the file exists, if not create it!
    if (!out.getParentFile().exists()) {
      out.getParentFile().mkdirs();
    }

    // Convert the pixel data from RGBA to ARGB. (This is for Android compatibility)
    int bitmapData[] = new int[pixelData.length];
    for (int i = 0; i < mHeight; i++) {
      for (int j = 0; j < mWidth; j++) {
        int p = pixelData[i * mWidth + j];
        int b = (p & 0x00ff0000) >> 16;
        int r = (p & 0x000000ff) << 16;
        int ga = p & 0xff00ff00;
        bitmapData[(mHeight - i - 1) * mWidth + j] = ga | r | b;
      }
    }

    // Create a bitmap to hold the pixel data
    Bitmap bmp = Bitmap.createBitmap(bitmapData,
            mWidth, mHeight, Bitmap.Config.ARGB_8888);

    // Write the bitmap to the PokerHands file within Pictures
    FileOutputStream fos = new FileOutputStream(out);
    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
    fos.flush();
    fos.close();

    //Display message saying the file has been written
    String text = "Wrote file: " + out.getName();
    messageSnackbarHelper.showMessage(this, text);

    //Loop through the Pictures/PokerHands file and delete anything older than the 3 most recent pictures.
    long oldestTimeStamp = out.listFiles()[0].lastModified();
    int oldestFileIndex = 0;
    if (out.listFiles().length > 3) {
      for (int i = 1; i < out.listFiles().length; i++) {
        File temp = out.listFiles()[i];
        if (temp.lastModified() > oldestTimeStamp) {
          oldestTimeStamp = temp.lastModified();
          oldestFileIndex = i;
        }
      }
      String temp = out.listFiles()[oldestFileIndex].getAbsolutePath();
      File oldestFile = new File(temp);
      oldestFile.delete();
    }

    //refresh the Gallery so the images are no longer displayed when the folder is inspected.
    MediaScannerConnection.scanFile(this, new String[] { out.getPath() }, null,
            new MediaScannerConnection.OnScanCompletedListener() {
                public void onScanCompleted(String path, Uri uri) {
                    Log.i("ExternalStorage", "Scanned " + path + ":");
                    Log.i("ExternalStorage", "-> uri=" + uri);
                }
            });

  }

  public handDetermination.Card parseCard(AugmentedImage card) {
      //Determine from the passed Augmented Image what playing card is being detected
      String imageName = card.getName();
      String[] splitString = imageName.split("[_.]+");
      byte[] values = new byte[2];
      boolean faceCard = false;
      //check if the Augmented Image is a King
      if (splitString[1].equalsIgnoreCase("KING")) {
          values[1] = 13;
          faceCard = true;
      }
      //check if the Augmented Image is a Queen
      else if (splitString[1].equalsIgnoreCase("QUEEN")) {
          values[1] = 12;
          faceCard = true;
      }
      //check if the Augmented Image is a Jack
      else if (splitString[1].equalsIgnoreCase("JACK")) {
          values[1] = 11;
          faceCard = true;
      }
      //check if the Augmented Image is an Ace
      else if (splitString[1].equalsIgnoreCase("ACE")) {
          values[1] = 14;
          faceCard = true;
      }
      //check if the Augmented Image is a Heart
      if (splitString[0].equalsIgnoreCase("HEART")) {
          values[0] = 0;
          int intType = Integer.parseInt(splitString[1]);
          if (!faceCard) {
              values[1] = (byte) intType;
          }
      }
      //check if the Augmented Image is a Diamond
      else if (splitString[0].equalsIgnoreCase("DIAMOND")) {
          values[0] = 1;
          int intType = Integer.parseInt(splitString[1]);
          if (!faceCard) {
              values[1] = (byte) intType;
          }
      }
      //check if the Augmented Image is a Club
      else if (splitString[0].equalsIgnoreCase("CLUB")) {
          values[0] = 2;
          int intType = Integer.parseInt(splitString[1]);
          if (!faceCard) {
              values[1] = (byte) intType;
          }
      }
      //check if the Augmented Image is a Spade
      else if (splitString[0].equalsIgnoreCase("SPADE")) {
          values[0] = 3;
          int intType = Integer.parseInt(splitString[1]);
          if (!faceCard) {
              values[1] = (byte) intType;
          }
      }
      //Return a Card object with the value and suit parsed from the Augmented Image filename
      handDetermination.Card result = new handDetermination.Card(values[0], values[1]);
      return result;
  }

  public handDetermination.Card[] arrListToArr(ArrayList<handDetermination.Card> cards) {
      //convert an ArrayList to an array
      //This was to solve dynamic sizing issues
      handDetermination.Card[] cardArr = new Card[cards.size()];
      int index = 0;
      for (Card c : cards) {
          cardArr[index] = c;
          index++;
      }
      return cardArr;
  }

}
