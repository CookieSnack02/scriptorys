package com.example.scriptorys

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class TeleprompterActivity : AppCompatActivity() {

    private lateinit var config: ConfigTeleprompter
    private lateinit var raiz: View
    private lateinit var rolagem: ScrollView
    private lateinit var texto: TextView
    private lateinit var painel: PainelTeleprompter
    private lateinit var regua: ReguaVelocidade
    private lateinit var pilula: TextView
    private lateinit var rotuloModo: TextView
    private lateinit var rotuloVizinho: TextView
    private lateinit var botaoPlay: ImageButton
    private lateinit var rolador: RolagemAutomatica
    private lateinit var controle: ControleRemoto
    private lateinit var bolhaValor: TextView

    private var scriptId = -1L
    private var painelRodando = false

    /** Ponto da leitura (0 a 1), para não perder o lugar ao girar a tela ou mudar o tamanho da fonte. */
    private var fracaoLida = 0f
    private var restaurandoPosicao = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_teleprompter)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        esconderBarras()

        scriptId = intent.getLongExtra(MainActivity.EXTRA_SCRIPT_ID, -1L)
        if (ScriptRepository(this).buscar(scriptId) == null) {
            finish()
            return
        }

        raiz = findViewById(R.id.raizPrompt)
        rolagem = findViewById(R.id.rolagem)
        texto = findViewById(R.id.textoPrompt)
        regua = findViewById(R.id.regua)
        pilula = findViewById(R.id.pilulaVelocidade)
        rotuloModo = findViewById(R.id.rotuloModo)
        rotuloVizinho = findViewById(R.id.rotuloVizinho)
        botaoPlay = findViewById(R.id.botaoPlay)
        bolhaValor = findViewById(R.id.bolhaValor)
        val painelView = findViewById<ConstraintLayout>(R.id.painel)
        painel = PainelTeleprompter(painelView)
        carregarTexto()

        ViewCompat.setOnApplyWindowInsetsListener(painelView) { v, insets ->
            val corte = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = corte.left, top = corte.top, right = corte.right, bottom = corte.bottom)
            insets
        }

        rolador = RolagemAutomatica(rolagem, texto) { atualizarPlay() }
        // Com o foco já na tela, o Android não "gasta" a primeira seta do controle movendo o foco.
        raiz.requestFocus()
        // A régua desliza até o novo valor e o balão acima dela mostra qual é.
        controle = ControleRemoto(rolador, ::alternarPlay) { direcao ->
            regua.animarPara(regua.valor + direcao * 2 * regua.passo)
        }

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

        configurarPainel()
        if (savedInstanceState != null) fracaoLida = savedInstanceState.getFloat(ESTADO_FRACAO)
        aplicar(ConfigTeleprompter.carregar(this))
        configurarRegua()
        atualizarCarrossel(animar = false)
        painel.mostrar(rodando = false, horizontal = horizontal(), animar = false)
    }

    private fun configurarPainel() {
        findViewById<View>(R.id.fundoCartao).isClickable = true
        findViewById<View>(R.id.botaoVoltarPrompt).setOnClickListener { finish() }
        findViewById<View>(R.id.botaoTrocarModo).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java).putExtra(MainActivity.EXTRA_SCRIPT_ID, scriptId))
            finish()
        }
        botaoPlay.setOnClickListener { alternarPlay() }
        findViewById<View>(R.id.botaoAnterior).setOnClickListener { irParaParagrafo(-1) }
        findViewById<View>(R.id.botaoProximo).setOnClickListener { irParaParagrafo(1) }
        findViewById<View>(R.id.botaoEditar).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, scriptId))
        }
        findViewById<View>(R.id.botaoTexto).setOnClickListener { abrirOpcoesTexto() }
        findViewById<View>(R.id.botaoConfig).setOnClickListener { abrirConfiguracoes() }
        findViewById<View>(R.id.iconeLento).setOnClickListener { regua.animarPara(regua.valor - 2 * regua.passo) }
        findViewById<View>(R.id.iconeRapido).setOnClickListener { regua.animarPara(regua.valor + 2 * regua.passo) }
        pilula.setOnClickListener { digitarVelocidade() }

        findViewById<View>(R.id.botaoAlternarModo).setOnClickListener { alternarModoVelocidade() }
        val carrossel = findViewById<View>(R.id.carrosselModo)
        carrossel.setOnClickListener { alternarModoVelocidade() }
        val deslizar = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy)) alternarModoVelocidade()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                alternarModoVelocidade()
                return true
            }
        })
        carrossel.setOnTouchListener { _, evento -> deslizar.onTouchEvent(evento) }

        regua.aoMudar = { valor, final ->
            mudarVelocidade(valor, final)
            // Enquanto a régua se move, o balão mostra o valor em que ela vai parar.
            bolhaValor.mostrarBrevemente(textoVelocidade(regua.encaixar(valor)))
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Voltando do editor: mostra o texto atualizado, no mesmo ponto da leitura.
        carregarTexto()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        painel.mostrar(rolador.rodando, horizontal(), animar = false)
        atualizarCarrossel(animar = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(ESTADO_FRACAO, fracaoLida)
    }

    override fun onPause() {
        super.onPause()
        if (::rolador.isInitialized) {
            rolador.parar()
            atualizarPlay()
        }
    }

    // O controle Bluetooth é tratado antes da tela, para a rolagem nunca "pular" linhas ou páginas.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (::controle.isInitialized && controle.tecla(event)) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        (::controle.isInitialized && controle.movimento(ev)) || super.dispatchGenericMotionEvent(ev)

    private fun esconderBarras() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun horizontal() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun alcance() = ((rolagem.getChildAt(0)?.height ?: 0) - rolagem.height).coerceAtLeast(0)

    private fun carregarTexto() {
        val script = ScriptRepository(this).buscar(scriptId) ?: return
        texto.text = destacarColchetes(Html.paraTexto(script.html))
    }

    /** Trechos entre colchetes, como [pausa] ou [sorrir], são instruções: ficam em laranja. */
    private fun destacarColchetes(original: CharSequence): CharSequence {
        val resultado = SpannableStringBuilder(original)
        val laranja = ContextCompat.getColor(this, R.color.laranja)
        COLCHETES.findAll(resultado).forEach {
            resultado.setSpan(ForegroundColorSpan(laranja), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return resultado
    }

    private fun alternarPlay() {
        if (rolador.rodando) rolador.pausar() else rolador.iniciar()
        atualizarPlay()
    }

    private fun atualizarPlay() {
        val rodando = rolador.rodando
        botaoPlay.setImageResource(if (rodando) R.drawable.ic_pause else R.drawable.ic_play)
        botaoPlay.contentDescription = getString(if (rodando) R.string.pause else R.string.play)
        if (rodando != painelRodando) {
            painelRodando = rodando
            painel.mostrar(rodando, horizontal(), animar = true)
        }
    }

    /** Pula, deslizando, para o início do parágrafo anterior (ou do atual, se já passou dele) ou do próximo. */
    private fun irParaParagrafo(direcao: Int) {
        val layout = texto.layout ?: return
        val conteudo = texto.text
        val inicios = listOf(0) + conteudo.indices.filter { i ->
            conteudo[i] == '\n' && i + 1 < conteudo.length && conteudo[i + 1] != '\n'
        }.map { it + 1 }
        // A linha de leitura fica no meio da tela, que é onde começa o texto (há meia tela de espaço antes).
        val leitura = rolagem.scrollY
        val atual = layout.getLineStart(layout.getLineForVertical(leitura))
        val alvo = if (direcao < 0) {
            val anteriores = inicios.filter { it <= atual }
            val doAtual = anteriores.lastOrNull() ?: 0
            val topoAtual = layout.getLineTop(layout.getLineForOffset(doAtual))
            if (leitura - topoAtual > texto.lineHeight / 2) doAtual else anteriores.dropLast(1).lastOrNull() ?: 0
        } else {
            inicios.firstOrNull { it > atual } ?: return
        }
        rolador.deslizarPara(layout.getLineTop(layout.getLineForOffset(alvo)))
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
        texto.aplicarAlinhamento(nova.alinhamento)
        texto.setTextSize(TypedValue.COMPLEX_UNIT_SP, nova.tamanhoSp.toFloat())
        texto.typeface = nova.fonte.typeface()
        texto.setTextColor(nova.corTexto(this))
        raiz.setBackgroundColor(nova.corFundo(this))
        nova.salvar(this)
    }

    // ---- Velocidade: régua, pílula e carrossel de modo ----

    private fun configurarRegua() {
        if (config.modoVelocidade == ModoVelocidade.PALAVRAS) {
            regua.configurar(0f, 300f, 5f, 10, config.palavrasPorMinuto.toFloat())
        } else {
            regua.configurar(0f, 10f, 0.2f, 5, config.nivelRolagem)
        }
        mudarVelocidade(regua.valor, final = false)
    }

    private fun mudarVelocidade(valor: Float, final: Boolean) {
        if (config.modoVelocidade == ModoVelocidade.PALAVRAS) {
            config = config.copy(palavrasPorMinuto = valor.roundToInt())
            rolador.nivelRolagem = null
            rolador.palavrasPorMinuto = config.palavrasPorMinuto
        } else {
            config = config.copy(nivelRolagem = (valor * 10).roundToInt() / 10f)
            rolador.nivelRolagem = config.nivelRolagem
        }
        pilula.text = textoVelocidade(regua.encaixar(valor))
        if (final) config.salvar(this)
    }

    private fun textoVelocidade(valor: Float): String {
        val limitado = valor.coerceIn(regua.minimo, regua.maximo)
        return if (config.modoVelocidade == ModoVelocidade.PALAVRAS) {
            getString(R.string.ppm_formato, limitado.roundToInt())
        } else {
            getString(R.string.rolagem_formato, String.format(Locale.getDefault(), "%.1f", limitado))
        }
    }

    private fun alternarModoVelocidade() {
        val novo = if (config.modoVelocidade == ModoVelocidade.PALAVRAS) ModoVelocidade.ROLAGEM else ModoVelocidade.PALAVRAS
        config = config.copy(modoVelocidade = novo)
        config.salvar(this)
        configurarRegua()
        atualizarCarrossel(animar = true)
    }

    /** Modo escolhido no centro; na vertical o outro modo aparece esmaecido ao lado, como num carrossel. */
    private fun atualizarCarrossel(animar: Boolean) {
        val palavras = config.modoVelocidade == ModoVelocidade.PALAVRAS
        val nomeAtual = getString(if (palavras) R.string.modo_palavras else R.string.modo_rolagem)
        val nomeVizinho = getString(if (palavras) R.string.modo_rolagem else R.string.modo_palavras)
        val deitado = horizontal()
        val cor = ContextCompat.getColor(this, if (deitado) R.color.white else R.color.laranja)

        val trocarTextos = {
            rotuloModo.text = nomeAtual
            rotuloModo.setTextColor(cor)
            rotuloModo.updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = if (deitado) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            }
            rotuloVizinho.text = nomeVizinho
            rotuloVizinho.visibility = if (deitado) View.GONE else View.VISIBLE
            // O vizinho fica do lado em que está na ordem [ROLAGEM, PALAVRAS POR MINUTO].
            rotuloModo.post {
                val distancia = rotuloModo.width / 2f + rotuloVizinho.width / 2f + 28 * resources.displayMetrics.density
                rotuloVizinho.translationX = if (palavras) -distancia else distancia
            }
        }

        if (!animar) {
            trocarTextos()
            return
        }
        // Desliza o rótulo antigo para fora e o novo para dentro.
        val deslocamento = 48 * resources.displayMetrics.density * (if (palavras) 1 else -1)
        listOf(rotuloModo, rotuloVizinho).forEach { it.animate().cancel() }
        rotuloVizinho.animate().alpha(0f).setDuration(140).start()
        rotuloModo.animate().translationX(deslocamento).alpha(0f).setDuration(140).withEndAction {
            trocarTextos()
            rotuloModo.translationX = -deslocamento
            rotuloModo.animate().translationX(0f).alpha(1f).setDuration(220).start()
            rotuloVizinho.animate().alpha(0.35f).setDuration(220).start()
        }.start()
    }

    private fun digitarVelocidade() {
        val palavras = config.modoVelocidade == ModoVelocidade.PALAVRAS
        val campo = EditText(this).apply {
            inputType = if (palavras) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (palavras) regua.valor.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", regua.valor))
            selectAll()
        }
        val moldura = FrameLayout(this).apply {
            val margem = (24 * resources.displayMetrics.density).toInt()
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editar_velocidade)
            .setMessage(if (palavras) "0 – 300 ppm" else "0 – 10")
            .setView(moldura)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                campo.text.toString().replace(',', '.').toFloatOrNull()?.let { regua.animarPara(it) }
            }
            .show()
    }

    // ---- Painéis de opções ----

    private fun novoPainel(layout: Int): Pair<BottomSheetDialog, View> {
        rolador.pausar()
        atualizarPlay()
        val conteudo = LayoutInflater.from(this).inflate(layout, null)
        val dialogo = BottomSheetDialog(this)
        dialogo.setContentView(conteudo)
        dialogo.window?.setDimAmount(0.2f)
        dialogo.abrirExpandido()
        // O painel não pode trazer de volta as barras do sistema (elas empurrariam os controles).
        dialogo.setOnShowListener {
            dialogo.window?.let { janela ->
                WindowCompat.getInsetsController(janela, janela.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        dialogo.setOnDismissListener { esconderBarras() }
        return dialogo to conteudo
    }

    /** Botão "TT": tamanho, fonte e alinhamento do texto. */
    private fun abrirOpcoesTexto() {
        val (dialogo, conteudo) = novoPainel(R.layout.sheet_texto)

        val alinhamentos = mapOf(
            R.id.alinharEsquerda to Alinhamento.ESQUERDA,
            R.id.alinharCentro to Alinhamento.CENTRO,
            R.id.alinharDireita to Alinhamento.DIREITA,
            R.id.alinharJustificado to Alinhamento.JUSTIFICADO,
        )
        conteudo.findViewById<MaterialButtonToggleGroup>(R.id.grupoAlinhamento).ligar(alinhamentos, config.alinhamento) {
            aplicar(config.copy(alinhamento = it))
        }

        val rotuloTamanho = conteudo.findViewById<TextView>(R.id.rotuloTamanho)
        rotuloTamanho.text = getString(R.string.tamanho_texto, config.tamanhoSp)
        conteudo.findViewById<Slider>(R.id.sliderTamanho).apply {
            value = config.tamanhoSp.toFloat().coerceIn(valueFrom, valueTo)
            addOnChangeListener { _, valor, _ ->
                rotuloTamanho.text = getString(R.string.tamanho_texto, valor.toInt())
                aplicar(config.copy(tamanhoSp = valor.toInt()))
            }
        }

        val fontes = mapOf(
            R.id.fonteSerifada to Fonte.SERIFADA,
            R.id.fonteSans to Fonte.SANS,
            R.id.fonteMono to Fonte.MONO,
            R.id.fonteManuscrita to Fonte.MANUSCRITA,
        )
        conteudo.findViewById<MaterialButtonToggleGroup>(R.id.grupoFonte).ligar(fontes, config.fonte) {
            aplicar(config.copy(fonte = it))
        }

        dialogo.show()
    }

    /** Engrenagem: orientação da tela, espelhamento e cor de fundo. */
    private fun abrirConfiguracoes() {
        val (dialogo, conteudo) = novoPainel(R.layout.sheet_teleprompter)
        fun grupo(id: Int) = conteudo.findViewById<MaterialButtonToggleGroup>(id)

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

        val fundos = mapOf(
            R.id.fundoPreto to Fundo.PRETO,
            R.id.fundoBranco to Fundo.BRANCO,
            R.id.fundoMeio to Fundo.MEIO,
        )
        grupo(R.id.grupoFundo).ligar(fundos, config.fundo) { aplicar(config.copy(fundo = it)) }

        dialogo.show()
    }

    companion object {
        private const val ESTADO_FRACAO = "fracao"
        private val COLCHETES = Regex("\\[[^\\]\\n]*]")
    }
}

/**
 * Abre o painel já totalmente aberto. Sem isso, na horizontal ele para na metade
 * e é preciso arrastá-lo para cima para ver todas as opções.
 */
fun BottomSheetDialog.abrirExpandido() {
    behavior.skipCollapsed = true
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
}

/** Mostra um aviso curto sobre o texto (ex.: a nova velocidade) e o esconde em seguida. */
fun TextView.mostrarBrevemente(mensagem: String) {
    text = mensagem
    animate().cancel()
    alpha = 1f
    visibility = View.VISIBLE
    animate().alpha(0f).setStartDelay(900).setDuration(400).withEndAction { visibility = View.GONE }.start()
}

/** Marca o botão correspondente a [atual] e avisa quando outro for escolhido. */
fun <T> MaterialButtonToggleGroup.ligar(opcoes: Map<Int, T>, atual: T, aoEscolher: (T) -> Unit) {
    opcoes.entries.firstOrNull { it.value == atual }?.let { check(it.key) }
    addOnButtonCheckedListener { _, id, marcado ->
        if (marcado) opcoes[id]?.let(aoEscolher)
    }
}
