package com.example.scriptorys

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Régua de velocidade: o marcador laranja fica fixo no centro e os tracinhos deslizam por baixo
 * dele quando o usuário arrasta (com inércia e encaixe no tracinho mais próximo).
 * Arrastar para a esquerda aumenta o valor. Os tracinhos esmaecem nas bordas.
 */
class ReguaVelocidade @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Chamado a cada mudança; [final] é true quando o usuário solta e o valor assenta. */
    var aoMudar: ((valor: Float, final: Boolean) -> Unit)? = null

    var minimo = 0f
        private set
    var maximo = 300f
        private set
    var passo = 5f
        private set
    private var maiorACada = 10

    var valor = 120f
        private set

    private val densidade = resources.displayMetrics.density
    private val espacamento = 9f * densidade
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val laranja = ContextCompat.getColor(context, R.color.laranja)

    private val rolador = OverScroller(context)
    private var rastreador: VelocityTracker? = null
    private var ultimoX = 0f
    private var animacao: ValueAnimator? = null
    private var ultimoTracinho = 0

    fun configurar(minimo: Float, maximo: Float, passo: Float, maiorACada: Int, valor: Float) {
        this.minimo = minimo
        this.maximo = maximo
        this.passo = passo
        this.maiorACada = maiorACada
        this.valor = valor.coerceIn(minimo, maximo)
        ultimoTracinho = indice(this.valor)
        invalidate()
    }

    /** Muda o valor deslizando a régua até ele (usado pelos ícones e pelo controle remoto). */
    fun animarPara(novo: Float) {
        rolador.forceFinished(true)
        animacao?.cancel()
        val alvo = encaixar(novo)
        animacao = ValueAnimator.ofFloat(valor, alvo).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { definir(it.animatedValue as Float, false) }
            doOnEnd { definir(alvo, true) }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val centroX = width / 2f
        val centroY = height / 2f
        val metade = width / 2f
        val primeiro = floor((valor - metade / espacamento * passo - minimo) / passo).toInt().coerceAtLeast(0)
        val ultimo = indice(maximo)

        for (i in primeiro..ultimo) {
            val x = centroX + (minimo + i * passo - valor) / passo * espacamento
            if (x < -espacamento) continue
            if (x > width + espacamento) break
            // Esmaece suavemente nas bordas.
            val distancia = abs(x - centroX) / metade
            val opacidade = (1f - distancia * distancia).coerceIn(0f, 1f)
            val maior = i % maiorACada == 0
            tinta.color = Color.WHITE
            tinta.alpha = ((if (maior) 255 else 150) * opacidade).toInt()
            tinta.strokeWidth = (if (maior) 4f else 2f) * densidade
            val meiaAltura = (if (maior) 13f else 7f) * densidade
            canvas.drawLine(x, centroY - meiaAltura, x, centroY + meiaAltura, tinta)
        }

        tinta.color = laranja
        tinta.alpha = 255
        tinta.strokeWidth = 4.5f * densidade
        val meiaAltura = minOf(20f * densidade, height / 2f - tinta.strokeWidth)
        canvas.drawLine(centroX, centroY - meiaAltura, centroX, centroY + meiaAltura, tinta)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                rolador.forceFinished(true)
                animacao?.cancel()
                rastreador = VelocityTracker.obtain()
                ultimoX = evento.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = evento.x - ultimoX
                ultimoX = evento.x
                definir(valor - dx / espacamento * passo, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                rastreador?.addMovement(evento)
                rastreador?.computeCurrentVelocity(1000)
                val velocidadeX = rastreador?.xVelocity ?: 0f
                rastreador?.recycle()
                rastreador = null
                if (abs(velocidadeX) > 300f) {
                    // Inércia: a régua continua girando e freia sozinha.
                    val inicio = posicaoPx(valor)
                    rolador.fling(inicio, 0, -velocidadeX.toInt(), 0, posicaoPx(minimo), posicaoPx(maximo), 0, 0)
                    postInvalidateOnAnimation()
                } else {
                    animarPara(valor)
                }
                return true
            }
        }
        rastreador?.addMovement(evento)
        return true
    }

    override fun computeScroll() {
        if (rolador.computeScrollOffset()) {
            definir(minimo + rolador.currX / espacamento * passo, false)
            if (rolador.isFinished) animarPara(valor) else postInvalidateOnAnimation()
        }
    }

    private fun definir(novo: Float, final: Boolean) {
        valor = novo.coerceIn(minimo, maximo)
        val tracinho = indice(valor)
        if (tracinho != ultimoTracinho) {
            ultimoTracinho = tracinho
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
        aoMudar?.invoke(valor, final)
    }

    /** Valor arredondado para o tracinho mais próximo (o que a régua assume ao soltar). */
    fun encaixar(v: Float) = (minimo + ((v - minimo) / passo).roundToInt() * passo).coerceIn(minimo, maximo)
    private fun indice(v: Float) = ((v - minimo) / passo).roundToInt()
    private fun posicaoPx(v: Float) = ((v - minimo) / passo * espacamento).toInt()

    private fun ValueAnimator.doOnEnd(acao: () -> Unit) {
        addListener(object : AnimatorListenerAdapter() {
            private var cancelada = false
            override fun onAnimationCancel(animation: Animator) {
                cancelada = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelada) acao()
            }
        })
    }
}
