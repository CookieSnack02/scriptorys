package com.example.scriptorys

import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT

/**
 * Posiciona os controles do teleprompter nos dois estados e anima a passagem entre eles:
 * - completo (pausado): voltar, "Trocar modo", pílula de velocidade e o cartão com régua,
 *   modo de velocidade, editar / texto / configurações e anterior / play / próximo;
 * - compacto (rodando): só a pílula com a régua, ladeada por "anterior" e "pausar".
 *
 * Os mesmos botões mudam de lugar e de tamanho (ChangeBounds) enquanto o resto esmaece (Fade),
 * então a transição é contínua. Na vertical o cartão empilha as linhas; na horizontal fica numa só.
 */
class PainelTeleprompter(private val painel: ConstraintLayout) {

    private val densidade = painel.resources.displayMetrics.density
    private val transicao = TransitionSet().apply {
        ordering = TransitionSet.ORDERING_TOGETHER
        addTransition(ChangeBounds())
        addTransition(Fade())
        duration = 420
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    fun mostrar(rodando: Boolean, horizontal: Boolean, animar: Boolean) {
        val conjunto = if (rodando) compacto(horizontal) else completo(horizontal)
        if (animar) TransitionManager.beginDelayedTransition(painel, transicao)
        conjunto.applyTo(painel)
    }

    private fun completo(horizontal: Boolean): ConstraintSet = ConstraintSet().apply {
        barraSuperior(View.VISIBLE)

        if (horizontal) {
            tamanho(R.id.fundoCartao, 0, dp(96))
            ligar(R.id.fundoCartao, START, PARENT_ID, START, 12)
            ligar(R.id.fundoCartao, END, PARENT_ID, END, 12)
            ligar(R.id.fundoCartao, BOTTOM, PARENT_ID, BOTTOM, 20)

            // Da esquerda: botão laranja e o modo de velocidade.
            tamanho(R.id.botaoAlternarModo, dp(40), dp(40))
            ligar(R.id.botaoAlternarModo, START, R.id.fundoCartao, START, 20)
            centralizarVertical(R.id.botaoAlternarModo, R.id.fundoCartao)
            setVisibility(R.id.botaoAlternarModo, View.VISIBLE)

            // Da direita para a esquerda: configurações, texto, editar, próximo, play, anterior, régua.
            botaoRedondo(R.id.botaoConfig, 48)
            ligar(R.id.botaoConfig, END, R.id.fundoCartao, END, 20)
            centralizarVertical(R.id.botaoConfig, R.id.fundoCartao)
            botaoRedondo(R.id.botaoTexto, 48)
            ligar(R.id.botaoTexto, END, R.id.botaoConfig, START, 8)
            centralizarVertical(R.id.botaoTexto, R.id.fundoCartao)
            botaoRedondo(R.id.botaoEditar, 48)
            ligar(R.id.botaoEditar, END, R.id.botaoTexto, START, 8)
            centralizarVertical(R.id.botaoEditar, R.id.fundoCartao)

            tamanho(R.id.botaoProximo, dp(44), dp(44))
            ligar(R.id.botaoProximo, END, R.id.botaoEditar, START, 16)
            centralizarVertical(R.id.botaoProximo, R.id.fundoCartao)
            tamanho(R.id.botaoPlay, dp(56), dp(56))
            ligar(R.id.botaoPlay, END, R.id.botaoProximo, START, 6)
            centralizarVertical(R.id.botaoPlay, R.id.fundoCartao)
            tamanho(R.id.botaoAnterior, dp(44), dp(44))
            ligar(R.id.botaoAnterior, END, R.id.botaoPlay, START, 6)
            centralizarVertical(R.id.botaoAnterior, R.id.fundoCartao)

            tamanho(R.id.iconeRapido, dp(36), dp(36))
            ligar(R.id.iconeRapido, END, R.id.botaoAnterior, START, 12)
            centralizarVertical(R.id.iconeRapido, R.id.fundoCartao)
            tamanho(R.id.regua, dp(96), dp(48))
            ligar(R.id.regua, END, R.id.iconeRapido, START, 6)
            centralizarVertical(R.id.regua, R.id.fundoCartao)
            tamanho(R.id.iconeLento, dp(36), dp(36))
            ligar(R.id.iconeLento, END, R.id.regua, START, 6)
            centralizarVertical(R.id.iconeLento, R.id.fundoCartao)

            tamanho(R.id.carrosselModo, 0, dp(32))
            ligar(R.id.carrosselModo, START, R.id.botaoAlternarModo, END, 12)
            ligar(R.id.carrosselModo, END, R.id.iconeLento, START, 8)
            centralizarVertical(R.id.carrosselModo, R.id.fundoCartao)
        } else {
            tamanho(R.id.fundoCartao, 0, dp(292))
            ligar(R.id.fundoCartao, START, PARENT_ID, START, 16)
            ligar(R.id.fundoCartao, END, PARENT_ID, END, 16)
            ligar(R.id.fundoCartao, BOTTOM, PARENT_ID, BOTTOM, 20)

            // Linha 1: régua.
            tamanho(R.id.iconeLento, dp(36), dp(36))
            ligar(R.id.iconeLento, START, R.id.fundoCartao, START, 26)
            ligar(R.id.iconeLento, TOP, R.id.fundoCartao, TOP, 34)
            tamanho(R.id.iconeRapido, dp(36), dp(36))
            ligar(R.id.iconeRapido, END, R.id.fundoCartao, END, 26)
            centralizarVertical(R.id.iconeRapido, R.id.iconeLento)
            tamanho(R.id.regua, 0, dp(48))
            ligar(R.id.regua, START, R.id.iconeLento, END, 8)
            ligar(R.id.regua, END, R.id.iconeRapido, START, 8)
            centralizarVertical(R.id.regua, R.id.iconeLento)

            // Linha 2: modo de velocidade (o botão laranja só existe na horizontal).
            tamanho(R.id.botaoAlternarModo, dp(40), dp(40))
            ligar(R.id.botaoAlternarModo, START, R.id.fundoCartao, START, 24)
            ligar(R.id.botaoAlternarModo, TOP, R.id.regua, BOTTOM, 10)
            setVisibility(R.id.botaoAlternarModo, View.GONE)
            tamanho(R.id.carrosselModo, 0, dp(32))
            ligar(R.id.carrosselModo, START, R.id.fundoCartao, START, 24)
            ligar(R.id.carrosselModo, END, R.id.fundoCartao, END, 24)
            ligar(R.id.carrosselModo, TOP, R.id.regua, BOTTOM, 14)

            // Linha 3: editar e texto à esquerda, configurações à direita.
            botaoRedondo(R.id.botaoEditar)
            ligar(R.id.botaoEditar, START, R.id.fundoCartao, START, 24)
            ligar(R.id.botaoEditar, TOP, R.id.carrosselModo, BOTTOM, 10)
            botaoRedondo(R.id.botaoTexto)
            ligar(R.id.botaoTexto, START, R.id.botaoEditar, END, 14)
            centralizarVertical(R.id.botaoTexto, R.id.botaoEditar)
            botaoRedondo(R.id.botaoConfig)
            ligar(R.id.botaoConfig, END, R.id.fundoCartao, END, 24)
            centralizarVertical(R.id.botaoConfig, R.id.botaoEditar)

            // Linha 4: anterior, play, próximo.
            tamanho(R.id.botaoPlay, dp(64), dp(64))
            ligar(R.id.botaoPlay, START, R.id.fundoCartao, START, 0)
            ligar(R.id.botaoPlay, END, R.id.fundoCartao, END, 0)
            ligar(R.id.botaoPlay, TOP, R.id.botaoEditar, BOTTOM, 8)
            tamanho(R.id.botaoAnterior, dp(48), dp(48))
            ligar(R.id.botaoAnterior, END, R.id.botaoPlay, START, 36)
            centralizarVertical(R.id.botaoAnterior, R.id.botaoPlay)
            tamanho(R.id.botaoProximo, dp(48), dp(48))
            ligar(R.id.botaoProximo, START, R.id.botaoPlay, END, 36)
            centralizarVertical(R.id.botaoProximo, R.id.botaoPlay)
        }

        tamanho(R.id.pilulaVelocidade, WRAP_CONTENT, WRAP_CONTENT)
        ligar(R.id.pilulaVelocidade, START, PARENT_ID, START, 0)
        ligar(R.id.pilulaVelocidade, END, PARENT_ID, END, 0)
        ligar(R.id.pilulaVelocidade, BOTTOM, R.id.fundoCartao, TOP, 16)

        // Os quadrados que emolduram "anterior" e "pausar" só aparecem no modo compacto.
        moldura(R.id.fundoAnterior, R.id.botaoAnterior)
        moldura(R.id.fundoPlay, R.id.botaoPlay)

        listOf(
            R.id.fundoCartao, R.id.pilulaVelocidade, R.id.carrosselModo, R.id.iconeLento, R.id.regua,
            R.id.iconeRapido, R.id.botaoAnterior, R.id.botaoPlay, R.id.botaoProximo, R.id.botaoEditar,
            R.id.botaoTexto, R.id.botaoConfig,
        ).forEach { setVisibility(it, View.VISIBLE) }
        setVisibility(R.id.fundoAnterior, View.INVISIBLE)
        setVisibility(R.id.fundoPlay, View.INVISIBLE)
    }

    private fun compacto(horizontal: Boolean): ConstraintSet = completo(horizontal).apply {
        barraSuperior(View.INVISIBLE)
        listOf(
            R.id.pilulaVelocidade, R.id.carrosselModo, R.id.botaoProximo, R.id.botaoEditar,
            R.id.botaoTexto, R.id.botaoConfig,
        ).forEach { setVisibility(it, View.INVISIBLE) }
        if (horizontal) setVisibility(R.id.botaoAlternarModo, View.INVISIBLE)

        // Anterior e pausar viram quadrados nas laterais da pílula.
        listOf(R.id.botaoAnterior, R.id.botaoPlay).forEach {
            clear(it)
            tamanho(it, dp(58), dp(58))
            ligar(it, BOTTOM, PARENT_ID, BOTTOM, 28)
        }
        setVisibility(R.id.fundoAnterior, View.VISIBLE)
        setVisibility(R.id.fundoPlay, View.VISIBLE)

        clear(R.id.fundoCartao)
        if (horizontal) {
            tamanho(R.id.fundoCartao, dp(440), dp(78))
            ligar(R.id.fundoCartao, START, PARENT_ID, START, 0)
            ligar(R.id.fundoCartao, END, PARENT_ID, END, 0)
            centralizarVertical(R.id.fundoCartao, R.id.botaoPlay)
            ligar(R.id.botaoAnterior, END, R.id.fundoCartao, START, 14)
            ligar(R.id.botaoPlay, START, R.id.fundoCartao, END, 14)
        } else {
            ligar(R.id.botaoAnterior, START, PARENT_ID, START, 16)
            ligar(R.id.botaoPlay, END, PARENT_ID, END, 16)
            tamanho(R.id.fundoCartao, 0, dp(78))
            ligar(R.id.fundoCartao, START, R.id.botaoAnterior, END, 14)
            ligar(R.id.fundoCartao, END, R.id.botaoPlay, START, 14)
            centralizarVertical(R.id.fundoCartao, R.id.botaoPlay)
        }

        // A régua ocupa a pílula, entre o bonequinho andando e o correndo.
        listOf(R.id.iconeLento, R.id.regua, R.id.iconeRapido).forEach { clear(it) }
        tamanho(R.id.iconeLento, dp(36), dp(36))
        ligar(R.id.iconeLento, START, R.id.fundoCartao, START, 20)
        centralizarVertical(R.id.iconeLento, R.id.fundoCartao)
        tamanho(R.id.iconeRapido, dp(36), dp(36))
        ligar(R.id.iconeRapido, END, R.id.fundoCartao, END, 20)
        centralizarVertical(R.id.iconeRapido, R.id.fundoCartao)
        tamanho(R.id.regua, 0, dp(48))
        ligar(R.id.regua, START, R.id.iconeLento, END, 6)
        ligar(R.id.regua, END, R.id.iconeRapido, START, 6)
        centralizarVertical(R.id.regua, R.id.fundoCartao)
    }

    private fun ConstraintSet.barraSuperior(visibilidade: Int) {
        tamanho(R.id.botaoVoltarPrompt, dp(52), dp(52))
        ligar(R.id.botaoVoltarPrompt, START, PARENT_ID, START, 16)
        ligar(R.id.botaoVoltarPrompt, TOP, PARENT_ID, TOP, 16)
        tamanho(R.id.botaoTrocarModo, WRAP_CONTENT, dp(56))
        ligar(R.id.botaoTrocarModo, END, PARENT_ID, END, 16)
        centralizarVertical(R.id.botaoTrocarModo, R.id.botaoVoltarPrompt)
        setVisibility(R.id.botaoVoltarPrompt, visibilidade)
        setVisibility(R.id.botaoTrocarModo, visibilidade)
    }

    private fun ConstraintSet.botaoRedondo(id: Int, lado: Int = 52) = tamanho(id, dp(lado), dp(lado))

    private fun ConstraintSet.moldura(fundo: Int, botao: Int) {
        clear(fundo)
        tamanho(fundo, 0, 0)
        ligar(fundo, START, botao, START, 0)
        ligar(fundo, END, botao, END, 0)
        ligar(fundo, TOP, botao, TOP, 0)
        ligar(fundo, BOTTOM, botao, BOTTOM, 0)
    }

    private fun ConstraintSet.tamanho(id: Int, largura: Int, altura: Int) {
        constrainWidth(id, largura)
        constrainHeight(id, altura)
    }

    private fun ConstraintSet.ligar(id: Int, lado: Int, alvo: Int, ladoAlvo: Int, margemDp: Int) =
        connect(id, lado, alvo, ladoAlvo, dp(margemDp))

    private fun ConstraintSet.centralizarVertical(id: Int, alvo: Int) {
        connect(id, TOP, alvo, TOP, 0)
        connect(id, BOTTOM, alvo, BOTTOM, 0)
    }

    private fun dp(valor: Int) = (valor * densidade).toInt()
}
