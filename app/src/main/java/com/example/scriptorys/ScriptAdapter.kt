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

/**
 * Lista de scripts. Cada cartão diferencia três gestos:
 * toque simples → [aoAbrir], toque duplo → [aoEditar], segurar → [aoExcluir].
 */
class ScriptAdapter(
    private val aoAbrir: (Script) -> Unit,
    private val aoEditar: (Script) -> Unit,
    private val aoExcluir: (Script) -> Unit,
) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {

    private var scripts: List<Script> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun atualizar(novos: List<Script>) {
        scripts = novos
        notifyDataSetChanged()
    }

    override fun getItemCount() = scripts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.vincular(scripts[position])
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
}
