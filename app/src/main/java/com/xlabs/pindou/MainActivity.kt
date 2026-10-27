package com.xlabs.pindou

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.xlabs.pindou.pattern.BeadPattern
import com.xlabs.pindou.pattern.PatternGenerator
import com.xlabs.pindou.pattern.PatternRenderer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val imageLoader by lazy { ImageLoader(this) }
    private var sourceBitmap: Bitmap? = null
    private var currentPattern: BeadPattern? = null
    private var cameraUri: Uri? = null

    private lateinit var patternView: PatternView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var columnsText: TextView
    private lateinit var columnsSlider: Slider
    private lateinit var backgroundSwitch: SwitchMaterial
    private lateinit var gridSwitch: SwitchMaterial
    private lateinit var symbolsSwitch: SwitchMaterial
    private lateinit var generateButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::loadImage)
    }
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) loadImage(uri)
    }
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) exportPattern(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        patternView = findViewById(R.id.patternView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        columnsText = findViewById(R.id.columnsText)
        columnsSlider = findViewById(R.id.columnsSlider)
        backgroundSwitch = findViewById(R.id.backgroundSwitch)
        gridSwitch = findViewById(R.id.gridSwitch)
        symbolsSwitch = findViewById(R.id.symbolsSwitch)
        generateButton = findViewById(R.id.generateButton)
        saveButton = findViewById(R.id.saveButton)

        findViewById<MaterialButton>(R.id.cameraButton).setOnClickListener { openCamera() }
        findViewById<MaterialButton>(R.id.galleryButton).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        columnsSlider.addOnChangeListener { _, value, _ ->
            columnsText.text = getString(R.string.columns_value, value.toInt())
        }
        val optionsListener = { _: android.widget.CompoundButton, _: Boolean ->
            patternView.setOptions(gridSwitch.isChecked, symbolsSwitch.isChecked)
        }
        gridSwitch.setOnCheckedChangeListener(optionsListener)
        symbolsSwitch.setOnCheckedChangeListener(optionsListener)
        generateButton.setOnClickListener { generatePattern() }
        saveButton.setOnClickListener {
            val pattern = currentPattern ?: return@setOnClickListener
            createDocument.launch("pindou-${pattern.columns}x${pattern.rows}.png")
        }
    }

    private fun openCamera() {
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    private fun loadImage(uri: Uri) {
        setBusy(true, getString(R.string.loading_image))
        executor.execute {
            runCatching { imageLoader.load(uri) }
                .onSuccess { bitmap ->
                    runOnUiThread {
                        sourceBitmap?.recycle()
                        sourceBitmap = bitmap
                        setBusy(false, getString(R.string.image_loaded, bitmap.width, bitmap.height))
                        generatePattern()
                    }
                }
                .onFailure { error -> showError(error) }
        }
    }

    private fun generatePattern() {
        val bitmap = sourceBitmap ?: return
        val request = generation.incrementAndGet()
        val columns = columnsSlider.value.toInt()
        val removeBackground = backgroundSwitch.isChecked
        setBusy(true, getString(R.string.generating))
        executor.execute {
            runCatching {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                PatternGenerator.generate(pixels, bitmap.width, bitmap.height, columns, removeBackground)
            }.onSuccess { pattern ->
                runOnUiThread {
                    if (request != generation.get()) return@runOnUiThread
                    currentPattern = pattern
                    patternView.setPattern(pattern)
                    patternView.setOptions(gridSwitch.isChecked, symbolsSwitch.isChecked)
                    saveButton.isEnabled = true
                    setBusy(false, getString(R.string.pattern_summary, pattern.columns, pattern.rows, pattern.totalBeads, pattern.legend.size))
                }
            }.onFailure { error -> showError(error) }
        }
    }

    private fun exportPattern(uri: Uri) {
        val pattern = currentPattern ?: return
        val showGrid = gridSwitch.isChecked
        val showSymbols = symbolsSwitch.isChecked
        setBusy(true, getString(R.string.exporting))
        executor.execute {
            runCatching {
                val bitmap = PatternRenderer.createBitmap(pattern, showGrid, showSymbols)
                contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "无法打开保存位置" }
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG 导出失败" }
                }
                bitmap.recycle()
            }.onSuccess {
                runOnUiThread {
                    setBusy(false, getString(R.string.export_success))
                    Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error -> showError(error) }
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        statusText.text = message
        generateButton.isEnabled = !busy && sourceBitmap != null
        saveButton.isEnabled = !busy && currentPattern != null
    }

    private fun showError(error: Throwable) {
        runOnUiThread {
            setBusy(false, getString(R.string.ready))
            Toast.makeText(this, error.message ?: getString(R.string.operation_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        executor.shutdown()
        super.onDestroy()
    }
}
