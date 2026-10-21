package com.example.scriptorys

import android.Manifest
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

/** Câmera ao vivo com o script rolando por cima. */
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
        findViewById<Button>(R.id.botaoTrocarCamera).setOnClickListener {
            cameraFrontal = !cameraFrontal
            iniciarCamera()
        }
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

    private fun temPermissao() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

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
                provedor.unbindAll()
                provedor.bindToLifecycle(this, seletor, preview)
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
                bottomMargin = if (posicao == PosicaoTexto.BASE) barra.height + margem else 0
            }
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

        dialogo.show()
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
