/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cloudvision;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import us.monoid.web.Resty;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.WebDetection;
import com.google.api.services.vision.v1.model.WebEntity;
import com.google.api.services.vision.v1.model.WebImage;
import com.google.api.services.vision.v1.model.WebLabel;
import com.google.api.services.vision.v1.model.WebPage;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final String CLOUD_VISION_API_KEY = BuildConfig.API_KEY;
    private static final String PUBLIC_MARVEL_API_KEY = BuildConfig.PUBLIC_MARVEL_API_KEY;
    private static final String PRIVATE_MARVEL_API_KEY = BuildConfig.PRIVATE_MARVEL_API_KEY;
    private static final String PRIVATE_SEARCH_API_KEY = BuildConfig.PRIVATE_SEARCH_API_KEY;
    private static final String SEARCH_CX_KEY = BuildConfig.SEARCH_CX_KEY;
    public static final String FILE_NAME = "temp.jpg";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;

    private TextView mImageDetails;
    private TextView mMarvelInfo;
    private TextView mDetailedInfo;
    private TextView mDescriptionName;
    private ImageView mMainImage;
    private TextView mLink;
    private static String moreLink = "";
    private static String marvelInfoString = "";
    private static String detailedInfoString = "";
    private static String descriptionNameString = "";
    public static boolean isMarvel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder
                        .setMessage(R.string.dialog_select_prompt)
                        .setPositiveButton(R.string.dialog_select_gallery, (dialog, which) -> startGalleryChooser())
                        .setNegativeButton(R.string.dialog_select_camera, (dialog, which) -> startCamera());
                FloatingActionButton fab = findViewById(R.id.fab);
                fab.setOnClickListener((View view) -> {
                    builder.setCancelable(true);
                    builder.create().show();
                });
                AlertDialog alertdialog=builder.setCancelable(false).create();
                alertdialog.show();
            }
        }, 5);

        mImageDetails = findViewById(R.id.image_details);
        mLink = findViewById(R.id.link);
        mMainImage = findViewById(R.id.main_image);
        mDetailedInfo = findViewById(R.id.detailedInfo);
        mMarvelInfo = findViewById(R.id.marvelInfo);
        mDescriptionName = findViewById(R.id.descriptionName);
    }

    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        moreLink = "";

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                    startCamera();
                }
                break;
            case GALLERY_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                    startGalleryChooser();
                }
                break;
        }
    }

    /**
     * Rotate an image if required.
     *
     * @param img           The image bitmap
     * @param selectedImage Image URI
     * @return The resulted Bitmap after manipulation
     */
    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                MAX_DIMENSION);

                bitmap = rotateImageIfRequired(getApplicationContext(), bitmap, uri);

                callCloudVision(bitmap);
                mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature labelDetection = new Feature();
                labelDetection.setType("WEB_DETECTION");
                labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(labelDetection);
            }});

            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }

    private static class LableDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        LableDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
        }

        @Override
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }

        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.image_details);
                TextView link = activity.findViewById(R.id.link);
                TextView mDetailedInfo = activity.findViewById(R.id.detailedInfo);
                TextView mMarvelInfo = activity.findViewById(R.id.marvelInfo);
                TextView mDescriptionName = activity.findViewById(R.id.descriptionName);
                FloatingActionButton button = activity.findViewById(R.id.fab);

                button.setVisibility(View.VISIBLE);

                imageDetail.setText(result);
                mMarvelInfo.setText(marvelInfoString);
                mDescriptionName.setText(descriptionNameString);
                mDetailedInfo.setText(detailedInfoString);

                link.setMovementMethod(LinkMovementMethod.getInstance());
                link.setText(Html.fromHtml(moreLink));

                if (isMarvel) {
                    Typeface tf = ResourcesCompat.getFont(activity.getBaseContext(), R.font.bangers);
                    mMarvelInfo.setTypeface(tf);
                    imageDetail.setTypeface(tf);
                }
                //link.setText(moreLink);
            }
        }
    }

        private void callCloudVision(final Bitmap bitmap) {
        // Switch text to loading
        mImageDetails.setText(R.string.loading_message);
            ImageView logo = findViewById(R.id.main_image);

            //Fade
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.3f, .9f);
            ObjectAnimator alphaAnimator1 = ObjectAnimator.ofFloat(logo, View.TRANSLATION_X, 40, -40);

            alphaAnimator.setDuration(650);
            alphaAnimator.setRepeatMode(ValueAnimator.REVERSE);
            alphaAnimator.setRepeatCount(10);
            alphaAnimator.start();

            alphaAnimator1.setDuration(1300);
            alphaAnimator1.setRepeatMode(ValueAnimator.REVERSE);
            alphaAnimator1.setRepeatCount(4);
            alphaAnimator1.start();

            mLink.setText("");

        // Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, String> labelDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap));
            labelDetectionTask.execute();
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

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

    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder("I found these things");

        List<AnnotateImageResponse> responses = response.getResponses();


        for (AnnotateImageResponse res : responses) {
            if (res.getError() != null) {
                System.out.printf("Error: %s", res.getError().getMessage());
                message.append("nothing");
            } else {
                WebDetection annotation = res.getWebDetection();

                //For loop for future, lots of different characters in the same panel
                //for (WebEntity entity : annotation.getWebEntities()) {
//                    message.append(entity.getWebEntities().get(0).getDescription());
                String name = annotation.getWebEntities().get(0).getDescription();
                if (name != null) {
                    name = name.replaceAll(" ","%20");
                }
                //System.out.println(name);

                long timeStamp = System.currentTimeMillis();
                String tS = Long.toString(timeStamp);

                String stringToHash = tS + PRIVATE_MARVEL_API_KEY + PUBLIC_MARVEL_API_KEY;
                String hash = DigestUtils.md5Hex(stringToHash);

                String url = String.format("http://gateway.marvel.com/v1/public/characters?nameStartsWith=%s&ts=%d&apikey=%s&hash=%s",
                        name, timeStamp, PUBLIC_MARVEL_API_KEY, hash);

                StringBuilder description = new StringBuilder();
                StringBuilder marvelInfo = new StringBuilder();
                StringBuilder descriptionName = new StringBuilder();
                try {
                    String output = new Resty().text(url).toString();
                    try {
                        JSONObject des = new JSONObject(output);
                        int code = des.getInt("code");
                        if(code == 200 && des.getJSONObject("data").getInt("count") > 1) {
                            marvelInfo.append("This is a Marvel Character!");
                            isMarvel = true;
                            descriptionName.append("Name: " + des.getJSONObject("data").getJSONArray("results").getJSONObject(0).optString("name"));
                            String descript = des.getJSONObject("data").getJSONArray("results").getJSONObject(0).getString("description");
                            String wiki = des.getJSONObject("data").getJSONArray("results").getJSONObject(0).getJSONArray("urls").getJSONObject(1).getString("url");
                            StringBuilder urlStr = new StringBuilder();
                            urlStr.append("<a href=\"" + wiki + "\">More info here!</a>");
                            moreLink = urlStr.toString();
                            description.append(descript);
                        } else {
                            descriptionName.append("Name: " + annotation.getWebEntities().get(0).getDescription());
                            marvelInfo.append("Not a Marvel character, but here's what I found from Google:");
                            isMarvel = false;
                            String searchURL = String.format("https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=1", PRIVATE_SEARCH_API_KEY, SEARCH_CX_KEY, name);
                            try {
                                String searchOutput = new Resty().text(searchURL).toString();
                                try {
                                    JSONObject searchRes = new JSONObject(searchOutput);
                                    JSONArray searchItems = searchRes.getJSONArray("items");
                                    String snippet = searchItems.getJSONObject(0).getString("snippet").replaceAll("\\r\\n|\\r|\\n", "");
                                    description.append(snippet);
                                    if (name != null) {
                                        name = name.replaceAll("%20","+");
                                    }
                                    StringBuilder urlStr = new StringBuilder();
                                    urlStr.append("<a href=\"https://www.google.com/search?q=" + name + "\">More info here!</a>");
                                    moreLink = urlStr.toString();
                                } catch (JSONException e) {
                                    System.out.print("jSoN eXCepTIoN");
                                }
                            } catch (IOException e) {
                                System.out.println("Google died");
                            }
                        }
                    } catch (JSONException e) {
                        System.out.print("jSoN eXCepTIoN");
                    }

                    marvelInfoString = marvelInfo.toString();
                    descriptionNameString = descriptionName.toString();
                    detailedInfoString = description.toString();
                } catch (IOException io) {
                    System.out.println("no");
                }
            }
        }
        return message.toString();

    }
}
