package com.example.scriptorys

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import androidx.core.graphics.toColorInt
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

/**
 * Colagem que preserva a formatação vinda de Word, Google Docs, navegadores etc.
 *
 * Esses apps colocam HTML na área de transferência. O conversor nativo do Android só entende
 * tags simples (<b>, <i>...), mas o Docs e o Word formatam quase tudo por CSS inline
 * (`font-weight:700`, `color:rgb(...)`), então interpretamos o HTML aqui.
 */
object ColagemFormatada {

    fun ativar(campo: EditText) {
        ViewCompat.setOnReceiveContentListener(campo, arrayOf("text/*"), OnReceiveContentListener { view, conteudo ->
            val clip = conteudo.clip
            val textoSimples = conteudo.flags and ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT != 0
            val colado = SpannableStringBuilder()
            for (i in 0 until clip.itemCount) {
                if (i > 0) colado.append('\n')
                colado.append(if (textoSimples) clip.getItemAt(i).coerceToText(view.context) else converter(clip.getItemAt(i)))
            }
            val editavel = (view as EditText).text
            val inicio = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
            val fim = maxOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
            editavel.replace(inicio, fim, colado)
            view.setSelection((inicio + colado.length).coerceAtMost(editavel.length))
            null
        })
    }

    private fun converter(item: ClipData.Item): CharSequence {
        item.htmlText?.takeIf { it.isNotBlank() }?.let { return deHtml(it) }
        // Alguns apps já entregam o texto com estilos do Android; outros (PDF, por exemplo) só texto puro.
        return normalizar(item.text ?: "")
    }

    fun deHtml(html: String): Spannable {
        val corpo = Jsoup.parse(html).body()
        val saida = SpannableStringBuilder()
        val pilha = ArrayDeque<Estilo>().apply { addLast(Estilo()) }

        corpo.traverse(object : NodeVisitor {
            override fun head(no: Node, profundidade: Int) {
                when (no) {
                    is TextNode -> anexarTexto(saida, no, pilha.last())
                    is Element -> {
                        val tag = no.normalName()
                        if (tag in BLOCOS) quebrarLinha(saida)
                        if (tag == "br") saida.append('\n')
                        if (tag == "li") saida.append("• ")
                        pilha.addLast(if (no == corpo) pilha.last() else pilha.last().derivar(no))
                    }
                }
            }

            override fun tail(no: Node, profundidade: Int) {
                if (no is Element) {
                    pilha.removeLast()
                    if (no.normalName() in BLOCOS) quebrarLinha(saida)
                }
            }
        })

        // Remove quebras de linha sobrando no começo e no fim.
        while (saida.isNotEmpty() && saida.last().isWhitespace()) saida.delete(saida.length - 1, saida.length)
        while (saida.isNotEmpty() && saida.first() == '\n') saida.delete(0, 1)
        return saida
    }

