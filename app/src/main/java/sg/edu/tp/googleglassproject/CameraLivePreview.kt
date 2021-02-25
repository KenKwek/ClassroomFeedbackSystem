package sg.edu.tp.googleglassproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.android.synthetic.main.activity_face_detection.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import sg.edu.tp.googleglassproject.ml.FaceRecognitionModel
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/*
    Google Codelabs: CameraX Getting Started
    https://codelabs.developers.google.com/codelabs/camerax-getting-started#0
*/

class CameraLivePreview : AppCompatActivity() {
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var byteBuffer: ByteBuffer
    private var feedbackStatus: Int = 0;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detection)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {
        // Create an instance of the ProcessCameraProvider. This is used to bind the lifecycle of cameras to the lifecycle owner.
        // This eliminates the task of opening and closing the camera since CameraX is lifecycle-aware.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // Add a listener to the cameraProviderFuture. Add a Runnable as one argument.
        // We will fill it in later. Add ContextCompat.getMainExecutor() as the second argument. This returns an Executor that runs on the main thread.
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            // In the Runnable, add a ProcessCameraProvider.
            // This is used to bind the lifecycle of your camera to the LifecycleOwner within the application's process.
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            // Initialize your Preview object, call build on it, get a surface provider from viewfinder, and then set it on the preview.
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            // Select back camera as a default
            // Create a CameraSelector object and select DEFAULT_BACK_CAMERA.
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

            imageAnalysis.setAnalyzer(AsyncTask.THREAD_POOL_EXECUTOR, ImageAnalysis.Analyzer { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val mediaImage = imageProxy.image

                val recognitionModel = FaceRecognitionModel.newInstance(this);

                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                    /* Convert to RGB Bitmap */
                    if (!::bitmapBuffer.isInitialized) {
                        // The image rotation and RGB image buffer are initialized only once
                        // the analyzer has started running
                        bitmapBuffer = Bitmap.createBitmap(
                                imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
                    }

                    val converter = YuvToRgbConverter(this)

                    // Convert the image to RGB and place it in our shared buffer
                    converter.yuvToRgb(mediaImage!!, bitmapBuffer);

                    // Crop Bitmap to 224X224
                    val bitmap = ThumbnailUtils.extractThumbnail(bitmapBuffer, 224, 224);

                    // Bitmap to ByteBuffer
                    //Calculate how many bytes our image consists of.
                    val bytes: Int = bitmap.getByteCount()
                    // or we can calculate bytes this way. Use a different value than 4 if you don't use 32bit images.
                    // int bytes = b.getWidth()*b.getHeight()*4;

                    // val byteBuffer = ByteBuffer.allocate(bytes) // Create a new buffer
                    val byteBuffer = ByteBuffer.allocate((224 * 224 * 3 * DataType.FLOAT32.byteSize())) // Create a new buffer
                    bitmap.copyPixelsToBuffer(byteBuffer) // Move the byte data to the buffer
                    val byteArray = byteBuffer.array() // Get the underlying array containing the data.

                    /* ML Kit - Face Detection */
                    // Pass image to an ML Kit Vision API

//                    // Change Face Detector's Settings
//                    // https://developers.google.com/ml-kit/vision/face-detection/android#1.-configure-the-face-detector
//                    val options  = FaceDetectorOptions.Builder()
//                            < SETTINGS OPTIONS >
//                            .build()
//
//                    val detector = FaceDetection.getClient(options);

                    val detector = FaceDetection.getClient();

                    val result = detector.process(image)
                            // Task completed successfully
                            .addOnSuccessListener { faces ->
                                for (face in faces) {
                                    val bounds = face.boundingBox

                                }

                                /* TensorFlow Lite - Face Recognition */
                                // Creates inputs for reference.
                                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
                                inputFeature0.loadBuffer(byteBuffer)

                                val outputs = recognitionModel.process(inputFeature0)
                                val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                                // Releases model resources if no longer used.
                                recognitionModel.close()

                                // ** Temporary Hardcode Predicted Result
                                val predictedName = "Unknown"

                                /*
                                    Firebase Cloud Firestore
                                    https://firebase.google.com/docs/firestore/quickstart
                                */

                                // Access a Cloud Firestore instance from your Activity
                                // Find and Extract predicted name in the Cloud Firestore -> users
                                val database = Firebase.firestore
                                database.collection("users").document(predictedName)
                                        .get()
                                        .addOnSuccessListener { document ->
                                            if (document.data != null) {
                                                val status: Boolean = document.getBoolean("FeedbackStatus") == true;
                                                if (!status) {
                                                    feedbackStatus = 1
                                                }
                                                else if (status) {
                                                    feedbackStatus = 2
                                                }
                                            }
                                        }

                                        .addOnFailureListener { exception ->
                                            Log.w(TAG, "Error getting 'Users' documents.", exception)
                                        }

                                /* Send Detected Face(s), predictedName & feedbackStatus to be drawn*/
                                processFaceResult(faces, predictedName, feedbackStatus)

                                imageProxy.close()
                            }

                            // Task failed with an exception
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Error running ML Kit - Face Detection", e)
                            }

                }

            })

            // Create a try block. Inside that block, make sure nothing is bound to your cameraProvider, and then bind your cameraSelector and preview object to the cameraProvider.
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)

            } catch (exc: Exception) { //There are a few ways this code could fail, like if the app is no longer in focus. Wrap this code in a catch block to log if there's a failure.
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        // Check if the request code is correct; ignore it if not.
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // If the permissions are granted, call startCamera()
            if (allPermissionsGranted()) {
                startCamera()
            } else { // If permissions are not granted, present a toast to notify the user that the permissions were not granted.
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun processFaceResult(faces: List<Face>, predictedName: String, feedbackStatus: Int) {
        // Remove previous bounding boxes
        graphic_overlay.clear()

        var statusBitmap: Bitmap = BitmapFactory.decodeResource(this.resources, R.drawable.transparent);

        if (feedbackStatus == 1) {
            statusBitmap = BitmapFactory.decodeResource(this.resources, R.drawable.redcross)
        }
        else if (feedbackStatus == 2) {
            statusBitmap = BitmapFactory.decodeResource(this.resources, R.drawable.greentick)
        }

        // Every Face Detected, sent BoundingBox Coordinate
        faces.forEach {
            val bounds = it.boundingBox
            val rectOverLay = RectOverlay(graphic_overlay, bounds, predictedName, statusBitmap)
            graphic_overlay.add(rectOverLay)
        }
    }

}
