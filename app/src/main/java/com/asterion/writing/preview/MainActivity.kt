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
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.asterion.writing.QualityGate
import com.asterion.writing.NarrativeController
import com.asterion.writing.SceneObjective
import com.asterion.writing.ChapterContext
import com.asterion.writing.AuthoringProfile
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
    private var activeAdapter: String? = null
    private var engine: InferenceEngine? = null
    private var prompt = "Invent an original fantasy web novel with complex characters, layered consequences, mature themes, and a distinctive world. Begin with a consequential scene."
    private var canon = ""
    private var generated = ""
    private var tokenCap = 768
    private var contextCap = 4096
    private var segments = 4
    private var projectTitle = ""
    private var chapterTitle = ""
    private var seriesTitle = ""
    private var volumeTitle = ""
    private var chapterPlan = ""
    private var authorProfile = ""
    private var pointOfView = ""
    private var editorGuidance = ""
    private var creationMode = "Chapter prose"
    private var cast = ""
    private var sceneObjective = ""
    private var downloadProgressView: ProgressBar? = null
    private var resumeDownloadButton: Button? = null
    private var showSampler = false
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
        generated = WritingTestBench.readCheckpoint(this).ifBlank { prefs.getString("generated", "") ?: "" }
        projectTitle = prefs.getString("project_title", "") ?: ""
        chapterTitle = prefs.getString("chapter_title", "") ?: ""
        seriesTitle = prefs.getString("series_title", "") ?: ""
        volumeTitle = prefs.getString("volume_title", "") ?: ""
        chapterPlan = prefs.getString("chapter_plan", "") ?: ""
        authorProfile = prefs.getString("author_profile", "") ?: ""
        pointOfView = prefs.getString("point_of_view", "") ?: ""
        editorGuidance = prefs.getString("editor_guidance", "") ?: ""
        creationMode = prefs.getString("creative_mode", "Chapter prose") ?: "Chapter prose"
        cast = prefs.getString("cast", "") ?: ""
        sceneObjective = prefs.getString("scene_objective", "") ?: ""
        tokenCap = prefs.getInt("token_cap", 1280)
        contextCap = prefs.getInt("context_cap", 4096)
        segments = prefs.getInt("segments", 4)
        val current = prefs.getString("active_workspace_id", null)?.let {
            runCatching { WriterWorkspaceStore(this).get(it) }.getOrNull()
        }
        if (current != null) restoreWorkspace(current)
        build()
        handler.post(object: Runnable {
            override fun run() {
                if (!isFinishing) {
                    if (tab == 2) {
                        statusView?.text = prefs.getString(ModelDownloadService.KEY_STATUS, "No active download")
                        downloadProgressView?.progress = prefs.getInt(ModelDownloadService.KEY_PROGRESS, 0)
                        resumeDownloadButton?.isEnabled = ModelDownloadService.resumable(this@MainActivity) &&
                            !ModelDownloadService.isRunning()
                    }
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
        b.setTextColor(if (primary) bg else white)
        b.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (primary) mint else panel)
            cornerRadius = dp(14).toFloat()
            if (!primary) setStroke(dp(1), Color.rgb(51, 67, 84))
        }
        b.minHeight = dp(46); b.setPadding(dp(12), dp(9), dp(12), dp(9))
        b.setOnClickListener { action() }; return b
    }
    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white); setBackgroundColor(panel)
                    setPadding(dp(12), dp(13), dp(12), dp(13))
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white); setBackgroundColor(panel)
                    setPadding(dp(12), dp(15), dp(12), dp(15))
                }
        }
    private fun group(v: View, margins: Int = 9) {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, dp(margins), 0, dp(margins)); content.addView(v, p)
    }
    private fun build() {
        val outer = LinearLayout(this); outer.orientation = LinearLayout.VERTICAL; outer.setBackgroundColor(bg)
        val header = LinearLayout(this); header.orientation = LinearLayout.VERTICAL; header.setPadding(dp(20), dp(12), dp(20), dp(4))
        header.addView(label("ASTERION  /  WRITER", 23f, mint, true))
        header.addView(label("LOCAL FICTION STUDIO  ·  REAL MODEL INFERENCE", 11f, muted))
        outer.addView(header)
        val nav = LinearLayout(this); nav.setPadding(dp(12), 0, dp(12), 0)
        val w = button("STUDIO", { tab = 0; build() }, tab == 0)
        val l = button("LIBRARY", { tab = 1; build() }, tab == 1)
        val m = button("MODELS", { tab = 2; build() }, tab == 2)
        val r = button("REVIEW", { tab = 3; build() }, tab == 3)
        nav.addView(w, LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(l, LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(m, LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(r, LinearLayout.LayoutParams(0, dp(52), 1f))
        outer.addView(nav)
        val scroll = ScrollView(this); scroll.isFillViewport = true
        content = LinearLayout(this); content.orientation = LinearLayout.VERTICAL; content.setPadding(dp(20), dp(8), dp(20), dp(36))
        scroll.addView(content); outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
        when(tab) { 0 -> writerPage(); 1 -> workspacePage(); 2 -> modelPage(); else -> resultsPage() }
    }
    private fun writerPage() {
        group(label("CHAPTER STUDIO", 18f, white, true))
        group(label("Write a real chapter with scene continuity and recoverable drafts. Tests remain private; Nexus integration is not yet connected.", 13f, muted))
        group(label("STORY PROJECT", 14f, mint, true))
        val projectInput = field(projectTitle, 1, "Universe / series title")
        projectInput.addTextChangedListener(watcher { projectTitle = it; prefs.edit().putString("project_title", it).apply() })
        group(projectInput)
        val seriesInput = field(seriesTitle, 1, "Series")
        seriesInput.addTextChangedListener(watcher { seriesTitle = it; prefs.edit().putString("series_title", it).apply() })
        group(seriesInput)
        val volumeInput = field(volumeTitle, 1, "Volume")
        volumeInput.addTextChangedListener(watcher { volumeTitle = it; prefs.edit().putString("volume_title", it).apply() })
        group(volumeInput)
        val chapterInput = field(chapterTitle, 1, "Chapter")
        chapterInput.addTextChangedListener(watcher { chapterTitle = it; prefs.edit().putString("chapter_title", it).apply() })
        group(chapterInput)
        group(label("CHARACTERS & RELATIONSHIPS", 14f, mint, true))
        val castInput = field(cast, 3, "Characters, motivations, secrets, relationships")
        castInput.addTextChangedListener(watcher { cast = it; prefs.edit().putString("cast", it).apply() })
        group(castInput)
        group(label("CHAPTER ARC / PLAN", 14f, mint, true))
        val outlineInput = field(chapterPlan, 3, "How the chapter should develop, major beats and consequences")
        outlineInput.addTextChangedListener(watcher { chapterPlan = it; prefs.edit().putString("chapter_plan", it).apply() })
        group(outlineInput)
        group(label("CURRENT SCENE OBJECTIVE", 14f, mint, true))
        val objectiveInput = field(sceneObjective, 3, "What changes in this scene? Decision, conflict, consequence")
        objectiveInput.addTextChangedListener(watcher { sceneObjective = it; prefs.edit().putString("scene_objective", it).apply() })
        group(objectiveInput)
        val model = loaded ?: "No model loaded"
        group(label("Active model: $model" +
            (activeAdapter?.let { " · LoRA: $it" } ?: ""), 13f, if (loaded == null) muted else mint))
        group(button("Manage or load models", { tab = 2; build() }))
        group(button("Use standardized A/B story test", { prompt = WritingTestBench.STANDARD_BRIEF; prefs.edit().putString("prompt", prompt).apply(); build() }))
        group(label("PREMISE & WRITING DIRECTION", 14f, mint, true))
        val promptInput = field(prompt, 5)
        promptInput.addTextChangedListener(watcher { prompt = it; prefs.edit().putString("prompt", it).apply() }); group(promptInput)
        group(label("ESTABLISHED CANON / CONTINUITY", 14f, mint, true))
        val canonInput = field(canon, 3, "World rules, established characters, what must stay consistent")
        canonInput.addTextChangedListener(watcher { canon = it; prefs.edit().putString("canon", it).apply() }); group(canonInput)
        group(label("AUTHORING PROFILE", 14f, mint, true))
        val profileInput = field(authorProfile, 1, "Narrative style, language, prose density")
        profileInput.addTextChangedListener(watcher { authorProfile = it; prefs.edit().putString("author_profile", it).apply() })
        group(profileInput)
        val povInput = field(pointOfView, 1, "Viewpoint / narration")
        povInput.addTextChangedListener(watcher { pointOfView = it; prefs.edit().putString("point_of_view", it).apply() })
        group(povInput)
        group(button("Save project and draft", { saveCurrentWorkspace() }, true))
        group(button(if (showSampler) "Hide sampler parameters" else "Edit native sampler parameters", {
            showSampler = !showSampler; build()
        }))
        if (showSampler) {
            listOf(
                Triple("Temperature", "temperature", "0.80"),
                Triple("Top-p", "top_p", "0.90"),
                Triple("Min-p", "min_p", "0.05"),
                Triple("Repeat penalty", "repetition", "1.12"),
                Triple("Seed (-1 random)", "seed", "42")
            ).forEach { (labelText, key, fallback) ->
                group(label(labelText, 12f, muted))
                val input = field(prefs.getString(key, fallback) ?: fallback)
                input.addTextChangedListener(watcher { prefs.edit().putString(key, it).apply() })
                group(input, 1)
            }
        }
        group(label("Context window (applies on next model load)", 13f, muted))
        val windows = listOf(4096, 8192)
        val contextChoice = Spinner(this)
        contextChoice.adapter = spinnerAdapter(windows.map { it.toString() + " tokens" })
        contextChoice.setSelection(windows.indexOf(contextCap).coerceAtLeast(0))
        contextChoice.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                contextCap = windows[pos]; prefs.edit().putInt("context_cap", contextCap).apply()
            }
        }
        group(contextChoice)
        group(label("Output tokens per scene segment", 13f, muted))
        val tok = Spinner(this)
        val choices = listOf(384, 768, 1280, 2048)
        tok.adapter = spinnerAdapter(choices.map { "$it tokens" })
        tok.setSelection(choices.indexOf(tokenCap).coerceAtLeast(0))
        tok.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { tokenCap = choices[pos]; prefs.edit().putInt("token_cap", tokenCap).apply() }
        }; group(tok)
        group(label("Segments per run (1 for A/B; 2–8 for chapter experiments)", 13f, muted))
        val segmentCounts = listOf(1, 2, 4, 8)
        val parts = Spinner(this)
        parts.adapter = spinnerAdapter(segmentCounts.map { it.toString() + " segment(s)" })
        parts.setSelection(segmentCounts.indexOf(segments).coerceAtLeast(0))
        parts.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                segments = segmentCounts[pos]; prefs.edit().putInt("segments", segments).apply()
            }
        }
        group(parts)
        group(label("CREATIVE MODE", 14f, mint, true))
        val creativeModes = listOf(
            "Chapter prose", "Worldbuilding notes", "Character arcs",
            "Volume outline", "Continuity critique"
        )
        val modeSpinner = Spinner(this)
        modeSpinner.adapter = spinnerAdapter(creativeModes)
        modeSpinner.setSelection(creativeModes.indexOf(creationMode).coerceAtLeast(0))
        modeSpinner.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                creationMode = creativeModes[pos]
                prefs.edit().putString("creative_mode", creationMode).apply()
            }
        }
        group(modeSpinner)
        group(label("The selected mode executes a real model task. Proposed world/character/plot changes require your approval.", 12f, muted))
        group(button("Generate with selected mode", {
            if (creationMode == "Chapter prose") generate(false) else generateCreativeMode()
        }, true))
        group(label("GENERATION CONTROLS", 14f, mint, true))
        group(button("Start new chapter · preserve old draft", { generate(false) }, true))
        group(button("Continue chapter from saved text", { generate(true) }))
        group(button("Stop after current token", { generation?.cancel(); info("Stopping; draft checkpoint will be preserved.") }))
        group(button("Edit or revise chapter", { editChapter() }))
        group(button("Rewrite an excerpt with the loaded model", { rewritePassage() }))
        group(button("Review and approve draft", { tab = 3; build() }))
        group(label("LIVE CHAPTER DRAFT", 18f, white, true))
        group(label("Continuations append to the saved draft; they do not reproduce hidden model state or pretend to resume mid-token.", 12f, muted))
        outputView = label(generated.ifBlank { "Your generated prose will appear here. No synthetic sample text is displayed." }, 15f)
        outputView!!.setTextIsSelectable(true); group(outputView!!)
        group(button("Share draft / send to Nexus", { shareDraft() }))
        group(label("Review gate: generated text is never automatically approved or published. Nexus integration is pending the actual interface contract.", 12f, muted))
    }
    private fun modelPage() {
        group(label("HUGGING FACE / DIRECT INSTALL", 17f, white, true))
        group(label("Choose a verified public GGUF repository or enter any Hugging Face owner/model. Download, resume and verify directly in this app.", 13f, muted))
        val names = HuggingFaceCatalog.presets.keys.toList()
        val preset = Spinner(this); preset.adapter = spinnerAdapter(names)
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
            fileSpin.adapter = spinnerAdapter(files.map { it.label })
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
        downloadProgressView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = prefs.getInt(ModelDownloadService.KEY_PROGRESS, 0)
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
        }
        group(downloadProgressView!!)
        group(button("Pause active download", { ModelDownloadService.cancel(this); info("Pause requested; transfer position is retained.") }))
        resumeDownloadButton = button("Resume interrupted download", {
            if (ModelDownloadService.resume(this)) info("Resuming saved GGUF from its partial file")
            else info("No resumable GGUF file was found")
        }, true)
        resumeDownloadButton!!.isEnabled = ModelDownloadService.resumable(this) &&
            !ModelDownloadService.isRunning()
        group(resumeDownloadButton!!)
        group(label("The resume action uses the actual saved download, even after restarting this screen.", 12f, muted))
        group(label("LOCAL GGUF WRITER ADAPTERS", 17f, white, true))
        group(label("Optional specialist LoRA in compatible GGUF format. The base model and adapter must match; no silent fallback.", 12f, muted))
        val adaptersDir = File(filesDir, "writer-adapters").apply { mkdirs() }
        val adapters = adaptersDir.listFiles()?.filter { it.isFile && it.extension.equals("gguf", true) }
            ?.sortedBy { it.name } ?: emptyList()
        val adapterChoices = listOf("No adapter") + adapters.map { it.name }
        val adapterSpinner = Spinner(this)
        adapterSpinner.adapter = spinnerAdapter(adapterChoices)
        val preferredAdapter = prefs.getString("selected_adapter", "") ?: ""
        adapterSpinner.setSelection(adapterChoices.indexOf(preferredAdapter).coerceAtLeast(0))
        adapterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val choice = if (pos == 0) "" else adapterChoices[pos]
                prefs.edit().putString("selected_adapter", choice).apply()
            }
        }
        group(adapterSpinner)
        group(button("Import a local GGUF LoRA adapter", {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            startActivityForResult(intent, 46)
        }))
        group(label("Choose an adapter before loading its matching base model; activating a different adapter requires reloading the base.", 12f, muted))
        group(label("LOCAL MODELS", 17f, white, true))
        val local = ModelDownloadService.modelsDir(this).listFiles()?.filter { it.extension.equals("gguf", true) }?.sortedBy { it.name } ?: emptyList()
        if (local.isEmpty()) group(label("No installed GGUF model yet", 13f, muted))
        else {
            modelList = Spinner(this)
            modelList!!.adapter = spinnerAdapter(local.map { "${it.name} • ${"%.2f".format(it.length()/1e9)} GB" })
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
        if (requestCode == 45 && resultCode == RESULT_OK && data?.data != null) {
            try {
                val target = data.data!!
                contentResolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write("# " + chapterTitle.ifBlank { "Untitled chapter" } + "\n\n")
                    it.write(generated)
                } ?: error("The selected document provider did not open")
                info("Manuscript exported to the selected Android document provider")
            } catch (e: Exception) { info("Export failed: ${e.message}") }
            return
        }
        if (requestCode == 46 && resultCode == RESULT_OK && data?.data != null) {
            importAdapter(data.data!!)
            return
        }
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
    private fun importAdapter(uri: android.net.Uri) {
        asyncRequest("Importing and checking specialist adapter", {
            val root = File(filesDir, "writer-adapters").apply { mkdirs() }
            val display = contentResolver.query(uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "writer-adapter.gguf"
            require(display.endsWith(".gguf", ignoreCase = true)) { "The adapter must be GGUF" }
            val filename = display.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(root, filename)
            require(!target.exists()) { "Adapter already imported" }
            val partial = File(root, filename + ".part")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    partial.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
                } ?: error("Cannot open selected adapter")
                require(partial.length() > 4096L && partial.inputStream().use {
                    it.readNBytes(4).contentEquals(byteArrayOf(71, 71, 85, 70))
                }) { "Adapter is not a valid GGUF file" }
                require(partial.renameTo(target)) { "Cannot finalize adapter" }
                filename
            } finally { if (partial.exists()) partial.delete() }
        }) { name ->
            prefs.edit().putString("selected_adapter", name).apply()
            info("Adapter imported: $name. Reload compatible base to activate.")
            build()
        }
    }

    private fun loadModel(file: File) {
        if (busy) { info("Another operation is running"); return }
        busy = true; info("Loading ${file.name} — memory intensive")
        val adapterName = prefs.getString("selected_adapter", "") ?: ""
        val adapterPath = if (adapterName.isBlank()) null else
            File(File(filesDir, "writer-adapters"), adapterName)
        scope.launch {
            try {
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mem = ActivityManager.MemoryInfo(); manager.getMemoryInfo(mem)
                // Avoid likely OOM from weight files larger than the currently available budget.
                require(mem.availMem > file.length() + (adapterPath?.length() ?: 0L) + 500_000_000L) { "Not enough available RAM (${mem.availMem/1e9} GB) to load this model safely" }
                withContext(Dispatchers.IO) {
                    engine?.let { existing ->
                        if (loaded != null) { existing.cleanUp(); loaded = null }
                    }
                    val candidate = AiChat.getInferenceEngine(this@MainActivity)
                    candidate.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error }
                    require(candidate.state.value is InferenceEngine.State.Initialized) { "Native engine failed to initialize" }
                    try {
                        candidate.loadModel(file.absolutePath, contextCap)
                        if (adapterPath != null) {
                            require(adapterPath.isFile) { "Selected GGUF LoRA adapter is unavailable" }
                            candidate.loadAdapter(adapterPath.absolutePath)
                        }
                        candidate.setSystemPrompt(WritingTestBench.SYSTEM_PROMPT)
                        engine = candidate
                    } catch (failure: Throwable) {
                        runCatching { candidate.cleanUp() }
                        engine = null
                        loaded = null
                        activeAdapter = null
                        throw failure
                    }
                }
                loaded = file.name
                activeAdapter = adapterName.takeIf { it.isNotBlank() }
                info("Loaded ${file.name}" + (activeAdapter?.let { " with LoRA $it" } ?: "") +
                    " · CPU native engine ready")
                build()
            } catch (e: Throwable) { info("Model load failed: ${e.message ?: e.javaClass.simpleName}") }
            finally { busy = false }
        }
    }
    private fun unloadModel() {
        if (busy) { info("Wait for generation or loading to finish"); return }
        scope.launch { busy = true; try { withContext(Dispatchers.IO) { engine?.cleanUp() }; loaded = null; info("Model unloaded"); build() }
            catch(e: Throwable) { info("Unload error: ${e.message}") }
            finally { activeAdapter = null; busy = false } }
    }
    private fun generate(continueExisting: Boolean) {
        val active = engine
        if (loaded == null || active == null) { info("Load a GGUF model from Models first"); return }
        if (busy) { info("A generation is already running"); return }
        if (prompt.isBlank()) { info("Enter a story brief first"); return }
        val sampling = try { WritingSampling.from(prefs) }
            catch(e: Exception) { info("Sampler settings invalid: " + e.message); return }
        val trialModel = loaded!! + (activeAdapter?.let { "::LoRA::$it" } ?: "")
        val brief = prompt
        val constraints = canon
        val project = projectTitle
        val series = seriesTitle
        val volume = volumeTitle
        val chapter = chapterTitle
        val plan = chapterPlan
        val profile = authorProfile
        val viewpoint = pointOfView
        val workspaceId = prefs.getString("active_workspace_id", null)
        val acceptedHistory = workspaceId?.let {
            runCatching { WriterWorkspaceStore(this).acceptedHistory(it) }.getOrElse { error ->
                info("Accepted chapter history unavailable: ${error.message}"); return
            }
        } ?: emptyList()
        val characters = cast
        val objective = sceneObjective
        val existingDraft = if (continueExisting) generated else ""
        if (workspaceId != null && WriterWorkspaceStore(this).get(workspaceId)?.approved == true) {
            info("This chapter is accepted and locked. Fork it from Library to edit."); return
        }
        if (continueExisting && existingDraft.isBlank()) {
            info("No saved chapter to continue"); return
        }
        if (!continueExisting && generated.isNotBlank()) WritingTestBench.archiveDraft(this, generated)
        val passCount = segments
        val cap = tokenCap
        val ctx = contextCap
        busy = true
        generated = existingDraft
        outputView?.text = if (continueExisting) "Continuing saved chapter…" else "Running real native inference…"
        val started = SystemClock.elapsedRealtime()
        val startMetrics = WritingTestBench.deviceMetrics(this)
        generation = scope.launch {
            val text = StringBuilder(existingDraft)
            if (existingDraft.isNotBlank()) text.append("\n\n")
            var firstTextMs = -1L
            var chunks = 0
            var lastUi = 0L
            var lastCheckpoint = 0L
            var finishedSegments = 0
            var status = "complete"
            var problem: String? = null
            try {
                repeat(passCount) { section ->
                    // The narrative director builds explicit scene goals; its metadata is not
                    // automatically promoted to canon until the owner accepts it.
                    val director = NarrativeController()
                    val goal = SceneObjective("scene-${section + 1}", workspaceId ?: "local-chapter",
                        "Scene ${section + 1}", objective.ifBlank { "Develop conflict and its consequences" },
                        characters.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                        viewpoint, chronology = section.toLong())
                    val state = ChapterContext(
                        project.ifBlank { "Untitled universe" }, series, volume,
                        chapter.ifBlank { "Current chapter" }, brief,
                        constraints, characters, plan, acceptedHistory,
                        AuthoringProfile(name = "Writer profile", style = profile, viewpoint = viewpoint))
                    val instruction = director.makeInstruction(state, goal, text.toString(), text.isNotBlank())
                    withContext(Dispatchers.IO) {
                        active.configureSampler(sampling.temperature, sampling.topP, sampling.minP,
                            sampling.repetition, sampling.seed)
                        active.resetSession(WritingTestBench.SYSTEM_PROMPT)
                    }
                    var emittedInSegment = false
                    active.sendUserPrompt(instruction, cap).collect { piece ->
                        if(piece.isNotEmpty()) {
                            emittedInSegment = true
                            val now = SystemClock.elapsedRealtime()
                            if(firstTextMs < 0L) firstTextMs = now - started
                            text.append(piece); chunks++
                            if(now - lastUi >= 180) {
                                generated = text.toString()
                                outputView?.text = generated
                                lastUi = now
                            }
                            if(now - lastCheckpoint >= 2000) {
                                generated = text.toString()
                                prefs.edit().putString("generated", generated).apply()
                                withContext(Dispatchers.IO) {
                                    WritingTestBench.checkpoint(this@MainActivity, generated)
                                    if (workspaceId != null) WriterWorkspaceStore(this@MainActivity).autosave(workspaceId, generated)
                                }
                                lastCheckpoint = now
                            }
                        }
                    }
                    if (!emittedInSegment) error("Native engine returned no text for scene ${section + 1}")
                    finishedSegments++
                    generated = text.toString()
                    withContext(Dispatchers.IO) {
                        WritingTestBench.checkpoint(this@MainActivity, generated)
                        if (workspaceId != null) WriterWorkspaceStore(this@MainActivity).autosave(workspaceId, generated)
                    }
                    if(section + 1 < passCount && text.isNotEmpty()) text.append("\n\n")
                    if(text.isEmpty()) error("Native engine returned no text")
                }
            } catch(e: CancellationException) {
                status = "stopped"
            } catch(e: Throwable) {
                status = "error"
                problem = e.message ?: e.javaClass.simpleName
            } finally {
                generated = text.toString()
                val elapsed = SystemClock.elapsedRealtime() - started
                val flags = if(generated.isBlank()) 1 else QualityGate().check(generated)
                    .count { it.severity.name == "BLOCK" }
                withContext(NonCancellable + Dispatchers.IO) {
                    WritingTestBench.checkpoint(this@MainActivity, generated)
                    if (workspaceId != null) WriterWorkspaceStore(this@MainActivity).autosave(workspaceId, generated)
                    WritingTestBench.saveRun(this@MainActivity, trialModel, brief, generated, sampling,
                        ctx, cap, finishedSegments, elapsed, firstTextMs, chunks, status, flags,
                        startMetrics)
                }
                outputView?.text = generated
                prefs.edit().putString("generated", generated).apply()
                busy = false
                info(if(status == "complete") "Run saved: " + generated.trim().split(Regex("\\s+")).size +
                    " words / " + (elapsed / 1000) + "s, " + flags + " structural flags"
                    else "Run " + status + "; draft preserved. " + (problem ?: ""))
            }
        }
    }

    private fun restoreWorkspace(w: WriterWorkspace) {
        projectTitle = w.universe
        seriesTitle = w.series
        volumeTitle = w.volume
        chapterTitle = w.chapter
        prompt = w.premise
        canon = w.canon
        cast = w.cast
        sceneObjective = w.sceneObjective
        chapterPlan = w.chapterPlan
        authorProfile = w.profile
        generated = WriterWorkspaceStore(this).draft(w.id)
        prefs.edit().putString("active_workspace_id", w.id)
            .putString("project_title", projectTitle)
            .putString("series_title", seriesTitle)
            .putString("volume_title", volumeTitle)
            .putString("chapter_title", chapterTitle)
            .putString("prompt", prompt)
            .putString("canon", canon)
            .putString("cast", cast)
            .putString("scene_objective", sceneObjective)
            .putString("chapter_plan", chapterPlan)
            .putString("author_profile", authorProfile)
            .putString("generated", generated).apply()
    }

    private fun saveCurrentWorkspace() {
        if (busy) { info("Finish the current operation before changing the project"); return }
        try {
            val store = WriterWorkspaceStore(this)
            val id = prefs.getString("active_workspace_id", null)
            val original = id?.let { store.get(it) }
            if (original?.approved == true) {
                info("Approved chapter is locked. Create a revision from Library."); return
            }
            val w = if (original == null) store.create(
                projectTitle, seriesTitle, volumeTitle, chapterTitle, prompt, cast, canon,
                sceneObjective, chapterPlan, authorProfile
            ) else store.update(original.copy(
                universe = projectTitle, series = seriesTitle, volume = volumeTitle,
                chapter = chapterTitle, premise = prompt, cast = cast, canon = canon,
                sceneObjective = sceneObjective, chapterPlan = chapterPlan, profile = authorProfile
            ))
            store.saveRevision(w.id, generated)
            prefs.edit().putString("active_workspace_id", w.id).apply()
            info("Project saved locally with revision history")
        } catch (e: Exception) { info("Save failed: ${e.message}") }
    }

    private fun workspacePage() {
        group(label("YOUR WRITING PROJECTS", 19f, white, true))
        group(label("Multiple independent projects, chapters and revisions. Manuscripts stay private; explicit Nexus publication is not connected.", 13f, muted))
        group(button("New original project", {
            if (busy) { info("Stop generation before switching projects"); return@button }
            if (prefs.getString("active_workspace_id", null) == null && generated.isNotBlank())
                WritingTestBench.archiveDraft(this, generated)
            prefs.edit().remove("active_workspace_id").putString("generated", "").apply()
            WritingTestBench.checkpoint(this, "")
            projectTitle = ""; seriesTitle = ""; volumeTitle = ""; chapterTitle = ""
            cast = ""; canon = ""; sceneObjective = ""; chapterPlan = ""
            prompt = ""; generated = ""; tab = 0; build()
        }, true))
        group(button("Save current project and draft", { saveCurrentWorkspace() }))
        val store = WriterWorkspaceStore(this)
        val projects = runCatching { store.list() }.getOrElse {
            group(label("Workspace index could not be read: ${it.message}. Files were not erased.", 13f, muted))
            emptyList()
        }
        if (projects.isEmpty()) group(label("No saved projects yet. Set your universe and chapter in Studio, then save.", 13f, muted))
        projects.forEach { w ->
            val active = prefs.getString("active_workspace_id", null) == w.id
            group(label((if (active) "● " else "") + w.universe + "  /  " + w.series, 15f, mint, true))
            group(label(w.volume + " · " + w.chapter + " · " +
                if (w.approved) "Approved (locked)" else "Draft", 12f, muted))
            group(button("Open this chapter", {
                if (busy) { info("Stop generation before switching projects"); return@button }
                restoreWorkspace(w); tab = 0; build()
            }, active))
            group(button("Fork to a new chapter revision", {
                if (busy) { info("Stop generation before forking"); return@button }
                try { restoreWorkspace(store.fork(w.id)); tab = 0; build()
                    info("New editable revision created; original preserved") }
                catch(e: Exception) { info("Fork failed: ${e.message}") }
            }))
            if (w.approved) {
                group(label("Accepted consequence: " + w.acceptedOutcome, 12f, muted))
                group(button("Create next chapter in this series", {
                    if (busy) { info("Stop generation before changing chapters"); return@button }
                    try {
                        restoreWorkspace(store.nextChapter(w.id)); tab = 0; build()
                        info("Next chapter created with accepted continuity")
                    } catch (e: Exception) { info("Next chapter unavailable: ${e.message}") }
                }))
            }
            if (w.id == prefs.getString("active_workspace_id", null)) {
                group(label("Stored revisions: " + store.revisions(w.id).size, 12f, muted))
            }
            group(label("────────────────────────", 12f, muted))
        }
    }

    private fun editChapter() {
        if (busy) { info("Stop generation before editing prose"); return }
        val id = prefs.getString("active_workspace_id", null)
        if (id != null && WriterWorkspaceStore(this).get(id)?.approved == true) {
            info("Approved chapter is locked. Fork it from Library."); return
        }
        val edit = field(generated, 12, "Your actual chapter prose")
        edit.minLines = 12
        edit.maxLines = 26
        val container = ScrollView(this).apply {
            setPadding(dp(16), dp(6), dp(16), dp(6)); addView(edit)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Edit chapter prose")
            .setView(container)
            .setPositiveButton("Save revision") { _, _ ->
                try {
                    val next = edit.text.toString()
                    if (id != null) WriterWorkspaceStore(this).saveRevision(id, next)
                    generated = next
                    WritingTestBench.checkpoint(this, generated)
                    prefs.edit().putString("generated", generated).apply()
                    outputView?.text = generated
                    info("Draft saved; previous revision preserved")
                } catch (e: Exception) { info("Edit was not saved: ${e.message}") }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun generateCreativeMode() {
        val active = engine
        if (loaded == null || active == null) { info("Load a compatible local GGUF model"); return }
        if (busy) { info("An operation is already running"); return }
        val mode = creationMode
        if (mode == "Chapter prose") { generate(false); return }
        if (prompt.isBlank()) { info("Define your original universe premise first"); return }
        val sampling = try { WritingSampling.from(prefs) }
        catch(e: Exception) { info("Sampler settings invalid: ${e.message}"); return }
        val task = when (mode) {
            "Worldbuilding notes" -> "Develop original world mechanics, social consequences, history, cultures, limitations and new conflicts. Propose notes; do not write chapter prose."
            "Character arcs" -> "Develop distinct original character motivations, secrets, contradictions, relationship dynamics and believable changes. Propose notes, not chapter prose."
            "Volume outline" -> "Develop a multi-chapter volume arc with cause/effect, varied pacing, turning points and consequences. Avoid generic filler and plagiarism."
            "Continuity critique" -> "Critically review established canon, recent manuscript, character knowledge, chronology, decision causes and inconsistencies. Flag uncertainty instead of inventing facts."
            else -> error("Unknown creative mode")
        }
        val request = "CREATIVE MODE: " + mode + "\n" + task +
            "\nUniverse: " + projectTitle + "\nSeries: " + seriesTitle +
            "\nVolume: " + volumeTitle + "\nCurrent chapter: " + chapterTitle +
            "\nPremise: " + prompt +
            "\nExisting canon: " + canon.takeLast(5000) +
            "\nExisting cast: " + cast.takeLast(3500) +
            "\nCurrent outline: " + chapterPlan.takeLast(2500) +
            "\nCurrent manuscript excerpt: " + generated.takeLast(2500) +
            "\nDo not assert new material is established until reviewed by the author."
        busy = true
        generation = scope.launch {
            val note = StringBuilder()
            var problem: String? = null
            try {
                withContext(Dispatchers.IO) {
                    active.configureSampler(sampling.temperature, sampling.topP, sampling.minP,
                        sampling.repetition, sampling.seed)
                    active.resetSession(WritingTestBench.SYSTEM_PROMPT)
                }
                active.sendUserPrompt(request, minOf(tokenCap, 1280)).collect { piece -> note.append(piece) }
            } catch (e: CancellationException) { problem = "Cancelled" }
              catch (e: Throwable) { problem = e.message ?: e.javaClass.simpleName }
              finally { busy = false }
            if (problem != null) { info("$mode: $problem"); return@launch }
            val proposed = note.toString().trim()
            if (proposed.isBlank()) { info("Model returned no content"); return@launch }
            val view = label(proposed, 14f).apply { setTextIsSelectable(true) }
            val acceptTitle = if (mode == "Continuity critique") "Close critique" else "Accept into project"
            val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("$mode — review")
                .setView(ScrollView(this@MainActivity).apply {
                    setPadding(dp(18), dp(8), dp(18), dp(8)); addView(view)
                })
                .setNegativeButton("Discard", null)
            dialog.setPositiveButton(acceptTitle) { _, _ ->
                if (mode == "Continuity critique") return@setPositiveButton
                when (mode) {
                    "Worldbuilding notes" -> canon += "\n" + proposed
                    "Character arcs" -> cast += "\n" + proposed
                    "Volume outline" -> chapterPlan += "\n" + proposed
                }
                prefs.edit().putString("canon", canon).putString("cast", cast)
                    .putString("chapter_plan", chapterPlan).apply()
                // Saving never approves or publishes: even model-generated notes stay provisional.
                saveCurrentWorkspace()
                tab = 0; build()
            }.show()
        }
    }

    private fun rewritePassage() {
        if (busy) { info("Wait for the current generation to finish"); return }
        if (engine == null || loaded == null) { info("Load a local writing model first"); return }
        if (generated.isBlank()) { info("Generate or open a chapter first"); return }
        val id = prefs.getString("active_workspace_id", null)
        if (id != null && WriterWorkspaceStore(this).get(id)?.approved == true) {
            info("Accepted chapters cannot be edited; fork a revision"); return
        }
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        pane.addView(label("Paste an EXACT excerpt from the existing chapter", 13f, muted))
        val excerpt = field("", 5, "Existing paragraph to improve")
        pane.addView(excerpt)
        pane.addView(label("Revision goal", 13f, muted))
        val goal = field("Improve the prose and dialogue while preserving facts and consequences", 3)
        pane.addView(goal)
        android.app.AlertDialog.Builder(this)
            .setTitle("Targeted scene revision")
            .setView(ScrollView(this).apply { addView(pane) })
            .setPositiveButton("Generate alternative") { _, _ ->
                runRewrite(excerpt.text.toString(), goal.text.toString())
            }.setNegativeButton("Cancel", null).show()
    }

    private fun runRewrite(excerpt: String, instruction: String) {
        val active = engine ?: return
        val selection = excerpt.trim()
        if (selection.length < 30 || selection.length > 6000 || instruction.isBlank()) {
            info("Choose a passage of 30–6000 characters and provide a revision goal"); return
        }
        if (!generated.contains(selection)) {
            info("The selected passage must match the current draft exactly"); return
        }
        if (busy) return
        val sampling = try { WritingSampling.from(prefs) }
        catch(e: Exception) { info("Sampling settings invalid: ${e.message}"); return }
        busy = true
        generation = scope.launch {
            val replacement = StringBuilder()
            var failure: String? = null
            try {
                withContext(Dispatchers.IO) {
                    active.configureSampler(sampling.temperature, sampling.topP, sampling.minP,
                        sampling.repetition, sampling.seed)
                    active.resetSession(WritingTestBench.SYSTEM_PROMPT)
                }
                val context = "Chapter: " + chapterTitle +
                    "\nEstablished canon: " + canon +
                    "\nKnown cast: " + cast +
                    "\nSurrounding prose: " + generated.takeLast(1600)
                val task = "Revise ONLY the quoted excerpt, preserving story facts and chronology. " +
                    "Return replacement prose only; no explanation.\n" +
                    "Revision instruction: " + instruction +
                    "\n" + context + "\nExcerpt to rewrite:\n" + selection
                active.sendUserPrompt(task, minOf(tokenCap, 1536)).collect { piece ->
                    replacement.append(piece)
                }
            } catch (e: CancellationException) {
                failure = "Revision was cancelled"
            } catch (e: Throwable) {
                failure = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
            if (failure != null) { info(failure!!); return@launch }
            val revision = replacement.toString().trim()
            if (revision.isBlank()) { info("The model returned no revision"); return@launch }
            val resultView = label(revision, 15f).apply { setTextIsSelectable(true) }
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("Review the proposed revision")
                .setView(ScrollView(this@MainActivity).apply {
                    setPadding(dp(18), dp(6), dp(18), dp(6)); addView(resultView)
                })
                .setPositiveButton("Replace excerpt") { _, _ ->
                    try {
                        if (!generated.contains(selection)) error("The original excerpt changed; revision not applied")
                        val id = prefs.getString("active_workspace_id", null)
                        if (id != null && WriterWorkspaceStore(this@MainActivity).get(id)?.approved == true)
                            error("Accepted chapter is locked")
                        val next = generated.replaceFirst(selection, revision)
                        if (id != null) WriterWorkspaceStore(this@MainActivity).saveRevision(id, next)
                        generated = next
                        WritingTestBench.checkpoint(this@MainActivity, next)
                        prefs.edit().putString("generated", next).apply()
                        outputView?.text = next
                        info("Revised excerpt saved; previous manuscript retained as a revision")
                    } catch (e: Exception) { info("Revision not applied: ${e.message}") }
                }.setNegativeButton("Discard suggestion", null).show()
        }
    }

    private fun resultsPage() {
        group(label("EDITORIAL DESK", 18f, white, true))
        val workspace = prefs.getString("active_workspace_id", null)?.let {
            WriterWorkspaceStore(this).get(it)
        }
        val issues = NarrativeController().reviewChapter(generated).codes
        group(label("Chapter: " + chapterTitle.ifBlank { "Untitled" } +
            " · " + generated.trim().split(Regex("\\s+")).count { it.isNotEmpty() } + " words", 14f, mint, true))
        if (issues.isNotEmpty()) {
            group(label("Review findings: " + issues.joinToString(", "), 12f, muted))
        } else group(label("No deterministic structural issues detected; human literary review is still required.", 12f, mint))
        group(button("Edit manuscript", { editChapter() }))
        group(button("Share draft with compatible reader / Nexus", { shareDraft() }))
        group(button("Export chapter as Markdown through Android files", {
            if (generated.isBlank()) { info("No prose to export"); return@button }
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/markdown")
                .putExtra(Intent.EXTRA_TITLE,
                    (chapterTitle.ifBlank { "asterion-chapter" }.replace(Regex("[^A-Za-z0-9._-]"), "_")) + ".md")
            startActivityForResult(i, 45)
        }))
        group(button("Approve and lock this chapter locally", {
            try {
                val id = workspace?.id ?: error("Save the chapter to a project before approving")
                if (issues.any { it == "SHORT_DRAFT" || it == "REPEATED_PARAGRAPH" ||
                        it == "REPEATED_SCENE_START" }) error("Resolve blocking structural findings first")
                val outcomeInput = field("", 4,
                    "What actually changed? Decisions, knowledge, timeline and consequences")
                android.app.AlertDialog.Builder(this)
                    .setTitle("Accept this chapter's consequences")
                    .setMessage("Record the real outcome. It will govern subsequent chapters. This does not publish to Nexus.")
                    .setView(outcomeInput)
                    .setPositiveButton("Approve and lock") { _, _ ->
                        try {
                            WriterWorkspaceStore(this).approve(id, outcomeInput.text.toString())
                            info("Chapter accepted; subsequent chapters inherit its outcome")
                            build()
                        } catch (e: Exception) { info("Approval failed: ${e.message}") }
                    }.setNegativeButton("Cancel", null).show()
            } catch (e: Exception) { info("Cannot approve: ${e.message}") }
        }, true))
        group(label("MODEL COMPARISON RESULTS", 17f, white, true))
        group(label("Private results stored on-device. Characters/s is a measured display rate, not native tokens/s.", 12f, muted))
        group(button("Share results summary (without private story text)", {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, WritingTestBench.exportSummary(this))
            startActivity(Intent.createChooser(intent, "Share test summary"))
        }))
        val history = WritingTestBench.recent(this)
        if(history.isEmpty()) group(label("No recorded test runs yet", 13f, muted))
        history.forEach { result ->
            group(label(result.optString("model"), 13f, mint, true))
            group(label(result.optString("status") + " · " + result.optInt("words") + " words · " +
                (result.optLong("elapsed_ms") / 1000) + "s · " +
                result.optDouble("chars_per_sec").toInt() + " characters/s · " +
                result.optInt("segments") + " completed segments", 12f, white))
            group(label("First text: " + result.optLong("first_text_ms") + "ms · structural flags: " +
                result.optInt("structural_flags") + " · seed " +
                result.optJSONObject("settings")?.optInt("seed"), 12f, muted))
            val existing = WritingTestBench.rating(this, result.optString("id"))
            if (existing != null) {
                val scores = existing.optJSONObject("scores")
                val mean = WritingTestBench.QUALITY_AXES.map { scores?.optInt(it, 0) ?: 0 }.average()
                group(label("Human-rated prose quality: " + "%.2f".format(mean) + "/5", 12f, mint))
            }
            group(button(if (existing == null) "Rate the actual writing" else "Update quality assessment", {
                showQualityAssessment(result.optString("id"))
            }))
        }
    }

    private fun showQualityAssessment(runId: String) {
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(8))
        }
        val scores = linkedMapOf<String, Int>()
        val previous = WritingTestBench.rating(this, runId)?.optJSONObject("scores")
        WritingTestBench.QUALITY_AXES.forEach { axis ->
            val starting = previous?.optInt(axis, 3) ?: 3
            scores[axis] = starting
            val title = label("$axis: $starting / 5", 13f, white)
            pane.addView(title)
            val rating = SeekBar(this).apply {
                max = 4
                progress = starting - 1
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        scores[axis] = progress + 1
                        title.text = "$axis: ${progress + 1} / 5"
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }
            pane.addView(rating)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Rate the real generated chapter")
            .setView(ScrollView(this).apply { addView(pane) })
            .setPositiveButton("Save assessment") { _, _ ->
                try {
                    WritingTestBench.rateRun(this, runId, scores)
                    info("Model comparison score recorded"); build()
                } catch (e: Exception) { info("Rating not saved: ${e.message}") }
            }.setNegativeButton("Cancel", null).show()
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
