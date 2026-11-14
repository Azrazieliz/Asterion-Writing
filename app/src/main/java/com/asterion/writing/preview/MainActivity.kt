package com.asterion.writing.preview

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.asterion.writing.QualityGate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.File

/** Mobile pilot for the future Art + Sound + 3D + Writing merged production app. */
class MainActivity : Activity() {
    private val bg = Color.rgb(12, 17, 26)
    private val panel = Color.rgb(23, 33, 46)
    private val mint = Color.rgb(159, 228, 212)
    private val white = Color.rgb(244, 247, 251)
    private val muted = Color.rgb(169, 185, 201)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var tab = 0
    private var statusView: TextView? = null
    private var busy = false
    private var generation: Job? = null
    private var loaded: String? = null
    private var engine: InferenceEngine? = null
    private var prompt = "Invent an original fantasy web novel with complex characters, layered consequences, mature themes, and a distinctive world. Begin with a consequential scene."
    private var canon = ""
    private var generated = ""
    private var tokenCap = 768
    private var selectedFile: ModelFile? = null
    private var files: List<ModelFile> = emptyList()
    private var repoText: EditText? = null
    private var outputView: TextView? = null
    private var modelList: Spinner? = null
    private val prefs by lazy { getSharedPreferences("writer", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        prompt = prefs.getString("prompt", prompt) ?: prompt
        canon = prefs.getString("canon", "") ?: ""
        generated = prefs.getString("generated", "") ?: ""
        build()
        handler.post(object: Runnable {
            override fun run() {
                if (!isFinishing) {
                    if (tab == 1) statusView?.text = prefs.getString(ModelDownloadService.KEY_STATUS, "No active download")
                    handler.postDelayed(this, 900)
                }
            }
        })
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 14f, color: Int = white, strong: Boolean = false): TextView {
        val t = TextView(this); t.text = value; t.textSize = size; t.setTextColor(color)
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        t.setPadding(0, dp(7), 0, dp(7)); return t
    }
    private fun field(value: String, lines: Int = 1, hint: String = ""): EditText {
        val f = EditText(this); f.setSingleLine(lines == 1); f.setText(value); f.hint = hint
        f.setTextColor(white); f.setHintTextColor(muted); f.textSize = 15f
        f.setBackgroundColor(panel); f.setPadding(dp(14), dp(12), dp(14), dp(12))
        if (lines > 1) { f.minLines = lines; f.maxLines = 12; f.gravity = Gravity.TOP; f.inputType = 0x20001 or 0x20000 }
        return f
    }
    private fun button(value: String, action: () -> Unit, primary: Boolean = false): Button {
        val b = Button(this); b.text = value; b.isAllCaps = false; b.textSize = 13.5f
        b.setTextColor(if (primary) bg else white); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (primary) mint else panel))
        b.setOnClickListener { action() }; return b
    }
    private fun group(v: View, margins: Int = 6) {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, dp(margins), 0, dp(margins)); content.addView(v, p)
    }
    private fun build() {
        val outer = LinearLayout(this); outer.orientation = LinearLayout.VERTICAL; outer.setBackgroundColor(bg)
        val header = LinearLayout(this); header.orientation = LinearLayout.VERTICAL; header.setPadding(dp(20), dp(12), dp(20), dp(4))
        header.addView(label("ASTERION / WRITING", 20f, mint, true))
        header.addView(label("Private on-device fiction test · no cloud inference", 12f, muted))
        outer.addView(header)
        val nav = LinearLayout(this); nav.setPadding(dp(12), 0, dp(12), 0)
        val w = button("WRITER", { tab = 0; build() }, tab == 0)
        val m = button("MODELS", { tab = 1; build() }, tab == 1)
        nav.addView(w, LinearLayout.LayoutParams(0, dp(52), 1f)); nav.addView(m, LinearLayout.LayoutParams(0, dp(52), 1f))
        outer.addView(nav)
        val scroll = ScrollView(this); scroll.isFillViewport = true
        content = LinearLayout(this); content.orientation = LinearLayout.VERTICAL; content.setPadding(dp(20), dp(8), dp(20), dp(36))
        scroll.addView(content); outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
        if (tab == 0) writerPage() else modelPage()
    }
    private fun writerPage() {
        group(label("STORY TEST BENCH", 17f, white, true))
        group(label("Real generation from one installed GGUF. Each trial is an independently reviewable draft; Nexus will own the library and reading experience.", 13f, muted))
        val model = loaded ?: "No model loaded"
        group(label("Active model: $model", 13f, if (loaded == null) muted else mint))
        group(button("Manage or load models", { tab = 1; build() }))
        group(label("Story brief", 14f, white, true))
        val promptInput = field(prompt, 5)
        promptInput.addTextChangedListener(watcher { prompt = it; prefs.edit().putString("prompt", it).apply() }); group(promptInput)
        group(label("Canon / constraints (optional)", 14f, white, true))
        val canonInput = field(canon, 3, "World rules, established characters, what must stay consistent")
        canonInput.addTextChangedListener(watcher { canon = it; prefs.edit().putString("canon", it).apply() }); group(canonInput)
        group(label("Output tokens (choose length)", 13f, muted))
        val tok = Spinner(this)
        val choices = listOf(384, 768, 1280, 2048)
        tok.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { "$it tokens" })
        tok.setSelection(choices.indexOf(tokenCap).coerceAtLeast(0))
        tok.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { tokenCap = choices[pos] }
        }; group(tok)
        group(button("Generate original fiction", { generate() }, true))
        group(button("Stop generation", { generation?.cancel(); info("Generation stopped; the existing text remains saved.") }))
        group(label("DRAFT / LIVE OUTPUT", 17f, white, true))
        outputView = label(generated.ifBlank { "Your generated prose will appear here. No synthetic sample text is displayed." }, 15f)
        outputView!!.setTextIsSelectable(true); group(outputView!!)
        group(button("Share draft / send to Nexus", { shareDraft() }))
        group(label("Review gate: generated text is never automatically approved or published. Nexus integration is pending the actual interface contract.", 12f, muted))
    }
    private fun modelPage() {
        group(label("HUGGING FACE / DIRECT INSTALL", 17f, white, true))
        group(label("Choose a verified public GGUF repository or enter any Hugging Face owner/model. Download, resume and verify directly in this app.", 13f, muted))
        val names = HuggingFaceCatalog.presets.keys.toList()
        val preset = Spinner(this); preset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        group(preset)
        group(button("Use selected model", { repoText?.setText(HuggingFaceCatalog.presets[names[preset.selectedItemPosition]]) }))
        group(label("Hugging Face repository", 13f, muted))
        repoText = field(prefs.getString("repo", HuggingFaceCatalog.presets.values.first()) ?: "")
        group(repoText!!)
        group(button("List available GGUF checkpoints", {
            val repo = repoText?.text.toString()
            asyncRequest("Finding GGUF files", { HuggingFaceCatalog.ggufs(repo) }) { data ->
                files = data; selectedFile = data.firstOrNull(); prefs.edit().putString("repo", HuggingFaceCatalog.normalizeRepo(repo)).apply()
                info("Found ${data.size} GGUF checkpoints"); build()
            }
        }, true))
        if (files.isNotEmpty()) {
            group(label("SELECT QUANTIZATION", 14f, white, true))
            val fileSpin = Spinner(this)
            fileSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, files.map { it.label })
            fileSpin.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { selectedFile = files[pos] }
            }
            group(fileSpin)
            group(button("Download selected model", {
                val choice = files.getOrNull(fileSpin.selectedItemPosition) ?: return@button
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 19)
                    ModelDownloadService.start(this, choice); info("Download started · background capable")
                } catch(e: Exception) { info("Download failed to start: ${e.message}") }
            }, true))
            group(label("Large models require enough free phone storage. 8B Q4 models are around 5 GB, plus additional inference RAM.", 12f, muted))
        }
        statusView = label(prefs.getString(ModelDownloadService.KEY_STATUS, "No active download") ?: "No active download", 13f, mint)
        group(statusView!!)
        group(button("Pause current download", { ModelDownloadService.cancel(this); info("Pause requested; download can resume later") }))
        group(label("LOCAL MODELS", 17f, white, true))
        val local = ModelDownloadService.modelsDir(this).listFiles()?.filter { it.extension.equals("gguf", true) }?.sortedBy { it.name } ?: emptyList()
        if (local.isEmpty()) group(label("No installed GGUF model yet", 13f, muted))
        else {
            modelList = Spinner(this)
            modelList!!.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, local.map { "${it.name} • ${"%.2f".format(it.length()/1e9)} GB" })
            group(modelList!!)
            group(button("Load selected model", {
                val file = local.getOrNull(modelList!!.selectedItemPosition) ?: return@button
                loadModel(file)
            }, true))
            group(button("Unload active model", { unloadModel() }))
        }
        group(button("Import GGUF from Rounder / files", {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            startActivityForResult(intent, 44)
        }))
        group(label("Inference backend: reused Asterion Art native llama.cpp GGUF engine. Its current configuration is CPU-based; Vulkan/QNN for this model is not yet device-verified.", 12f, muted))
    }
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 44 || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        val name = android.provider.OpenableColumns.DISPLAY_NAME.let { key ->
            contentResolver.query(uri, arrayOf(key), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
        } ?: "imported.gguf"
        if (!name.endsWith(".gguf", true)) { info("Select a .gguf model file"); return }
        asyncRequest("Importing model", {
            val dest = File(ModelDownloadService.modelsDir(this), "import_" + name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val stage = File(dest.path+".part")
            contentResolver.openInputStream(uri)!!.use { input -> stage.outputStream().use { input.copyTo(it, 1024*1024) } }
            require(stage.inputStream().use { it.readNBytes(4) }.contentEquals(byteArrayOf(71,71,85,70))) { "Not a GGUF file" }
            require(stage.renameTo(dest)) { "Cannot commit imported model" }
            dest.name
        }) { s -> info("Imported $s"); build() }
    }
    private fun loadModel(file: File) {
        if (busy) { info("Another operation is running"); return }
        busy = true; info("Loading ${file.name} — memory intensive")
        scope.launch {
            try {
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mem = ActivityManager.MemoryInfo(); manager.getMemoryInfo(mem)
                // Avoid likely OOM from weight files larger than the currently available budget.
                require(mem.availMem > file.length() + 450_000_000L) { "Not enough available RAM (${mem.availMem/1e9} GB) to load this model safely" }
                withContext(Dispatchers.IO) {
                    engine?.let { existing ->
                        if (loaded != null) { existing.cleanUp(); loaded = null }
                    }
                    val candidate = AiChat.getInferenceEngine(this@MainActivity)
                    candidate.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error }
                    require(candidate.state.value is InferenceEngine.State.Initialized) { "Native engine failed to initialize" }
                    candidate.loadModel(file.absolutePath, 4096)
                    candidate.setSystemPrompt("You are Asterion Writing, a specialist author of original web and light novels. Write compelling, coherent fiction with distinct personalities, sustained causal progression, vivid but controlled description, and natural dialogue. Never copy supplied source fiction. Follow the user's narrative brief; maintain continuity. Output prose, not commentary.")
                    engine = candidate
                }
                loaded = file.name; info("Loaded ${file.name} · CPU native engine ready"); build()
            } catch (e: Throwable) { info("Model load failed: ${e.message ?: e.javaClass.simpleName}") }
            finally { busy = false }
        }
    }
    private fun unloadModel() {
        if (busy) { info("Wait for generation or loading to finish"); return }
        scope.launch { busy = true; try { withContext(Dispatchers.IO) { engine?.cleanUp() }; loaded = null; info("Model unloaded"); build() }
            catch(e: Throwable) { info("Unload error: ${e.message}") } finally { busy = false } }
    }
    private fun generate() {
        val active = engine
        if (loaded == null || active == null) { info("Load a GGUF model from Models first"); return }
        if (busy) { info("A generation is already running"); return }
        if (prompt.isBlank()) { info("Enter a story brief first"); return }
        busy = true; generated = ""; outputView?.text = "Generating…"
        val fullBrief = "Writing task:\n$prompt\n\nApproved canon / constraints:\n${canon.ifBlank { "None established." }}\n\nWrite genuinely original fictional prose, opening directly in-scene. Avoid excessive exposition, repeated expressions and imitation of existing characters or settings."
        generation = scope.launch {
            try {
                active.sendUserPrompt(fullBrief, tokenCap).collect { token ->
                    generated += token
                    outputView?.text = generated
                    prefs.edit().putString("generated", generated).apply()
                }
                if (generated.isNotBlank()) {
                    File(filesDir, "last-draft.txt").writeText(generated)
                    val findings = QualityGate().check(generated)
                    info("Draft saved locally · ${generated.split(Regex("\\s+")).size} words · ${findings.filter { it.severity.name == "BLOCK" }.size} structural flags · editorial review required")
                } else info("Inference returned no text; inspect model compatibility")
            } catch(e: CancellationException) { info("Generation interrupted; partial draft retained") }
            catch(e: Throwable) { info("Generation failed: ${e.message ?: e.javaClass.simpleName}") }
            finally { busy = false; prefs.edit().putString("generated", generated).apply() }
        }
    }
    private fun shareDraft() {
        if (generated.isBlank()) { info("Generate a draft first"); return }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Asterion Writing draft").putExtra(Intent.EXTRA_TEXT, generated)
        startActivity(Intent.createChooser(i, "Send draft to Nexus or another app"))
    }
    private fun <T> asyncRequest(status: String, work: () -> T, success: (T) -> Unit) {
        if (busy) { info("Another operation is running"); return }
        busy = true; info(status)
        scope.launch {
            try { val answer = withContext(Dispatchers.IO) { work() }; success(answer) }
            catch(e: Exception) { info("${e.message ?: "Request failed"}") }
            finally { busy = false }
        }
    }
    private fun info(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        statusView?.text = message
    }
    private fun watcher(onText: (String) -> Unit) = object: android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s.toString()) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
    override fun onDestroy() { generation?.cancel(); scope.cancel(); super.onDestroy() }
}
