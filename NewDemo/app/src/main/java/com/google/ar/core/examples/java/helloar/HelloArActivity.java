/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

//The following code was modified by Austin Williams for CS499 at the University of Kentucky ***
//This is part of a project involving recognizing poker/euchre hands in an environment ***
//And determining their value for either Euchre or Poker ***
//You can tell my comments from those included by Google with this demo by the three asterixes ***
//At the end of each line ***

package com.google.ar.core.examples.java.helloar;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import hand_determination.Card;
import hand_determination.Euchre;

import static hand_determination.Card.ACE_HIGH;
import static hand_determination.Card.CLUBS;
import static hand_determination.Card.DIAMONDS;
import static hand_determination.Card.HEARTS;
import static hand_determination.Card.JACK;
import static hand_determination.Card.KING;
import static hand_determination.Card.QUEEN;
import static hand_determination.Card.SPADES;


public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private TapHelper tapHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

  private final ObjectRenderer heart = new ObjectRenderer();
  private final ObjectRenderer club = new ObjectRenderer();
  private final ObjectRenderer spade = new ObjectRenderer();
  private final ObjectRenderer diamond = new ObjectRenderer();

  private final ObjectRenderer ace = new ObjectRenderer();
  private final ObjectRenderer two = new ObjectRenderer();
  private final ObjectRenderer three = new ObjectRenderer();
  private final ObjectRenderer four = new ObjectRenderer();
  private final ObjectRenderer five = new ObjectRenderer();
  private final ObjectRenderer six = new ObjectRenderer();
  private final ObjectRenderer seven = new ObjectRenderer();
  private final ObjectRenderer eight = new ObjectRenderer();
  private final ObjectRenderer nine = new ObjectRenderer();
  private final ObjectRenderer ten = new ObjectRenderer();
  private final ObjectRenderer jack = new ObjectRenderer();
  private final ObjectRenderer queen = new ObjectRenderer();
  private final ObjectRenderer king = new ObjectRenderer();



  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];

  //Temporary matrix used to store a pose directly above the one of each Anchor, declared here for ***
  //speed and optimization.  (reducing work load per frame rendered) ***
  private final float[] topPoseMatrix = new float[16];

  private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

  //HashMap to map each anchor created to a card holding the suit and value that were selected ***
  //At the time of anchor creation ***
  private Map <ColoredAnchor, hand_determination.Card> cardTypes;

  //bytes to store the current suit selected and the current card value ***
  //These are used in the Card class to represent pre-specified values ***
  private byte currentSuit;
  private byte currentCardValue;
  private byte trumpSuit;
  private ArrayList<Card> trackingCards;
  private String message;
  private boolean showMessage;

  //Drop down menus to select the card value, card suit, and trump suit before tapping on a card in the environment ***
  private Spinner valueSpinner;
  private Spinner trumpSuitSpinner;
  private Spinner suitSpinner;

  //Buttons to clear out the currently selected cards, as well as to ***
  //Get the winner of the Euchre trick/Poker hand from the cards presented.  ***
  private Button resetButton;
  private Button submitButton;

  // Anchors created from taps used for object placing with a given color.
  private static class ColoredAnchor {
    public final Anchor anchor;
    public final float[] color;

    public ColoredAnchor(Anchor a, float[] color4f) {
      this.anchor = a;
      this.color = color4f;
    }
  }

  private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up tap listener.
    tapHelper = new TapHelper(/*context=*/ this);
    surfaceView.setOnTouchListener(tapHelper);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;

    //Map each anchor to a Card created from the suit and value selected when the anchor is created. ***
    cardTypes = new HashMap<>();

    //byte values to represent the suit and value of the card the user is about to select ***
    currentSuit = -1;
    currentCardValue = -1;
    trumpSuit = SPADES;
    showMessage = false;

    trackingCards = new ArrayList<Card>();

    //add listeners to the drop down menus ***

    //add listener to the value selection drop down, causing it to update currentCardValue accordingly ***
    valueSpinner = findViewById(R.id.valueSpinner);
    valueSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selectedItem = adapterView.getItemAtPosition(i).toString();
        switch (selectedItem) {
          case "Two": {
            currentCardValue = 2;
          }
          case "Three": {
            currentCardValue = 3;
          }
          case "Four": {
            currentCardValue = 4;
          }
          case "Five": {
            currentCardValue = 5;
          }
          case "Six": {
            currentCardValue = 6;
          }
          case "Seven": {
            currentCardValue = 7;
          }
          case "Eight": {
            currentCardValue = 8;
          }
          case "Nine": {
            currentCardValue = 9;
          }
          case "Ten": {
            currentCardValue = 10;
          }
          case "Jack": {
            currentCardValue = JACK;
          }
          case "Queen": {
            currentCardValue = QUEEN;
          }
          case "King": {
            currentCardValue = KING;
          }
          case "Ace": {
            currentCardValue = ACE_HIGH;
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
        //Do nothing ***
      }
    });

    //add listener to the suit selection drop down, and update currentSuit accordingly ***
    suitSpinner = findViewById(R.id.suitSpinner);
    suitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selectedItem = adapterView.getItemAtPosition(i).toString();
        switch (selectedItem) {
          case "Hearts": {
            currentSuit = HEARTS;
          }
          case "Diamonds": {
            currentSuit = DIAMONDS;
          }
          case "Clubs": {
            currentSuit = CLUBS;
          }
          case "Spades": {
            currentSuit = SPADES;
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
        //Do nothing ***
      }
    });

    //add listener to the trump suit selection drop down, and update trumpSuit accordingly ***
    trumpSuitSpinner = findViewById(R.id.trumpSuitSpinner);
    trumpSuitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selectedItem = adapterView.getItemAtPosition(i).toString();
        switch (selectedItem) {
          case "Hearts": {
            trumpSuit = HEARTS;
          }
          case "Diamonds": {
            trumpSuit = DIAMONDS;
          }
          case "Clubs": {
            trumpSuit = CLUBS;
          }
          case "Spades": {
            trumpSuit = SPADES;
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
        //Do nothing ***
      }
    });

    //add listener to the Submit button, telling you which card wins of the ones you have selected ****
    submitButton = findViewById(R.id.submitButton);
    submitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
          if (trackingCards.size() == 0) {
              showMessage = true;
              message = "Please indicate which cards you'd like to process!";
              return;
          } else {
              showMessage = true;
              Card[] trick = new Card[trackingCards.size()];
              for (int i = 0; i < trick.length; i++) {
                  trick[i] = trackingCards.get(i);
              }
              Card winningCard = trick[Euchre.trickWinner(trick, trumpSuit)];
              message = winningCard.toString() + " WINS!";
          }
      }
    });

    //add listener to the Reset button, erasing all of the 3D objects currently being rendered ***
    //and removes them from the trackingCards array for the sake of counting towards a win ***
    resetButton = findViewById(R.id.resetButton);
    resetButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        trackingCards = new ArrayList<Card>();
        while (anchors.size() > 0) {
          anchors.get(0).anchor.detach();
          anchors.remove(0);
        }
      }
    });
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

        // Create the session.
        session = new Session(/* context= */ this);

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
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
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

    messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
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
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
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
      planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(/*context=*/ this);

      //Create the ObjectRenderers that will be used to render the 3D objects on the cards ***

      //HEARTS object renderer ***
      heart.createOnGlThread(/*context=*/ this, "models/Card Type Models/Heart.obj", "models/Card Type Models/red.png");
      heart.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //DIAMONDS object renderer ***
      diamond.createOnGlThread(/*context=*/ this, "models/Card Type Models/Diamond.obj", "models/Card Type Models/red.png");
      diamond.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //CLUBS object renderer ***
      club.createOnGlThread(/*context=*/ this, "models/Card Type Models/Club.obj", "models/Card Type Models/black.png");
      club.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //SPADES object renderer ***
      spade.createOnGlThread(/*context=*/ this, "models/Card Type Models/Spade.obj", "models/Card Type Models/black.png");
      spade.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //ACE object renderer ***
      ace.createOnGlThread(/*context=*/ this, "models/Card Type Models/Ace.obj", "models/Card Type Models/black.png");
      ace.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //TWO object renderer ***
      two.createOnGlThread(/*context=*/ this, "models/Card Type Models/Two.obj", "models/Card Type Models/black.png");
      two.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //THREE object renderer ***
      three.createOnGlThread(/*context=*/ this, "models/Card Type Models/Three.obj", "models/Card Type Models/black.png");
      three.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //FOUR object renderer ***
      four.createOnGlThread(/*context=*/ this, "models/Card Type Models/Four.obj", "models/Card Type Models/black.png");
      four.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //FIVE object renderer ***
      five.createOnGlThread(/*context=*/ this, "models/Card Type Models/Five.obj", "models/Card Type Models/black.png");
      five.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //SIX object renderer ***
      six.createOnGlThread(/*context=*/ this, "models/Card Type Models/Six.obj", "models/Card Type Models/black.png");
      six.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //SEVEN object renderer ***
      seven.createOnGlThread(/*context=*/ this, "models/Card Type Models/Seven.obj", "models/Card Type Models/black.png");
      seven.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //EIGHT object renderer ***
      eight.createOnGlThread(/*context=*/ this, "models/Card Type Models/Eight.obj", "models/Card Type Models/black.png");
      eight.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //NINE object renderer ***
      nine.createOnGlThread(/*context=*/ this, "models/Card Type Models/Nine.obj", "models/Card Type Models/black.png");
      nine.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //TEN object renderer ***
      ten.createOnGlThread(/*context=*/ this, "models/Card Type Models/Ten.obj", "models/Card Type Models/black.png");
      ten.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //JACK object renderer ***
      jack.createOnGlThread(/*context=*/ this, "models/Card Type Models/Jack.obj", "models/Card Type Models/black.png");
      jack.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //QUEEN object renderer ***
      queen.createOnGlThread(/*context=*/ this, "models/Card Type Models/Queen.obj", "models/Card Type Models/black.png");
      queen.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      //KING object renderer ***
      king.createOnGlThread(/*context=*/ this, "models/Card Type Models/King.obj", "models/Card Type Models/black.png");
      king.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
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

      // Handle one tap per frame.
      handleTap(frame, camera);

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
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      pointCloudRenderer.update(pointCloud);
      pointCloudRenderer.draw(viewmtx, projmtx);

      // Application is responsible for releasing the point cloud resources after
      // using it.
      pointCloud.release();

      // Check if we detected at least one plane. If so, hide the loading message.
      if (messageSnackbarHelper.isShowing()) {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
          if (plane.getTrackingState() == TrackingState.TRACKING) {
            messageSnackbarHelper.hide(this);
            break;
          }
        }
      }

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // Visualize anchors created by touch.
      float scaleFactor = 1.0f;
      for (ColoredAnchor coloredAnchor : anchors) {
        if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

        //Creating a pose above the position of the Anchor so that the card value object can be rendered above ***
        //its suit object ***
        Pose topPose = coloredAnchor.anchor.getPose();
        topPose = Pose.makeTranslation(topPose.tx(), topPose.ty()+1, topPose.tz());
        topPose.toMatrix(topPoseMatrix, 0);

        //Determine the card suit so the correct object can be rendered in the environment ***
        Card temp = cardTypes.get(coloredAnchor);
        switch(temp.getSuit()) {
          case HEARTS: { //DIAMONDS suit from hand_determination.Card ***
            heart.updateModelMatrix(anchorMatrix, scaleFactor);
            heart.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case DIAMONDS: { //DIAMONDS suit from hand_determination.Card ***
            diamond.updateModelMatrix(anchorMatrix, scaleFactor);
            diamond.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case CLUBS: { //CLUBS suit from hand_determination.Card ***
            club.updateModelMatrix(anchorMatrix, scaleFactor);
            club.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case SPADES: { //SPADES suit from hand_determination.Card ***
            spade.updateModelMatrix(anchorMatrix, scaleFactor);
            spade.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
        }

        //Now determine the appropriate card value object to render, and render it above the suit ***
        switch(temp.getValue()) {
          case 2: {
            two.updateModelMatrix(topPoseMatrix, scaleFactor);
            two.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 3: {
            three.updateModelMatrix(topPoseMatrix, scaleFactor);
            three.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 4: {
            four.updateModelMatrix(topPoseMatrix, scaleFactor);
            four.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 5: {
            five.updateModelMatrix(topPoseMatrix, scaleFactor);
            five.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 6: {
            six.updateModelMatrix(topPoseMatrix, scaleFactor);
            six.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 7: {
            seven.updateModelMatrix(topPoseMatrix, scaleFactor);
            seven.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 8: {
            eight.updateModelMatrix(topPoseMatrix, scaleFactor);
            eight.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 9: {
            nine.updateModelMatrix(topPoseMatrix, scaleFactor);
            nine.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case 10: {
            ten.updateModelMatrix(topPoseMatrix, scaleFactor);
            ten.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case JACK: {
            jack.updateModelMatrix(topPoseMatrix, scaleFactor);
            jack.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case QUEEN: {
            queen.updateModelMatrix(topPoseMatrix, scaleFactor);
            queen.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case KING: {
            king.updateModelMatrix(topPoseMatrix, scaleFactor);
            king.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
          case ACE_HIGH: {
            ace.updateModelMatrix(topPoseMatrix, scaleFactor);
            ace.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
          }
        }
        //virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
        //virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
        //virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
        //virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
    if (showMessage) {
        messageSnackbarHelper.showMessageWithDismiss(this, message);
    }
  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
  private void handleTap(Frame frame, Camera camera) {
    MotionEvent tap = tapHelper.poll();
    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(tap)) {
        // Check if any plane was hit, and if it was hit inside the plane polygon
        Trackable trackable = hit.getTrackable();
        // Creates an anchor if a plane or an oriented point was hit.
        if ((trackable instanceof Plane
                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
            || (trackable instanceof Point
                && ((Point) trackable).getOrientationMode()
                    == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
          // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
          // Cap the number of objects created. This avoids overloading both the
          // rendering system and ARCore.
          if (anchors.size() >= 20) {
            anchors.get(0).anchor.detach();
            anchors.remove(0);
            trackingCards.remove(0);
          }

          // Assign a color to the object for rendering based on the suit of card it's supposed to represent ***
          // HEARTS and DIAMONDS are Red, while CLUBS and SPADES are black ***
          float[] objColor;
          if (currentSuit == HEARTS || currentSuit == DIAMONDS) {
            objColor = new float[] {255.0f, 255.0f, 0.0f, 0.0f};
          } else if (currentSuit == CLUBS || currentSuit == SPADES) {
            objColor = new float[] {255.0f, 0.0f, 0.0f, 0.0f};
          } else {
            objColor = DEFAULT_COLOR;
          }

          // Adding an Anchor tells ARCore that it should track this position in
          // space. This anchor is created on the Plane to place the 3D model
          // in the correct position relative both to the world and to the plane.
          ColoredAnchor newAnchor = new ColoredAnchor(hit.createAnchor(), objColor);

          //Create a new card representing the one just tapped by the user, and add that card ***
          //To the ArrayList representing the Euchre cards currently being tracked ***
          Card newCard = new Card(currentSuit, currentCardValue);
          trackingCards.add(newCard);

          //Map the location the User just tapped to a particular Card ***
          cardTypes.put(newAnchor, newCard);

          anchors.add(newAnchor);
          break;
        }
      }
    }
  }

}
