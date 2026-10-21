package com.example.scriptorys

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Traduz controles Bluetooth (como o MOCUTE-052 que acompanha o teleprompter Ulanzi) em
 * comandos suaves para a [RolagemAutomatica].
 *
 * Esses controles mudam de "modo" e, conforme o modo, chegam ao Android como setas, teclas de
 * volume/mídia, botões e alavanca de gamepad ou roda de mouse. Todos são aceitos:
 * - cima/baixo (setas, volume +/−, Page Up/Down, alavanca, roda): desliza o texto;
 * - esquerda/direita (setas, faixa anterior/próxima, L1/R1, alavanca): diminui/aumenta a velocidade;
 * - OK/Enter/espaço/play-pause/botão A: inicia ou pausa.
 *
 * Sem este tratamento, a lista rola sozinha "aos pulos" (uma linha ou uma página por vez).
 */
class ControleRemoto(
    private val rolador: RolagemAutomatica,
    private val alternarPlay: () -> Unit,
    /** Recebe -1 (mais devagar) ou +1 (mais rápido). */
    private val mudarVelocidade: (Int) -> Unit,
) {
    private var alavancaHorizontalTravada = false

    /** Retorna true quando a tecla foi usada (e não deve seguir para o sistema). */
    fun tecla(evento: KeyEvent): Boolean {
        Log.d(TAG, "tecla ${KeyEvent.keyCodeToString(evento.keyCode)} ação=${evento.action} repetição=${evento.repeatCount} dispositivo=${evento.device?.name}")
        val pressionou = evento.action == KeyEvent.ACTION_DOWN
        val primeira = pressionou && evento.repeatCount == 0
        when (evento.keyCode) {
            in VOLTAR, in AVANCAR -> {
                val direcao = if (evento.keyCode in VOLTAR) -1f else 1f
                val pagina = evento.keyCode == KeyEvent.KEYCODE_PAGE_UP || evento.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                when {
                    primeira -> {
                        rolador.empurrar(if (pagina) direcao * 4 else direcao)
                        if (!pagina) rolador.segurar(direcao)
                    }
                    !pressionou -> rolador.segurar(0f)
                }
            }
            in MAIS_LENTO, in MAIS_RAPIDO -> {
                // Segurando, a velocidade continua mudando, mas em passos espaçados.
                if (pressionou && evento.repeatCount % 4 == 0) {
                    mudarVelocidade(if (evento.keyCode in MAIS_RAPIDO) 1 else -1)
                }
            }
            in PLAY_PAUSE -> if (primeira) alternarPlay()
            else -> return false
        }
        return true
    }

    /** Alavanca de gamepad e roda de mouse. */
    fun movimento(evento: MotionEvent): Boolean {
        if (evento.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && evento.action == MotionEvent.ACTION_SCROLL) {
            val roda = evento.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (roda == 0f) return false
            rolador.empurrar(-roda)
            return true
        }

        val alavanca = evento.isFromSource(InputDevice.SOURCE_JOYSTICK) || evento.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!alavanca || evento.action != MotionEvent.ACTION_MOVE) return false

        val y = maiorEixo(evento, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
        rolador.segurar(if (abs(y) < ZONA_MORTA) 0f else y)

        val x = maiorEixo(evento, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        if (!alavancaHorizontalTravada && abs(x) > 0.6f) {
            alavancaHorizontalTravada = true
            mudarVelocidade(if (x > 0) 1 else -1)
        } else if (abs(x) < 0.3f) {
            alavancaHorizontalTravada = false
        }
        return true
    }

    private fun maiorEixo(evento: MotionEvent, a: Int, b: Int): Float {
        val va = evento.getAxisValue(a)
        val vb = evento.getAxisValue(b)
        return if (abs(va) >= abs(vb)) va else vb
    }

    private companion object {
        const val TAG = "ControleRemoto"
        const val ZONA_MORTA = 0.25f

        val VOLTAR = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_VOLUME_UP,
        )
        val AVANCAR = setOf(
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN,
        )
        val MAIS_LENTO = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_BUTTON_L1,
        )
        val MAIS_RAPIDO = setOf(
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_BUTTON_R1,
        )
        val PLAY_PAUSE = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_START,
        )
    }
}
