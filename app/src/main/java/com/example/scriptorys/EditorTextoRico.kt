package com.example.scriptorys

import android.content.ClipData
import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * Campo de texto do editor. Além do menu "Colar" (tratado por [ColagemFormatada.ativar]),
 * reconhece a colagem feita pela barra de área de transferência do teclado (ex.: Gboard):
 * o teclado entrega só o texto puro, então, quando esse texto é o que está copiado,
 * inserimos a versão formatada no lugar.
 */
class EditorTextoRico @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Só para testes: substitui a leitura da área de transferência do sistema. */
    var lerAreaDeTransferencia: (() -> ClipData?)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val conexao = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(conexao, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val ler = lerAreaDeTransferencia
                val formatado = text?.let { texto ->
                    if (ler != null) ColagemFormatada.formatadoDaAreaDeTransferencia(context, texto, ler)
                    else ColagemFormatada.formatadoDaAreaDeTransferencia(context, texto)
                } ?: return super.commitText(text, newCursorPosition)
                // Termina qualquer palavra em composição antes de inserir o trecho colado.
                super.finishComposingText()
                ColagemFormatada.inserir(this@EditorTextoRico, formatado)
                return true
            }
        }
    }
}
