package com.example.scriptorys

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

/** Um item da lista principal: uma pasta (com quantos scripts tem) ou um script. */
sealed interface ItemLista {
    data class DePasta(val pasta: Pasta, val quantidade: Int) : ItemLista
    data class DeScript(val script: Script) : ItemLista
}

/**
 * Lista de pastas e scripts. Cada cartão de script diferencia três gestos:
 * toque simples → [aoAbrir], toque duplo → [aoEditar], segurar → [aoExcluir].
 * Pastas: toque → [aoAbrirPasta], segurar → [aoSegurarPasta].
 */
class ScriptAdapter(
    private val aoAbrir: (Script) -> Unit,
    private val aoEditar: (Script) -> Unit,
    private val aoExcluir: (Script) -> Unit,
    private val aoAbrirPasta: (Pasta) -> Unit,
    private val aoSegurarPasta: (Pasta) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var itens: List<ItemLista> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun atualizar(novos: List<ItemLista>) {
        itens = novos
        notifyDataSetChanged()
    }

    override fun getItemCount() = itens.size

    override fun getItemViewType(position: Int) = if (itens[position] is ItemLista.DePasta) TIPO_PASTA else TIPO_SCRIPT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_PASTA) {
            PastaViewHolder(inflador.inflate(R.layout.item_pasta, parent, false))
        } else {
            ViewHolder(inflador.inflate(R.layout.item_script, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = itens[position]) {
            is ItemLista.DePasta -> (holder as PastaViewHolder).vincular(item)
            is ItemLista.DeScript -> (holder as ViewHolder).vincular(item.script)
        }
    }

    inner class PastaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nome: TextView = view.findViewById(R.id.nomePasta)
        private val quantidade: TextView = view.findViewById(R.id.quantidadePasta)
        private var pasta: Pasta? = null

        init {
            view.setOnClickListener { pasta?.let(aoAbrirPasta) }
            view.setOnLongClickListener {
                pasta?.let(aoSegurarPasta)
                pasta != null
            }
        }

        fun vincular(item: ItemLista.DePasta) {
            pasta = item.pasta
            nome.text = item.pasta.nome
            quantidade.text = itemView.resources.getQuantityString(R.plurals.quantidade_scripts, item.quantidade, item.quantidade)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titulo: TextView = view.findViewById(R.id.tituloScript)
        private val previa: TextView = view.findViewById(R.id.previaScript)
        private val info: TextView = view.findViewById(R.id.infoScript)
        private var script: Script? = null

        private val gestos = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            // Só dispara depois de garantir que não virá um segundo toque.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                script?.let(aoAbrir)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                script?.let(aoEditar)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                script?.let(aoExcluir)
            }
        })

        init {
            view.isLongClickable = false
            view.setOnTouchListener { v, evento ->
                gestos.onTouchEvent(evento)
                // Repassa o evento ao cartão só para manter o efeito visual de toque;
                // o "soltar" vira "cancelar" para o cartão não disparar um clique próprio.
                if (evento.actionMasked == MotionEvent.ACTION_UP) {
                    val cancelar = MotionEvent.obtain(evento).apply { action = MotionEvent.ACTION_CANCEL }
                    v.onTouchEvent(cancelar)
                    cancelar.recycle()
                } else {
                    v.onTouchEvent(evento)
                }
                true
            }
        }

        fun vincular(script: Script) {
            this.script = script
            val contexto = itemView.context
            val texto = HtmlCompat.fromHtml(script.html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
            titulo.text = script.titulo.ifBlank { contexto.getString(R.string.sem_titulo) }
            previa.text = texto.replace(Regex("\\s+"), " ")
            val palavras = Texto.contarPalavras(texto)
            val data = DateFormat.getMediumDateFormat(contexto).format(Date(script.atualizadoEm))
            info.text = "$palavras palavras · $data"
        }
    }

    private companion object {
        const val TIPO_PASTA = 0
        const val TIPO_SCRIPT = 1
    }
}
