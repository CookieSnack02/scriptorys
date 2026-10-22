package com.example.scriptorys

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Diálogos de pasta usados pela tela principal e pelo editor. */
object DialogosPasta {

    /** Pede o nome de uma pasta (nova ou renomeada); só confirma nomes não vazios. */
    fun pedirNome(contexto: Context, titulo: Int, nomeAtual: String = "", botao: Int = R.string.criar, aoConfirmar: (String) -> Unit) {
        val campo = EditText(contexto).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = contexto.getString(R.string.nome_pasta)
            setSingleLine()
            setText(nomeAtual)
            setSelection(nomeAtual.length)
        }
        val moldura = FrameLayout(contexto).apply {
            val margem = (24 * contexto.resources.displayMetrics.density).toInt()
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(contexto)
            .setTitle(titulo)
            .setView(moldura)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(botao) { _, _ ->
                campo.text.toString().trim().takeIf { it.isNotEmpty() }?.let(aoConfirmar)
            }
            .show()
        campo.requestFocus()
    }

    /**
     * Lista "Sem pasta", as pastas existentes e "Nova pasta…". Devolve o id escolhido
     * (null = sem pasta), criando a pasta antes quando for o caso.
     */
    fun escolher(contexto: Context, repositorio: ScriptRepository, atual: Long?, aoEscolher: (Long?) -> Unit) {
        val pastas = repositorio.listarPastas()
        val opcoes = listOf(contexto.getString(R.string.sem_pasta)) +
            pastas.map { it.nome } +
            contexto.getString(R.string.nova_pasta_opcao)
        val marcada = if (atual == null) 0 else pastas.indexOfFirst { it.id == atual } + 1
        MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.escolher_pasta)
            .setSingleChoiceItems(opcoes.toTypedArray(), marcada) { dialogo, i ->
                dialogo.dismiss()
                when (i) {
                    0 -> aoEscolher(null)
                    opcoes.lastIndex -> pedirNome(contexto, R.string.nova_pasta) { nome ->
                        aoEscolher(repositorio.criarPasta(nome).id)
                    }
                    else -> aoEscolher(pastas[i - 1].id)
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    fun nome(contexto: Context, repositorio: ScriptRepository, pastaId: Long?): String =
        pastaId?.let { id -> repositorio.listarPastas().firstOrNull { it.id == id }?.nome }
            ?: contexto.getString(R.string.sem_pasta)
}
