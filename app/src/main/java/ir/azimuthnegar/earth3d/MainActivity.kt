package ir.azimuthnegar.earth3d

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ExternalTexture
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import ir.azimuthnegar.earth3d.common.CameraPermissionHelper
import ir.azimuthnegar.earth3d.common.FullScreenHelper
import java.io.IOException

const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {


    lateinit var externalTexture: ExternalTexture
    lateinit var mediaPlayer: MediaPlayer

    var mSession: Session? = null
    lateinit var arFragment: ArFragment
    lateinit var arSceneView: ArSceneView
    private var modelRenderable: ModelRenderable? = null
    private var modelAdded = false // add model once
    private var sessionConfigured = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        arFragment = (supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment)


        // hiding the plane discovery
        arFragment.planeDiscoveryController.hide()
        arFragment.planeDiscoveryController.setInstructionView(null)






        arSceneView = arFragment.arSceneView
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime: FrameTime? -> onUpdateFrame(frameTime!!) }


    }


    private fun setupAugmentedImageDb(config: Config): Boolean {
        val augmentedImageDatabase: AugmentedImageDatabase
        val augmentedImageBitmap: Bitmap = loadAugmentedImage() ?: return false
        augmentedImageDatabase = AugmentedImageDatabase(mSession)
        augmentedImageDatabase.addImage("earth", augmentedImageBitmap)
        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }


    private fun loadAugmentedImage(): Bitmap? {
        try {
            assets.open("earth.jpg").use { `is` -> return BitmapFactory.decodeStream(`is`) }
        } catch (e: IOException) {
            Log.e("ImageLoad", "IO Exception while loading", e)
        }
        return null
    }

    private fun onUpdateFrame(frameTime: FrameTime) {

        if (modelAdded) return

        val frame = arFragment.arSceneView.arFrame
        val augmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
        for (image in augmentedImages) {
            if (image.trackingState == TrackingState.TRACKING) {
                if (image.name.contains("earth") && !modelAdded) {
                    renderObject(arFragment,
                            image.createAnchor(image.centerPose),
                            R.raw.earth_ball)


                    playVideo(image.createAnchor(image.getCenterPose()), image.getExtentX(),
                            image.getExtentZ())


                }


            }
        }
    }


    private fun playVideo(anchor: Anchor, extentX: Float, extentZ: Float) {

        val anchorNode = AnchorNode(anchor)
        anchorNode.anchor = anchor
        anchorNode.worldScale = Vector3(extentX, 1f, extentZ)
        arFragment.arSceneView.scene.addChild(anchorNode)
    }

    private fun renderObject(fragment: ArFragment, anchor: Anchor, model: Int) {
        ModelRenderable.builder()
                .setSource(this, model)
                .build()
                .thenAccept { renderable: ModelRenderable? -> addNodeToScene(fragment, anchor, renderable!!) }
                .exceptionally { throwable: Throwable ->
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage(throwable.message)
                            .setTitle("Error!")
                    val dialog = builder.create()
                    dialog.show()
                    null
                }
    }


    private fun addNodeToScene(fragment: ArFragment, anchor: Anchor, renderable: Renderable) {
        val anchorNode = AnchorNode(anchor)
        val node = TransformableNode(fragment.transformationSystem)
        node.renderable = renderable
        node.setParent(anchorNode)
        fragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                    this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
                    .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    override fun onPause() {
        super.onPause()
        if (mSession != null) {
            arSceneView.pause()
            mSession!!.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mSession == null) {
            var message: String? = null
            var exception: Exception? = null
            try {
                mSession = Session(this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update android"
                exception = e
            } catch (e: Exception) {
                message = "AR is not supported"
                exception = e
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Exception creating session", exception)
                return
            }
            sessionConfigured = true
        }
        if (sessionConfigured) {
            configureSession()
            sessionConfigured = false
            arSceneView.setupSession(mSession)
        }
    }

    private fun configureSession() {
        val config = Config(mSession)
        if (!setupAugmentedImageDb(config)) {
            Toast.makeText(this, "Unable to setup augmented", Toast.LENGTH_SHORT).show()
        }
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        mSession!!.configure(config)
    }

}