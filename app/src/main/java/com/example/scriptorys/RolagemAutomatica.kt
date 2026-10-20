package com.example.scriptorys

import android.view.Choreographer
import android.widget.ScrollView
import android.widget.TextView

/**
 * Rola um [ScrollView] de forma contínua numa velocidade em palavras por minuto.
 * A velocidade em pixels é derivada da altura média que cada palavra ocupa no [texto].
 */
class RolagemAutomatica(
    private val rolagem: ScrollView,
    private val texto: TextView,
    private val aoTerminar: () -> Unit,
) : Choreographer.FrameCallback {

    var palavrasPorMinuto = 120
    var rodando = false
        private set

    private var ultimoQuadro = 0L
    private var posicao = 0f

    fun iniciar() {
        if (rodando) return
        rodando = true
        posicao = rolagem.scrollY.toFloat()
        ultimoQuadro = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pausar() {
        rodando = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    fun reiniciar() {
        rolagem.scrollTo(0, 0)
        posicao = 0f
    }

    override fun doFrame(tempoNanos: Long) {
        if (!rodando) return
        if (ultimoQuadro != 0L) {
            val segundos = (tempoNanos - ultimoQuadro) / 1_000_000_000f
            // O usuário pode ter arrastado o texto manualmente.
            if (kotlin.math.abs(rolagem.scrollY - posicao) > 2f) posicao = rolagem.scrollY.toFloat()
            posicao += pixelsPorSegundo() * segundos
            rolagem.scrollTo(0, posicao.toInt())
            val maximo = (rolagem.getChildAt(0)?.height ?: 0) - rolagem.height
            if (rolagem.scrollY >= maximo) {
                pausar()
                aoTerminar()
                return
            }
        }
        ultimoQuadro = tempoNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    private var textoContado: CharSequence? = null
    private var palavras = 1

    private fun pixelsPorSegundo(): Float {
        if (texto.text !== textoContado) {
            textoContado = texto.text
            palavras = Texto.contarPalavras(texto.text).coerceAtLeast(1)
        }
        val alturaTexto = (texto.layout?.height ?: texto.height).toFloat()
        return alturaTexto / palavras * palavrasPorMinuto / 60f
    }
}
