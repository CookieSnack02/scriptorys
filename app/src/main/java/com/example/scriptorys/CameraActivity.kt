package com.example.scriptorys

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sqrt
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider

enum class PosicaoTexto { TOPO, CENTRO, BASE }

/**
 * Câmera ao vivo com o script rolando por cima, e gravação de vídeo.
 *
 * A imagem é gravada pelo CameraX e o som pelo [GravadorAudio], que usa o microfone escolhido
 * (ex.: Hollyland via USB). Ao parar, o [MontadorVideo] junta os dois num MP4 na galeria.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var previa: PreviewView
    private lateinit var rolagem: ScrollView
    private lateinit var texto: TextView
    private lateinit var barra: View
    private lateinit var semPermissao: View
    private lateinit var botaoPlay: MaterialButton
    private lateinit var rolador: RolagemAutomatica
    private lateinit var controle: ControleRemoto

    private var cameraFrontal = true
    private var margemTopo = 0
    private var posicao = PosicaoTexto.TOPO
    private var tamanhoSp = 24
    private var corTexto = Color.WHITE
    private var palavrasPorMinuto = 120

    // Gravação
    private lateinit var botaoGravar: ImageButton
    private lateinit var botaoTrocarCamera: View
    private lateinit var pilulaMicrofone: View
    private lateinit var nomeMicrofone: TextView
    private lateinit var medidorVolume: LinearProgressIndicator
    private lateinit var tempoGravacao: TextView
    private var videoCapture: VideoCapture<Recorder>? = null
    private var gravacao: Recording? = null
    private var gravadorAudio: GravadorAudio? = null
    private var gravando = false
    private var inicioVideoNanos = Long.MAX_VALUE
    private val pastaGravacao by lazy { File(cacheDir, "gravacao") }
    private lateinit var tituloScript: String

    /** Pasta (do app) onde os vídeos são salvos; null = solto em Filmes/Scriptorys. */
    private var pastaVideo: Long? = null

    /** null + automático = melhor microfone externo conectado, ou o do celular. */
    private var microfoneEscolhido: AudioDeviceInfo? = null
    private var microfoneAutomatico = true
    private val principal = Handler(Looper.getMainLooper())

    private val atualizarMedidor = object : Runnable {
        override fun run() {
            val audio = gravadorAudio ?: return
            medidorVolume.progress = (sqrt(audio.nivel) * 100).toInt()
            principal.postDelayed(this, 80)
        }
    }

    private val observadorMicrofones = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(adicionados: Array<out AudioDeviceInfo>) = atualizarNomeMicrofone()

        override fun onAudioDevicesRemoved(removidos: Array<out AudioDeviceInfo>) {
            val escolhido = microfoneEscolhido
            if (!microfoneAutomatico && escolhido != null && removidos.any { it.id == escolhido.id }) {
                microfoneAutomatico = true
                microfoneEscolhido = null
                Toast.makeText(this@CameraActivity, getString(R.string.microfone_desconectado, nomeDoMicrofoneAtual()), Toast.LENGTH_LONG).show()
            }
            atualizarNomeMicrofone()
        }
    }

    private val pedirPermissoesGravacao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        if (precisaPermissaoArmazenamento() && !permitido(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, R.string.permissao_armazenamento_negada, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val comAudio = resultado[Manifest.permission.RECORD_AUDIO] ?: permitido(Manifest.permission.RECORD_AUDIO)
        if (!comAudio) Toast.makeText(this, R.string.permissao_microfone_negada, Toast.LENGTH_LONG).show()
        iniciarGravacao(comAudio)
    }

    private val pedirMicrofoneParaTeste = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) abrirEscolhaMicrofone()
        else Toast.makeText(this, R.string.permissao_microfone_negada, Toast.LENGTH_LONG).show()
    }

    private val pedirPermissao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) iniciarCamera() else mostrarSemPermissao()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val script = ScriptRepository(this).buscar(intent.getLongExtra(MainActivity.EXTRA_SCRIPT_ID, -1L))
        if (script == null) {
            finish()
            return
        }

        previa = findViewById(R.id.previa)
        rolagem = findViewById(R.id.rolagemCamera)
        texto = findViewById(R.id.textoCamera)
        barra = findViewById(R.id.barraCamera)
        semPermissao = findViewById(R.id.semPermissao)
        botaoPlay = findViewById(R.id.botaoPlayCamera)

        botaoGravar = findViewById(R.id.botaoGravar)
        botaoTrocarCamera = findViewById(R.id.botaoTrocarCamera)
        pilulaMicrofone = findViewById(R.id.pilulaMicrofone)
        nomeMicrofone = findViewById(R.id.nomeMicrofone)
        medidorVolume = findViewById(R.id.medidorVolume)
        tempoGravacao = findViewById(R.id.tempoGravacao)
        tituloScript = script.titulo.ifBlank { getString(R.string.sem_titulo) }
        // Por padrão, os vídeos vão para a mesma pasta do script.
        pastaVideo = script.pastaId

        // Sobre a câmera o texto usa uma cor única, escolhida pelo usuário.
        texto.text = Html.paraTexto(script.html).toString()
        rolador = RolagemAutomatica(rolagem, texto) { atualizarPlay() }
        val indicador = findViewById<TextView>(R.id.indicador)
        // Com o foco já na tela, o Android não "gasta" a primeira seta do controle movendo o foco.
        findViewById<View>(R.id.raizCamera).requestFocus()
        controle = ControleRemoto(rolador, ::alternarPlay) { delta ->
            palavrasPorMinuto = (palavrasPorMinuto + delta * 10).coerceIn(0, 300)
            rolador.palavrasPorMinuto = palavrasPorMinuto
            salvarPreferencias()
            indicador.mostrarBrevemente(getString(R.string.velocidade, palavrasPorMinuto))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raizCamera)) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            barra.updatePadding(left = barras.left, right = barras.right, bottom = barras.bottom)
            margemTopo = barras.top
            aplicarPosicao()
            insets
        }

        val toque = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                alternarPlay()
                return true
            }
        })
        rolagem.setOnTouchListener { _, evento ->
            toque.onTouchEvent(evento)
            false
        }

        findViewById<Button>(R.id.botaoVoltarCamera).setOnClickListener { finish() }
        findViewById<Button>(R.id.botaoReiniciarCamera).setOnClickListener {
            rolador.reiniciar()
            atualizarPlay()
        }
        botaoPlay.setOnClickListener { alternarPlay() }
        botaoTrocarCamera.setOnClickListener {
            cameraFrontal = !cameraFrontal
            iniciarCamera()
        }
        botaoGravar.setOnClickListener { alternarGravacao() }
        pilulaMicrofone.setOnClickListener {
            if (gravando) return@setOnClickListener
            if (permitido(Manifest.permission.RECORD_AUDIO)) abrirEscolhaMicrofone()
            else pedirMicrofoneParaTeste.launch(Manifest.permission.RECORD_AUDIO)
        }
        getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(observadorMicrofones, principal)
        atualizarNomeMicrofone()
        findViewById<Button>(R.id.botaoConfigCamera).setOnClickListener { abrirConfiguracoes() }
        findViewById<Button>(R.id.botaoPermissao).setOnClickListener { solicitarPermissaoNovamente() }

        carregarPreferencias()
        if (savedInstanceState != null) {
            cameraFrontal = savedInstanceState.getBoolean(ESTADO_FRONTAL, true)
            val rolado = savedInstanceState.getInt(ESTADO_ROLAGEM)
            rolagem.post { rolagem.scrollTo(0, rolado) }
        }
        aplicarAparencia()

        if (temPermissao()) iniciarCamera() else pedirPermissao.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        // Volta das configurações do sistema com a permissão concedida.
        if (::previa.isInitialized && semPermissao.visibility == View.VISIBLE && temPermissao()) iniciarCamera()
    }

    override fun onPause() {
        super.onPause()
        if (::rolador.isInitialized) {
            rolador.parar()
            atualizarPlay()
        }
        // Sair da tela (ou apagar o celular) encerra e salva a gravação em andamento.
        if (gravando) pararGravacao()
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(observadorMicrofones)
        principal.removeCallbacks(atualizarMedidor)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (::controle.isInitialized && controle.tecla(event)) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        (::controle.isInitialized && controle.movimento(ev)) || super.dispatchGenericMotionEvent(ev)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ESTADO_FRONTAL, cameraFrontal)
        outState.putInt(ESTADO_ROLAGEM, rolagem.scrollY)
    }

    private fun temPermissao() = permitido(Manifest.permission.CAMERA)

    private fun permitido(permissao: String) =
        ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED

    /** Até o Android 9, salvar na galeria exige permissão de armazenamento. */
    private fun precisaPermissaoArmazenamento() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun mostrarSemPermissao() {
        semPermissao.visibility = View.VISIBLE
    }

    private fun solicitarPermissaoNovamente() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            pedirPermissao.launch(Manifest.permission.CAMERA)
        } else {
            // O usuário marcou "não perguntar novamente": só dá para liberar nas configurações do app.
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun iniciarCamera() {
        semPermissao.visibility = View.GONE
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            try {
                val provedor = futuro.get()
                var seletor = if (cameraFrontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!provedor.hasCamera(seletor)) {
                    cameraFrontal = !cameraFrontal
                    seletor = if (cameraFrontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previa.surfaceProvider)
                val gravador = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
                    .build()
                val captura = VideoCapture.withOutput(gravador)
                provedor.unbindAll()
                videoCapture = try {
                    provedor.bindToLifecycle(this, seletor, preview, captura)
                    captura
                } catch (e: IllegalArgumentException) {
                    // Câmera sem suporte a prévia + vídeo juntos: mantém só a prévia.
                    Log.w("CameraActivity", "Gravação de vídeo indisponível nesta câmera", e)
                    provedor.bindToLifecycle(this, seletor, preview)
                    null
                }
            } catch (e: Exception) {
                Log.e("CameraActivity", "Falha ao abrir a câmera", e)
                Toast.makeText(this, R.string.erro_camera, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun alternarPlay() {
        if (rolador.rodando) rolador.pausar() else rolador.iniciar()
        atualizarPlay()
    }

    private fun atualizarPlay() {
        val rodando = rolador.rodando
        botaoPlay.setIconResource(if (rodando) R.drawable.ic_pause else R.drawable.ic_play)
        botaoPlay.contentDescription = getString(if (rodando) R.string.pause else R.string.play)
    }

    private fun aplicarAparencia() {
        texto.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanhoSp.toFloat())
        texto.setTextColor(corTexto)
        // Texto escuro ganha sombra clara, e vice-versa, para continuar legível sobre a imagem.
        val escuro = Color.luminance(corTexto) < 0.4f
        texto.setShadowLayer(4f, 0f, 0f, if (escuro) 0xCCFFFFFF.toInt() else 0xCC000000.toInt())
        rolagem.setBackgroundColor(if (escuro) 0x66FFFFFF else 0x66000000)
        rolador.palavrasPorMinuto = palavrasPorMinuto

        // A caixa mostra cerca de quatro linhas; o espaço extra no fim deixa a última linha subir até o topo.
        val dp = resources.displayMetrics.density
        val alturaCaixa = (texto.lineHeight * 4 + 16 * dp).toInt()
        texto.updatePadding(top = (8 * dp).toInt(), bottom = alturaCaixa - texto.lineHeight)
        rolagem.updateLayoutParams<FrameLayout.LayoutParams> { height = alturaCaixa }
        aplicarPosicao()
    }

    private fun aplicarPosicao() {
        val margem = (12 * resources.displayMetrics.density).toInt()
        barra.post {
            rolagem.updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = when (posicao) {
                    PosicaoTexto.TOPO -> Gravity.TOP
                    PosicaoTexto.CENTRO -> Gravity.CENTER_VERTICAL
                    PosicaoTexto.BASE -> Gravity.BOTTOM
                }
                topMargin = if (posicao == PosicaoTexto.TOPO) margemTopo + margem else 0
                bottomMargin = if (posicao == PosicaoTexto.BASE) barra.height + pilulaMicrofone.height + 2 * margem else 0
            }
            pilulaMicrofone.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = barra.height + margem }
        }
    }

    private fun abrirConfiguracoes() {
        val painel = LayoutInflater.from(this).inflate(R.layout.sheet_camera, null)
        val dialogo = BottomSheetDialog(this)
        dialogo.setContentView(painel)
        dialogo.window?.setDimAmount(0.1f)
        dialogo.abrirExpandido()

        val posicoes = mapOf(
            R.id.posicaoTopo to PosicaoTexto.TOPO,
            R.id.posicaoCentro to PosicaoTexto.CENTRO,
            R.id.posicaoBase to PosicaoTexto.BASE,
        )
        painel.findViewById<MaterialButtonToggleGroup>(R.id.grupoPosicao).ligar(posicoes, posicao) {
            posicao = it
            aplicarPosicao()
            salvarPreferencias()
        }

        val rotuloTamanho = painel.findViewById<TextView>(R.id.rotuloTamanhoCamera)
        rotuloTamanho.text = getString(R.string.tamanho_texto, tamanhoSp)
        painel.findViewById<Slider>(R.id.sliderTamanhoCamera).apply {
            value = tamanhoSp.toFloat().coerceIn(valueFrom, valueTo)
            addOnChangeListener { _, valor, _ ->
                tamanhoSp = valor.toInt()
                rotuloTamanho.text = getString(R.string.tamanho_texto, tamanhoSp)
                aplicarAparencia()
                salvarPreferencias()
            }
        }

        val grupoCor = painel.findViewById<ChipGroup>(R.id.grupoCorCamera)
        CORES.forEach { (nome, cor) ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = SpannableString("●  $nome").apply {
                    setSpan(ForegroundColorSpan(cor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                isCheckable = true
                isChecked = cor == corTexto
                setOnCheckedChangeListener { _, marcado ->
                    if (marcado) {
                        corTexto = cor
                        aplicarAparencia()
                        salvarPreferencias()
                    }
                }
            }
            grupoCor.addView(chip)
        }

        val rotuloVelocidade = painel.findViewById<TextView>(R.id.rotuloVelocidadeCamera)
        rotuloVelocidade.text = getString(R.string.velocidade, palavrasPorMinuto)
        painel.findViewById<Slider>(R.id.sliderVelocidadeCamera).apply {
            value = palavrasPorMinuto.toFloat().coerceIn(valueFrom, valueTo)
            addOnChangeListener { _, valor, _ ->
                palavrasPorMinuto = valor.toInt()
                rotuloVelocidade.text = getString(R.string.velocidade, palavrasPorMinuto)
                rolador.palavrasPorMinuto = palavrasPorMinuto
                salvarPreferencias()
            }
        }

        val grupoPasta = painel.findViewById<ChipGroup>(R.id.grupoPastaVideo)
        val opcoesPasta = listOf<Pair<Long?, String>>(null to getString(R.string.sem_pasta)) +
            ScriptRepository(this).listarPastas().map { it.id to it.nome }
        opcoesPasta.forEach { (id, nome) ->
            grupoPasta.addView(Chip(this).apply {
                this.id = View.generateViewId()
                text = nome
                isCheckable = true
                isChecked = id == pastaVideo
                setOnCheckedChangeListener { _, marcado -> if (marcado) pastaVideo = id }
            })
        }

        dialogo.show()
    }

    // ---- Gravação de vídeo ----

    private fun alternarGravacao() {
        if (gravando) {
            pararGravacao()
            return
        }
        if (videoCapture == null) {
            Toast.makeText(this, R.string.erro_gravacao, Toast.LENGTH_LONG).show()
            return
        }
        val faltam = buildList {
            if (!permitido(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (precisaPermissaoArmazenamento() && !permitido(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (faltam.isEmpty()) iniciarGravacao(comAudio = true) else pedirPermissoesGravacao.launch(faltam.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun iniciarGravacao(comAudio: Boolean) {
        val captura = videoCapture ?: return
        pastaGravacao.mkdirs()
        val video = File(pastaGravacao, "video.mp4").apply { delete() }
        val audio = File(pastaGravacao, "audio.m4a").apply { delete() }

        // Girar o celular durante a gravação recriaria a tela e cortaria o vídeo.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        captura.targetRotation = previa.display?.rotation ?: Surface.ROTATION_0
        inicioVideoNanos = Long.MAX_VALUE

        gravacao = captura.output
            .prepareRecording(this, FileOutputOptions.Builder(video).build())
            .start(ContextCompat.getMainExecutor(this)) { evento -> tratarEvento(evento, video, audio) }

        if (comAudio) {
            val pedido = microfoneParaGravar()
            gravadorAudio = try {
                GravadorAudio(pedido, audio).also { it.iniciar() }
            } catch (e: Exception) {
                Log.e("CameraActivity", "Falha ao abrir o microfone", e)
                Toast.makeText(this, R.string.gravando_sem_som, Toast.LENGTH_LONG).show()
                null
            }
            // Confere, logo depois de começar, se o Android usou mesmo o microfone pedido.
            principal.postDelayed({ conferirMicrofoneEmUso(pedido) }, 600)
        }

        gravando = true
        atualizarBotoesGravacao()
        principal.post(atualizarMedidor)
    }

    private fun tratarEvento(evento: VideoRecordEvent, video: File, audio: File) {
        when (evento) {
            is VideoRecordEvent.Status -> {
                val duracao = evento.recordingStats.recordedDurationNanos
                // O primeiro quadro foi gravado "duração" atrás; o menor valor é a melhor estimativa.
                inicioVideoNanos = minOf(inicioVideoNanos, System.nanoTime() - duracao)
                val segundos = duracao / 1_000_000_000L
                tempoGravacao.text = String.format(Locale.ROOT, "● %02d:%02d", segundos / 60, segundos % 60)
            }
            is VideoRecordEvent.Finalize -> finalizarGravacao(evento, video, audio)
            else -> Unit
        }
    }

    private fun pararGravacao() {
        gravacao?.stop()
        gravacao = null
    }

    private fun finalizarGravacao(evento: VideoRecordEvent.Finalize, video: File, audio: File) {
        gravando = false
        principal.removeCallbacks(atualizarMedidor)
        atualizarBotoesGravacao()
        atualizarNomeMicrofone()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val gravadorDoAudio = gravadorAudio
        gravadorAudio = null
        if (evento.hasError()) Log.w("CameraActivity", "Gravação terminou com o código ${evento.error}", evento.cause)
        if (!video.exists() || video.length() == 0L) {
            gravadorDoAudio?.parar()
            Toast.makeText(this, R.string.erro_gravacao, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.salvando_video, Toast.LENGTH_SHORT).show()
        val app = applicationContext
        val inicioVideo = inicioVideoNanos
        val data = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(Date())
        val nomeArquivo = "${MontadorVideo.nomeSeguro(tituloScript)} $data"
        val subpasta = pastaVideo?.let { id -> ScriptRepository(app).listarPastas().firstOrNull { it.id == id }?.nome }
        val local = listOfNotNull(Environment.DIRECTORY_MOVIES, MontadorVideo.PASTA_BASE, subpasta?.let(MontadorVideo::nomeSeguro)).joinToString("/")

        thread(name = "SalvarVideo") {
            try {
                gravadorDoAudio?.parar()
                val comSom = gravadorDoAudio?.temAudio() == true && inicioVideo != Long.MAX_VALUE
                val deslocamentoUs = if (comSom) (gravadorDoAudio!!.inicioNanos - inicioVideo) / 1000 else 0L
                Log.i(
                    "CameraActivity",
                    "Vídeo: som=$comSom, áudio deslocado ${deslocamentoUs / 1000} ms, microfone=" +
                        (gravadorDoAudio?.dispositivoEmUso?.let { Microfones.nome(app, it) } ?: "nenhum"),
                )
                val final = File(pastaGravacao, "final.mp4")
                MontadorVideo.juntar(video, if (comSom) audio else null, deslocamentoUs, final)
                val uri = MontadorVideo.salvarNaGaleria(app, final, nomeArquivo, subpasta) ?: error("A galeria recusou o vídeo")
                listOf(video, audio, final).forEach { it.delete() }
                principal.post { avisarVideoSalvo(uri, local) }
            } catch (e: Exception) {
                Log.e("CameraActivity", "Falha ao salvar o vídeo", e)
                principal.post { Toast.makeText(app, R.string.erro_gravacao, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun avisarVideoSalvo(uri: android.net.Uri, local: String) {
        val mensagem = getString(R.string.video_salvo, local)
        if (isDestroyed) {
            Toast.makeText(applicationContext, mensagem, Toast.LENGTH_LONG).show()
            return
        }
        Snackbar.make(findViewById(R.id.raizCamera), mensagem, Snackbar.LENGTH_LONG)
            .setAnchorView(pilulaMicrofone)
            .setAction(R.string.abrir) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: ActivityNotFoundException) {
                    Log.w("CameraActivity", "Nenhum app para abrir o vídeo", e)
                }
            }
            .show()
    }

    private fun atualizarBotoesGravacao() {
        botaoGravar.setBackgroundResource(if (gravando) R.drawable.fundo_gravando else R.drawable.fundo_gravar)
        botaoGravar.contentDescription = getString(if (gravando) R.string.parar_gravacao else R.string.gravar_video)
        botaoTrocarCamera.isEnabled = !gravando
        botaoTrocarCamera.alpha = if (gravando) 0.4f else 1f
        medidorVolume.visibility = if (gravando && gravadorAudio != null) View.VISIBLE else View.GONE
        tempoGravacao.visibility = if (gravando) View.VISIBLE else View.GONE
        if (gravando) tempoGravacao.text = getString(R.string.tempo_inicial)
    }

    // ---- Microfone ----

    private fun microfoneParaGravar(): AudioDeviceInfo? =
        if (microfoneAutomatico) Microfones.automatico(Microfones.listar(this))?.dispositivo else microfoneEscolhido

    private fun nomeDoMicrofoneAtual(): String =
        microfoneParaGravar()?.let { Microfones.nome(this, it) } ?: getString(R.string.microfone_celular)

    private fun atualizarNomeMicrofone() {
        if (!gravando) nomeMicrofone.text = nomeDoMicrofoneAtual()
        aplicarPosicao()
    }

    /** Mostra na pílula o microfone que está de fato gravando, e avisa se não for o escolhido. */
    private fun conferirMicrofoneEmUso(pedido: AudioDeviceInfo?) {
        val emUso = gravadorAudio?.dispositivoEmUso ?: return
        nomeMicrofone.text = Microfones.nome(this, emUso)
        if (pedido != null && emUso.id != pedido.id) {
            Toast.makeText(this, getString(R.string.microfone_trocado, Microfones.nome(this, emUso)), Toast.LENGTH_LONG).show()
        }
    }

    /** Lista os microfones e deixa testar o volume de cada um antes de gravar. */
    @SuppressLint("MissingPermission")
    private fun abrirEscolhaMicrofone() {
        val conteudo = LayoutInflater.from(this).inflate(R.layout.dialogo_microfone, null)
        val grupo = conteudo.findViewById<RadioGroup>(R.id.grupoMicrofones)
        val medidor = conteudo.findViewById<LinearProgressIndicator>(R.id.medidorTeste)
        val captando = conteudo.findViewById<TextView>(R.id.captandoPor)

        val lista = Microfones.listar(this)
        val automatico = Microfones.automatico(lista)
        // Opção 0 = automático; as demais, cada microfone conectado.
        val opcoes: List<Pair<String, AudioDeviceInfo?>> =
            listOf(getString(R.string.microfone_automatico, automatico?.nome ?: getString(R.string.microfone_celular)) to null) +
                lista.map { it.nome to it.dispositivo }
        val marcada = if (microfoneAutomatico) 0 else opcoes.indexOfFirst { it.second?.id == microfoneEscolhido?.id }.coerceAtLeast(0)

        var teste: GravadorAudio? = null
        fun testar(dispositivo: AudioDeviceInfo?) {
            teste?.parar()
            captando.setText(R.string.microfone_aguardando)
            teste = try {
                GravadorAudio(dispositivo ?: automatico?.dispositivo, null).also { it.iniciar() }
            } catch (e: Exception) {
                Log.e("CameraActivity", "Falha ao testar o microfone", e)
                captando.setText(R.string.gravando_sem_som)
                null
            }
        }
        val atualizarTeste = object : Runnable {
            override fun run() {
                teste?.let { t ->
                    medidor.progress = (sqrt(t.nivel) * 100).toInt()
                    t.dispositivoEmUso?.let { captando.text = getString(R.string.microfone_captando, Microfones.nome(this@CameraActivity, it)) }
                }
                principal.postDelayed(this, 80)
            }
        }

        opcoes.forEachIndexed { indice, (nome, _) ->
            grupo.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = nome
                tag = indice
                isChecked = indice == marcada
            })
        }
        grupo.setOnCheckedChangeListener { g, idMarcado ->
            val indice = g.findViewById<View>(idMarcado)?.tag as? Int ?: return@setOnCheckedChangeListener
            microfoneAutomatico = indice == 0
            microfoneEscolhido = opcoes[indice].second
            testar(opcoes[indice].second)
            atualizarNomeMicrofone()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.microfone_titulo)
            .setView(conteudo)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener {
                principal.removeCallbacks(atualizarTeste)
                teste?.parar()
                teste = null
                atualizarNomeMicrofone()
            }
            .show()
        testar(opcoes[marcada].second)
        principal.post(atualizarTeste)
    }

    private fun carregarPreferencias() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        posicao = PosicaoTexto.entries.firstOrNull { it.name == p.getString("posicao", null) } ?: PosicaoTexto.TOPO
        tamanhoSp = p.getInt("tamanho", 24)
        corTexto = p.getInt("cor", Color.WHITE)
        palavrasPorMinuto = p.getInt("ppm", 120)
    }

    private fun salvarPreferencias() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("posicao", posicao.name)
            .putInt("tamanho", tamanhoSp)
            .putInt("cor", corTexto)
            .putInt("ppm", palavrasPorMinuto)
            .apply()
    }

    companion object {
        private const val PREFS = "camera"
        private const val ESTADO_FRONTAL = "frontal"
        private const val ESTADO_ROLAGEM = "rolagem"

        private val CORES = listOf(
            "Branco" to Color.WHITE,
            "Amarelo" to Color.parseColor("#FFEB3B"),
            "Verde" to Color.parseColor("#69F0AE"),
            "Ciano" to Color.parseColor("#4DD0E1"),
            "Vermelho" to Color.parseColor("#FF5252"),
            "Preto" to Color.BLACK,
        )
    }
}