    /** Mantém só os estilos que o editor sabe editar e salvar. */
    fun normalizar(texto: CharSequence): Spannable {
        val saida = SpannableStringBuilder(texto.toString())
        if (texto !is Spanned) return saida
        for (span in texto.getSpans(0, texto.length, Any::class.java)) {
            val inicio = texto.getSpanStart(span)
            val fim = texto.getSpanEnd(span)
            if (inicio >= fim) continue
            val estilos = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> listOf(StyleSpan(Typeface.BOLD))
                    Typeface.ITALIC -> listOf(StyleSpan(Typeface.ITALIC))
                    Typeface.BOLD_ITALIC -> listOf(StyleSpan(Typeface.BOLD), StyleSpan(Typeface.ITALIC))
                    else -> emptyList()
                }
                is UnderlineSpan -> listOf(UnderlineSpan())
                is StrikethroughSpan -> listOf(StrikethroughSpan())
                is ForegroundColorSpan -> listOfNotNull(corDeTexto(span.foregroundColor)?.let(::ForegroundColorSpan))
                is BackgroundColorSpan -> listOfNotNull(corDeFundo(span.backgroundColor)?.let(::BackgroundColorSpan))
                else -> emptyList()
            }
            estilos.forEach { saida.setSpan(it, inicio, fim, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        return saida
    }

    private fun anexarTexto(saida: SpannableStringBuilder, no: TextNode, estilo: Estilo) {
        var texto = no.wholeText.replace(' ', ' ').replace(ESPACOS, " ")
        // Espaço no início de linha não aparece no documento original.
        if (saida.isEmpty() || saida.last() == '\n' || saida.last() == ' ') texto = texto.trimStart()
        if (texto.isEmpty()) return
        val inicio = saida.length
        saida.append(texto)
        estilo.aplicar(saida, inicio, saida.length)
    }

    private fun quebrarLinha(saida: SpannableStringBuilder) {
        while (saida.isNotEmpty() && saida.last() == ' ') saida.delete(saida.length - 1, saida.length)
        if (saida.isNotEmpty() && saida.last() != '\n') saida.append('\n')
    }

    private data class Estilo(
        val negrito: Boolean = false,
        val italico: Boolean = false,
        val sublinhado: Boolean = false,
        val tachado: Boolean = false,
        val cor: Int? = null,
        val fundo: Int? = null,
    ) {
        fun derivar(elemento: Element): Estilo {
            var e = this
            // Primeiro o significado da tag...
            when (elemento.normalName()) {
                "b", "strong", "h1", "h2", "h3", "h4", "h5", "h6", "th" -> e = e.copy(negrito = true)
                "i", "em", "cite", "var", "dfn" -> e = e.copy(italico = true)
                "u", "ins" -> e = e.copy(sublinhado = true)
                "s", "strike", "del" -> e = e.copy(tachado = true)
                "mark" -> e = e.copy(fundo = Color.YELLOW)
                "font" -> lerCor(elemento.attr("color"))?.let { e = e.copy(cor = it) }
            }
            // ...depois o CSS inline, que pode desfazer a tag (o Google Docs embrulha tudo num <b style="font-weight:normal">).
            for ((propriedade, valor) in lerCss(elemento.attr("style"))) {
                when (propriedade) {
                    "font-weight" -> e = e.copy(negrito = pesoNegrito(valor))
                    "font-style" -> e = e.copy(italico = valor.startsWith("italic") || valor.startsWith("oblique"))
                    "text-decoration", "text-decoration-line" -> {
                        if ("underline" in valor) e = e.copy(sublinhado = true)
                        if ("line-through" in valor) e = e.copy(tachado = true)
                    }
                    "color" -> e = e.copy(cor = lerCor(valor))
                    "background-color" -> lerCor(valor)?.let { e = e.copy(fundo = it) }
                    // O atalho "background" pode trazer imagem e posição junto da cor.
                    "background" -> (RGB.find(valor)?.value ?: valor.substringBefore(' '))
                        .let(::lerCor)?.let { e = e.copy(fundo = it) }
                    "mso-highlight" -> lerCor(valor)?.let { e = e.copy(fundo = it) }
                }
            }
            return e
        }

        fun aplicar(texto: SpannableStringBuilder, inicio: Int, fim: Int) {
            val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            if (negrito) texto.setSpan(StyleSpan(Typeface.BOLD), inicio, fim, flag)
            if (italico) texto.setSpan(StyleSpan(Typeface.ITALIC), inicio, fim, flag)
            if (sublinhado) texto.setSpan(UnderlineSpan(), inicio, fim, flag)
            if (tachado) texto.setSpan(StrikethroughSpan(), inicio, fim, flag)
            cor?.let(::corDeTexto)?.let { texto.setSpan(ForegroundColorSpan(it), inicio, fim, flag) }
            fundo?.let(::corDeFundo)?.let { texto.setSpan(BackgroundColorSpan(it), inicio, fim, flag) }
        }
    }

    private fun lerCss(css: String): List<Pair<String, String>> =
        css.split(';').mapNotNull { declaracao ->
            val partes = declaracao.split(':', limit = 2)
            if (partes.size < 2) null
            else partes[0].trim().lowercase() to partes[1].trim().lowercase().removeSuffix("!important").trim()
        }

    private fun pesoNegrito(valor: String): Boolean = when {
        valor == "bold" || valor == "bolder" -> true
        else -> (valor.toIntOrNull() ?: 400) >= 600
    }

    private fun lerCor(valor: String): Int? {
        val v = valor.trim().lowercase()
        if (v.isEmpty() || v == "transparent" || v == "inherit" || v == "initial" || v == "windowtext") return null
        RGB.matchEntire(v)?.let { m ->
            val (r, g, b) = m.destructured
            val alfa = m.groups[4]?.value?.toFloatOrNull() ?: 1f
            if (alfa == 0f) return null
            return Color.rgb(canal(r), canal(g), canal(b))
        }
        val hex = if (v.startsWith("#") && v.length == 4) "#" + v.drop(1).map { "$it$it" }.joinToString("") else v
        return try {
            hex.toColorInt()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun canal(valor: String): Int =
        if (valor.endsWith("%")) (valor.dropLast(1).toFloat() * 2.55f).toInt().coerceIn(0, 255)
        else valor.toFloat().toInt().coerceIn(0, 255)

    /**
     * Preto e branco "de documento" são ignorados: no teleprompter o texto ganha a cor do tema,
     * e um preto explícito sumiria no fundo preto.
     */
    private fun corDeTexto(cor: Int): Int? = cor.takeIf { Color.luminance(it) in 0.05f..0.85f }

    /** Fundo branco ou quase branco é só a "folha" do documento, não um marca-texto. */
    private fun corDeFundo(cor: Int): Int? = cor.takeIf {
        Color.alpha(it) > 0 && minOf(Color.red(it), Color.green(it), Color.blue(it)) < 0xF0
    }

    private val ESPACOS = Regex("\\s+")
    private val RGB = Regex("rgba?\\(\\s*([\\d.]+%?)\\s*,?\\s*([\\d.]+%?)\\s*,?\\s*([\\d.]+%?)\\s*(?:[,/]\\s*([\\d.]+))?\\s*\\)")
    private val BLOCOS = setOf(
        "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table",
        "blockquote", "pre", "section", "article", "header", "footer",
    )
}
