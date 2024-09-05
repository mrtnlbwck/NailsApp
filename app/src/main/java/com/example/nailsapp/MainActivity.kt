package com.example.nailsapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

//import androidx.databinding.DataBindingUtil
import com.example.nailsapp.databinding.ActivityMainBinding
import com.example.nailsapp.ml.Model
import com.example.nailsapp.ml.ModelLAST22
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var cameraButton: Button
    private lateinit var result: TextView
    private lateinit var probabilityText: TextView
    private  var GALLERY_REQUEST_CODE = 123
    private lateinit var imageUrl: Uri

    private val contract = registerForActivityResult(ActivityResultContracts.TakePicture()){
        imageView.setImageURI(null)
        imageView.setImageURI(imageUrl)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView
        cameraButton = binding.cameraButton
        result = binding.result
        probabilityText = binding.probabilityText
        val loadButton = binding.launchButton

        imageUrl = createImageUri()

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR)).build();

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                ){
                takePicturePreview.launch(null)
            }
            else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }

        }

        loadButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            }
            else{
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }



    }
    private fun createImageUri():Uri{
        val image = File(filesDir, "camera_photos.png")
        return FileProvider.getUriForFile(this, "com.coding.nailsapp.FileProvider", image)
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if (granted){
            takePicturePreview.launch(null)
        }
        else{
            Toast.makeText(this, "Odmówiono dostępu do aparatu. ", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->
        if(bitmap != null){
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
        else{
            Toast.makeText(this, "Odmówiono dostępu do galerii. ", Toast.LENGTH_SHORT).show()
        }
    }

    //get image from gallery
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        Log.i("TAG", "This is the result: ${result.data} ${result.resultCode} ")
        onResultReceived(GALLERY_REQUEST_CODE, result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?){
        when(requestCode){
            GALLERY_REQUEST_CODE->{
                if(result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let{uri->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream((contentResolver.openInputStream(uri)))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                }
                else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
            }

        }
    }


    private fun outputGenerator(bitmap: Bitmap) {
        val model = ModelLAST22.newInstance(this)

        // Creates inputs for reference.
        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        // Runs model inference and gets result.
        val outputs = model.process(image)
        val probability = outputs.probabilityAsCategoryList

        // Variables to track the highest score and its corresponding label
        var maxScore = 0.0f
        var bestLabel = ""

        // Iterate through the probability list to find the label with the highest score
        for (category in probability) {
            val label = category.label
            val score = category.score

            if (score > maxScore) {
                maxScore = score
                bestLabel = label
            }

            Log.i("TAG", "Category: $label, Score: $score")
        }

        // Set the TextViews to display the label with the highest score
        result.text = bestLabel
        probabilityText.text = maxScore.toString()

        // Releases model resources if no longer used.
        model.close()
    }




}