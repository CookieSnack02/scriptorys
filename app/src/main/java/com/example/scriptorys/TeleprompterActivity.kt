package com.example.scriptorys

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider

class TeleprompterActivity : AppCompatActivity() {

    private lateinit var config: ConfigTeleprompter
    private lateinit var raiz: View
    private lateinit var rolagem: ScrollView
    private lateinit var texto: TextView
    private lateinit var barraControles: ViewGroup
    private lateinit var botaoPlay: MaterialButton
    private lateinit var rolador: RolagemAutomatica

    /** Ponto da leitura (0 a 1), para não perder o lugar ao girar a tela ou mudar o tamanho da fonte. */
    private var fracaoLida = 0f
    private var restaurandoPosicao = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_teleprompter)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val script = ScriptRepository(this).buscar(intent.getLongExtra(MainActivity.EXTRA_SCRIPT_ID, -1L))
        if (script == null) {
            finish()
            return
        }

        raiz = findViewById(R.id.raizPrompt)
        rolagem = findViewById(R.id.rolagem)
        texto = findViewById(R.id.textoPrompt)
        barraControles = findViewById(R.id.barraControles)
        botaoPlay = findViewById(R.id.botaoPlay)

        findViewById<TextView>(R.id.tituloPrompt).text = script.titulo
        texto.text = Html.paraTexto(script.html)

        ViewCompat.setOnApplyWindowInsetsListener(barraControles) { v, insets ->
            val corte = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = corte.left, top = corte.top, right = corte.right)
            insets
        }

        rolador = RolagemAutomatica(rolagem, texto) { atualizarPlay() }

        // Espaço de meia tela antes e depois do texto, para começar e terminar na linha de leitura.
        rolagem.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                val meio = (bottom - top) / 2
                texto.updatePadding(top = meio, bottom = meio)
            }
        }
        texto.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                restaurandoPosicao = true
                rolagem.post {
                    rolagem.scrollTo(0, (fracaoLida * alcance()).toInt())
                    restaurandoPosicao = false
                }
            }
        }
        rolagem.setOnScrollChangeListener { _, _, y, _, _ ->
            if (!restaurandoPosicao && alcance() > 0) fracaoLida = y.toFloat() / alcance()
        }

        // Tocar no texto inicia/pausa sem atrapalhar a rolagem manual com o dedo.
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

        findViewById<Button>(R.id.botaoVoltar).setOnClickListener { finish() }
        findViewById<Button>(R.id.botaoReiniciar).setOnClickListener {
            rolador.reiniciar()
            fracaoLida = 0f
        }
        botaoPlay.setOnClickListener { alternarPlay() }
        findViewById<Button>(R.id.botaoConfig).setOnClickListener { abrirConfiguracoes() }

        if (savedInstanceState != null) fracaoLida = savedInstanceState.getFloat(ESTADO_FRACAO)
        aplicar(ConfigTeleprompter.carregar(this))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(ESTADO_FRACAO, fracaoLida)
    }

    override fun onPause() {
        super.onPause()
        if (::rolador.isInitialized) {
            rolador.pausar()
            atualizarPlay()
        }
    }

    private fun alcance() = ((rolagem.getChildAt(0)?.height ?: 0) - rolagem.height).coerceAtLeast(0)

    private fun alternarPlay() {
        if (rolador.rodando) rolador.pausar() else rolador.iniciar()
        atualizarPlay()
    }

    private fun atualizarPlay() {
        val rodando = rolador.rodando
        botaoPlay.setIconResource(if (rodando) R.drawable.ic_pause else R.drawable.ic_play)
        botaoPlay.contentDescription = getString(if (rodando) R.string.pause else R.string.play)
        // Os controles somem durante a leitura e voltam ao pausar.
        barraControles.animate().alpha(if (rodando) 0f else 1f).setDuration(250).start()
        barraControles.isClickable = !rodando
        for (i in 0 until barraControles.childCount) {
            barraControles.getChildAt(i).isEnabled = !rodando
        }
    }

    private fun aplicar(nova: ConfigTeleprompter) {
        config = nova
        requestedOrientation = if (nova.horizontal) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        rolagem.scaleX = if (nova.espelharHorizontal) -1f else 1f
        rolagem.scaleY = if (nova.espelharVertical) -1f else 1f
        rolador.palavrasPorMinuto = nova.palavrasPorMinuto
        texto.aplicarAlinhamento(nova.alinhamento)
        texto.setTextSize(TypedValue.COMPLEX_UNIT_SP, nova.tamanhoSp.toFloat())
        texto.typeface = nova.fonte.typeface()
        texto.setTextColor(nova.corTexto(this))
        raiz.setBackgroundColor(nova.corFundo(this))
        nova.salvar(this)
    }

    private fun abrirConfiguracoes() {
        rolador.pausar()
        atualizarPlay()

        val painel = LayoutInflater.from(this).inflate(R.layout.sheet_teleprompter, null)
        val dialogo = BottomSheetDialog(this)
        dialogo.setContentView(painel)
        dialogo.window?.setDimAmount(0.2f)

        fun grupo(id: Int) = painel.findViewById<MaterialButtonToggleGroup>(id)

        grupo(R.id.grupoOrientacao).apply {
            check(if (config.horizontal) R.id.orientacaoHorizontal else R.id.orientacaoVertical)
            addOnButtonCheckedListener { _, id, marcado ->
                if (marcado) aplicar(config.copy(horizontal = id == R.id.orientacaoHorizontal))
            }
        }

        grupo(R.id.grupoEspelhar).apply {
            if (config.espelharHorizontal) check(R.id.espelharHorizontal)
            if (config.espelharVertical) check(R.id.espelharVertical)
            addOnButtonCheckedListener { _, id, marcado ->
                aplicar(
                    if (id == R.id.espelharHorizontal) config.copy(espelharHorizontal = marcado)
                    else config.copy(espelharVertical = marcado)
                )
            }
        }

        val rotuloVelocidade = painel.findViewById<TextView>(R.id.rotuloVelocidade)
        rotuloVelocidade.text = getString(R.string.velocidade, config.palavrasPorMinuto)
        painel.findViewById<Slider>(R.id.sliderVelocidade).apply {
            value = config.palavrasPorMinuto.toFloat().coerceIn(valueFrom, valueTo)
            addOnChangeListener { _, valor, _ ->
                rotuloVelocidade.text = getString(R.string.velocidade, valor.toInt())
                aplicar(config.copy(palavrasPorMinuto = valor.toInt()))
            }
        }

        val alinhamentos = mapOf(
            R.id.alinharEsquerda to Alinhamento.ESQUERDA,
            R.id.alinharCentro to Alinhamento.CENTRO,
            R.id.alinharDireita to Alinhamento.DIREITA,
            R.id.alinharJustificado to Alinhamento.JUSTIFICADO,
        )
        grupo(R.id.grupoAlinhamento).ligar(alinhamentos, config.alinhamento) {
            aplicar(config.copy(alinhamento = it))
        }

        val rotuloTamanho = painel.findViewById<TextView>(R.id.rotuloTamanho)
        rotuloTamanho.text = getString(R.string.tamanho_texto, config.tamanhoSp)
        painel.findViewById<Slider>(R.id.sliderTamanho).apply {
            value = config.tamanhoSp.toFloat().coerceIn(valueFrom, valueTo)
            addOnChangeListener { _, valor, _ ->
                rotuloTamanho.text = getString(R.string.tamanho_texto, valor.toInt())
                aplicar(config.copy(tamanhoSp = valor.toInt()))
            }
        }

        val fundos = mapOf(
            R.id.fundoPreto to Fundo.PRETO,
            R.id.fundoBranco to Fundo.BRANCO,
            R.id.fundoMeio to Fundo.MEIO,
        )
        grupo(R.id.grupoFundo).ligar(fundos, config.fundo) { aplicar(config.copy(fundo = it)) }

        val fontes = mapOf(
            R.id.fonteSerifada to Fonte.SERIFADA,
            R.id.fonteSans to Fonte.SANS,
            R.id.fonteMono to Fonte.MONO,
            R.id.fonteManuscrita to Fonte.MANUSCRITA,
        )
        grupo(R.id.grupoFonte).ligar(fontes, config.fonte) { aplicar(config.copy(fonte = it)) }

        dialogo.show()
    }

    companion object {
        private const val ESTADO_FRACAO = "fracao"
    }
}

/** Marca o botão correspondente a [atual] e avisa quando outro for escolhido. */
fun <T> MaterialButtonToggleGroup.ligar(opcoes: Map<Int, T>, atual: T, aoEscolher: (T) -> Unit) {
    opcoes.entries.firstOrNull { it.value == atual }?.let { check(it.key) }
    addOnButtonCheckedListener { _, id, marcado ->
        if (marcado) opcoes[id]?.let(aoEscolher)
    }
}
