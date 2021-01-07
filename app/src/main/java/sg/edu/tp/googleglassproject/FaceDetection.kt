package sg.edu.tp.googleglassproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.common.InputImage
import kotlinx.android.synthetic.main.activity_face_detection.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

/*
    Google Codelabs: CameraX Getting Started
    https://codelabs.developers.google.com/codelabs/camerax-getting-started#0
 */

class FaceDetection : AppCompatActivity() {
    // private var imageCapture: ImageCapture? = null

    // private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

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
                    // .setTargetResolution(Size(1280, 720))
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

//                    .build()
//                    .also {
//                        it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                            Log.d(TAG, "Average luminosity: $luma")
//                        })
//                    }

            imageAnalysis.setAnalyzer(AsyncTask.THREAD_POOL_EXECUTOR, ImageAnalysis.Analyzer { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    Log.d(TAG, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                    // Pass image to an ML Kit Vision API

//                    // Change Face Detector's Settings
//                    // https://developers.google.com/ml-kit/vision/face-detection/android#1.-configure-the-face-detector
//                    val options  = FaceDetectorOptions.Builder()
//                            .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
//                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                            .build()

                    val detector = FaceDetection.getClient();

                    val result = detector.process(image)
                            // Task completed successfully
                            .addOnSuccessListener { faces ->
                                for (face in faces) {
                                    val bounds = face.boundingBox
                                    val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                                    val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

//                                    // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
//                                    // nose available):
//                                    val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
//                                    leftEar?.let {
//                                        val leftEarPos = leftEar.position
//                                    }

//                                    // If contour detection was enabled:
//                                    val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
//                                    val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
//
//                                    // If classification was enabled:
//                                    if (face.smilingProbability != null) {
//                                        val smileProb = face.smilingProbability
//                                    }
//                                    if (face.rightEyeOpenProbability != null) {
//                                        val rightEyeOpenProb = face.rightEyeOpenProbability
//                                    }
//
//                                    // If face tracking was enabled:
//                                    if (face.trackingId != null) {
//                                        val id = face.trackingId
//                                    }
                                }

                                Log.d(TAG, "Faces: $faces")
                                imageProxy.close()
                            }

                            // Task failed with an exception
                            .addOnFailureListener { e ->

                            }

                }

            })

            // Create a try block. Inside that block, make sure nothing is bound to your cameraProvider, and then bind your cameraSelector and preview object to the cameraProvider.
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)

            } catch(exc: Exception) { //There are a few ways this code could fail, like if the app is no longer in focus. Wrap this code in a catch block to log if there's a failure.
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

//    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
//
//        private fun ByteBuffer.toByteArray(): ByteArray {
//            rewind()    // Rewind the buffer to zero
//            val data = ByteArray(remaining())
//            get(data)   // Copy the buffer into a byte array
//            return data // Return the byte array
//        }
//
//        override fun analyze(image: ImageProxy) {
//
//            val buffer = image.planes[0].buffer
//            val data = buffer.toByteArray()
//            val pixels = data.map { it.toInt() and 0xFF }
//            val luma = pixels.average()
//
//            listener(luma)
//
//            image.close()
//        }
//    }

}
