package com.example.scriptorys

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA, JUSTIFICADO }
enum class Fundo { PRETO, BRANCO, MEIO }
enum class Fonte { SERIFADA, SANS, MONO, MANUSCRITA }

/** Preferências do teleprompter; ficam salvas entre um uso e outro. */
data class ConfigTeleprompter(
    val horizontal: Boolean = false,
    val espelharHorizontal: Boolean = false,
    val espelharVertical: Boolean = false,
    val palavrasPorMinuto: Int = 120,
    val alinhamento: Alinhamento = Alinhamento.CENTRO,
    val tamanhoSp: Int = 40,
    val fundo: Fundo = Fundo.PRETO,
    val fonte: Fonte = Fonte.SANS,
) {
    fun salvar(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("horizontal", horizontal)
            .putBoolean("espelharH", espelharHorizontal)
            .putBoolean("espelharV", espelharVertical)
            .putInt("ppm", palavrasPorMinuto)
            .putString("alinhamento", alinhamento.name)
            .putInt("tamanho", tamanhoSp)
            .putString("fundo", fundo.name)
            .putString("fonte", fonte.name)
            .apply()
    }

    fun corFundo(context: Context) = ContextCompat.getColor(
        context,
        when (fundo) {
            Fundo.PRETO -> R.color.prompt_preto
            Fundo.BRANCO -> R.color.prompt_branco
            Fundo.MEIO -> R.color.prompt_meio
        },
    )

    fun corTexto(context: Context) = ContextCompat.getColor(
        context,
        when (fundo) {
            Fundo.PRETO -> R.color.white
            Fundo.BRANCO -> R.color.navy
            Fundo.MEIO -> R.color.prompt_branco
        },
    )

    companion object {
        private const val PREFS = "teleprompter"

        fun carregar(context: Context): ConfigTeleprompter {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val padrao = ConfigTeleprompter()
            return ConfigTeleprompter(
                horizontal = p.getBoolean("horizontal", padrao.horizontal),
                espelharHorizontal = p.getBoolean("espelharH", padrao.espelharHorizontal),
                espelharVertical = p.getBoolean("espelharV", padrao.espelharVertical),
                palavrasPorMinuto = p.getInt("ppm", padrao.palavrasPorMinuto),
                alinhamento = enumOu(p.getString("alinhamento", null), padrao.alinhamento),
                tamanhoSp = p.getInt("tamanho", padrao.tamanhoSp),
                fundo = enumOu(p.getString("fundo", null), padrao.fundo),
                fonte = enumOu(p.getString("fonte", null), padrao.fonte),
            )
        }

        private inline fun <reified T : Enum<T>> enumOu(nome: String?, padrao: T): T =
            enumValues<T>().firstOrNull { it.name == nome } ?: padrao
    }
}

fun Fonte.typeface(): Typeface = when (this) {
    Fonte.SERIFADA -> Typeface.SERIF
    Fonte.SANS -> Typeface.SANS_SERIF
    Fonte.MONO -> Typeface.MONOSPACE
    Fonte.MANUSCRITA -> Typeface.create("cursive", Typeface.NORMAL)
}

fun TextView.aplicarAlinhamento(alinhamento: Alinhamento) {
    gravity = when (alinhamento) {
        Alinhamento.ESQUERDA, Alinhamento.JUSTIFICADO -> Gravity.START
        Alinhamento.CENTRO -> Gravity.CENTER_HORIZONTAL
        Alinhamento.DIREITA -> Gravity.END
    }
    // Texto justificado só existe a partir do Android 8.0; antes disso fica alinhado à esquerda.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        justificationMode = if (alinhamento == Alinhamento.JUSTIFICADO) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }
    }
}
