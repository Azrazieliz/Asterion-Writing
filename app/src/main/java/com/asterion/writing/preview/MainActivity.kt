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
        group(label("Active model: $model", 13f, if (loaded == null) muted else mint))
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
        group(label("GENERATION CONTROLS", 14f, mint, true))
        group(button("Start new chapter · preserve old draft", { generate(false) }, true))
        group(button("Continue chapter from saved text", { generate(true) }))
        group(button("Stop after current token", { generation?.cancel(); info("Stopping; draft checkpoint will be preserved.") }))
        group(button("Edit or revise chapter", { editChapter() }))
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
                    candidate.loadModel(file.absolutePath, contextCap)
                    candidate.setSystemPrompt(WritingTestBench.SYSTEM_PROMPT)
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
    private fun generate(continueExisting: Boolean) {
        val active = engine
        if (loaded == null || active == null) { info("Load a GGUF model from Models first"); return }
        if (busy) { info("A generation is already running"); return }
        if (prompt.isBlank()) { info("Enter a story brief first"); return }
        val sampling = try { WritingSampling.from(prefs) }
            catch(e: Exception) { info("Sampler settings invalid: " + e.message); return }
        val trialModel = loaded!!
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
                        constraints, characters, plan, emptyList(),
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
                        ctx, cap, finishedSegments, elapsed, firstTextMs, chunks, status, flags)
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

    private fun resultsPage() {
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
