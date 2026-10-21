package com.example.scriptorys

import android.animation.ValueAnimator
import android.view.Choreographer
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.exp

/**
 * Motor de rolagem do teleprompter. Soma duas velocidades num único movimento contínuo:
 * - a automática, em palavras por minuto (derivada da altura média que cada palavra ocupa no [texto]);
 * - a manual, vinda do controle remoto, com inércia.
 *
 * Nenhuma mudança é instantânea: iniciar, pausar, trocar a velocidade ou apertar o controle
 * aceleram e desaceleram suavemente, então o texto nunca dá saltos.
 */
class RolagemAutomatica(
    private val rolagem: ScrollView,
    private val texto: TextView,
    private val aoTerminar: () -> Unit,
) : Choreographer.FrameCallback {

    var palavrasPorMinuto = 120
        set(valor) {
            field = valor
            agendar()
        }

    /** Quando não nulo, rola numa velocidade fixa (0 a 10) em vez de seguir as palavras por minuto. */
    var nivelRolagem: Float? = null
        set(valor) {
            field = valor
            agendar()
        }

    /** Se a rolagem automática está ligada (ela pode ainda estar desacelerando depois de pausar). */
    var rodando = false
        private set

    private var agendado = false
    private var ultimoQuadro = 0L
    private var posicao = 0f
    private var velocidadeAuto = 0f
    private var velocidadeManual = 0f
    private var direcaoSegurada = 0f

    fun iniciar() {
        rodando = true
        agendar()
    }

    /** Pausa desacelerando até parar. */
    fun pausar() {
        rodando = false
        agendar()
    }

    /** Para tudo na hora (ao sair da tela). */
    fun parar() {
        rodando = false
        velocidadeAuto = 0f
        velocidadeManual = 0f
        direcaoSegurada = 0f
        Choreographer.getInstance().removeFrameCallback(this)
        agendado = false
        ultimoQuadro = 0L
    }

    /** Volta ao início deslizando, e fica pausado. */
    fun reiniciar() {
        parar()
        rolagem.smoothScrollTo(0, 0)
    }

    private var deslize: ValueAnimator? = null

    /** Desliza suavemente até a posição [y] (pular parágrafos), sem interromper a rolagem automática. */
    fun deslizarPara(y: Int) {
        deslize?.cancel()
        velocidadeManual = 0f
        val inicio = rolagem.scrollY
        val distancia = abs(y - inicio)
        deslize = ValueAnimator.ofInt(inicio, y).apply {
            duration = (300 + distancia / 6).toLong().coerceAtMost(800)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { rolagem.scrollTo(0, it.animatedValue as Int) }
            start()
        }
    }

    /** Um toque no controle: um empurrão que desliza cerca de uma linha e meia e vai freando. */
    fun empurrar(direcao: Float) {
        velocidadeManual = (velocidadeManual + direcao * linha() * IMPULSO_LINHAS)
            .coerceIn(-linha() * MAXIMO_LINHAS, linha() * MAXIMO_LINHAS)
        agendar()
    }

    /** Botão segurado ou alavanca inclinada: rola continuamente enquanto durar (-1 a 1; 0 solta). */
    fun segurar(direcao: Float) {
        direcaoSegurada = direcao
        agendar()
    }

    private fun agendar() {
        if (agendado) return
        agendado = true
        ultimoQuadro = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(tempoNanos: Long) {
        val dt = if (ultimoQuadro == 0L) 0f else ((tempoNanos - ultimoQuadro) / 1_000_000_000f).coerceAtMost(0.05f)
        ultimoQuadro = tempoNanos

        // Aproximação exponencial: a velocidade "persegue" o alvo em vez de mudar de uma vez.
        val alvoAuto = if (rodando) pixelsPorSegundo() else 0f
        velocidadeAuto += (alvoAuto - velocidadeAuto) * suavizar(dt, SUAVIDADE_AUTO)
        velocidadeManual = if (direcaoSegurada != 0f) {
            velocidadeManual + (direcaoSegurada * linha() * SEGURAR_LINHAS - velocidadeManual) * suavizar(dt, SUAVIDADE_SEGURAR)
        } else {
            velocidadeManual * (1f - suavizar(dt, FRENAGEM))
        }

        // O usuário pode ter arrastado o texto com o dedo.
        if (abs(rolagem.scrollY - posicao) > 2f) posicao = rolagem.scrollY.toFloat()
        val maximo = ((rolagem.getChildAt(0)?.height ?: 0) - rolagem.height).coerceAtLeast(0).toFloat()
        posicao = (posicao + (velocidadeAuto + velocidadeManual) * dt).coerceIn(0f, maximo)
        // Só freia o controle quando ele empurra contra a borda (no início ainda dá para descer).
        if ((posicao <= 0f && velocidadeManual < 0f) || (posicao >= maximo && velocidadeManual > 0f)) velocidadeManual = 0f
        rolagem.scrollTo(0, posicao.toInt())

        if (rodando && posicao >= maximo && maximo > 0f) {
            rodando = false
            velocidadeAuto = 0f
            aoTerminar()
        }

        val parado = !rodando && abs(velocidadeAuto) < 1f && abs(velocidadeManual) < 1f && direcaoSegurada == 0f
        if (parado) {
            velocidadeAuto = 0f
            velocidadeManual = 0f
            agendado = false
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun suavizar(dt: Float, constanteTempo: Float) = 1f - exp(-dt / constanteTempo)

    private fun linha() = texto.lineHeight.toFloat().coerceAtLeast(1f)

    private var textoContado: CharSequence? = null
    private var palavras = 1

    private fun pixelsPorSegundo(): Float {
        nivelRolagem?.let { return it * linha() * LINHAS_POR_NIVEL }
        if (texto.text !== textoContado) {
            textoContado = texto.text
            palavras = Texto.contarPalavras(texto.text).coerceAtLeast(1)
        }
        val alturaTexto = (texto.layout?.height ?: texto.height).toFloat()
        return alturaTexto / palavras * palavrasPorMinuto / 60f
    }

    private companion object {
        /** Segundos para chegar a ~63% da nova velocidade automática. */
        const val SUAVIDADE_AUTO = 0.35f
        const val SUAVIDADE_SEGURAR = 0.15f
        const val FRENAGEM = 0.3f

        /** Em linhas por segundo; um empurrão percorre IMPULSO × FRENAGEM ≈ 1,5 linha. */
        const val IMPULSO_LINHAS = 5f
        const val SEGURAR_LINHAS = 6f
        const val MAXIMO_LINHAS = 14f

        /** No modo rolagem, cada nível equivale a meia linha por segundo. */
        const val LINHAS_POR_NIVEL = 0.5f
    }
}
