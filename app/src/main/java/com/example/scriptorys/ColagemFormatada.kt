package com.example.scriptorys

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

/**
 * Colagem que preserva a formatação vinda de Word, Google Docs, navegadores etc.
 *
 * Esses apps colocam HTML na área de transferência. O conversor nativo do Android só entende
 * tags simples (<b>, <i>...), mas o Docs e o Word formatam quase tudo por CSS, seja inline
 * (`font-weight:700`), seja por classes num bloco <style> (`.c1{font-weight:700}`), então
 * interpretamos o HTML aqui.
 *
 * A colagem chega por dois caminhos: o menu "Colar" do campo (tratado em [ativar]) e a barra de
 * área de transferência do teclado, que entrega só o texto puro (tratado em [EditorTextoRico]).
 */
object ColagemFormatada {

    fun ativar(campo: EditText) {
        ViewCompat.setOnReceiveContentListener(campo, arrayOf("text/*"), OnReceiveContentListener { view, conteudo ->
            val clip = conteudo.clip
            val textoSimples = conteudo.flags and ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT != 0
            val colado = if (textoSimples) {
                SpannableStringBuilder().apply {
                    for (i in 0 until clip.itemCount) {
                        if (i > 0) append('\n')
                        append(clip.getItemAt(i).coerceToText(view.context))
                    }
                }
            } else {
                converter(view.context, clip)
            }
            inserir(view as EditText, colado)
            null
        })
    }

    /**
     * Para a colagem pela barra do teclado: se [textoDoTeclado] é o mesmo texto que está na área de
     * transferência, devolve a versão formatada dele; senão, null (é digitação comum).
     */
    fun formatadoDaAreaDeTransferencia(
        contexto: Context,
        textoDoTeclado: CharSequence,
        lerClip: () -> ClipData? = { lerAreaDeTransferencia(contexto) },
    ): CharSequence? {
        // Palavras digitadas chegam uma a uma; só um trecho maior pode ser uma colagem.
        if (textoDoTeclado.length < 12 || textoDoTeclado.none { it.isWhitespace() }) return null
        val clip = lerClip() ?: return null
        val simples = clip.getItemAt(0).coerceToText(contexto).toString()
        if (normalizarEspacos(simples) != normalizarEspacos(textoDoTeclado.toString())) {
            depurar(contexto, "teclado: texto digitado/colado diferente do que está copiado")
            return null
        }
        if (!temFormatacao(clip)) {
            depurar(contexto, "teclado: o que está copiado não tem formatação (tipos: ${clip.description.mimeTypes()})")
            return null
        }
        return converter(contexto, clip)
    }

    private fun lerAreaDeTransferencia(contexto: Context): ClipData? {
        val area = contexto.getSystemService(ClipboardManager::class.java) ?: return null
        // Olhar só a descrição não lê o conteúdo (nem mostra o aviso de "colado" do Android).
        val descricao = area.primaryClipDescription
        if (descricao == null || !descricao.hasMimeType("text/*")) {
            depurar(contexto, "teclado: área de transferência vazia ou sem texto")
            return null
        }
        val clip = area.primaryClip
        if (clip == null || clip.itemCount == 0) {
            depurar(contexto, "teclado: o Android não liberou a leitura da área de transferência")
            return null
        }
        return clip
    }

    private fun ClipDescription.mimeTypes() = (0 until mimeTypeCount).joinToString { getMimeType(it) }

    private fun depurar(contexto: Context, mensagem: String) {
        if (contexto.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.d(TAG, mensagem)
    }

    fun inserir(campo: EditText, colado: CharSequence) {
        val editavel = campo.text
        val inicio = minOf(campo.selectionStart, campo.selectionEnd).coerceAtLeast(0)
        val fim = maxOf(campo.selectionStart, campo.selectionEnd).coerceAtLeast(0)
        editavel.replace(inicio, fim, colado)
        campo.setSelection((inicio + colado.length).coerceAtMost(editavel.length))
    }

    fun converter(contexto: Context, clip: ClipData): Spannable {
        registrarFormato(contexto, clip)
        val colado = SpannableStringBuilder()
        for (i in 0 until clip.itemCount) {
            if (i > 0) colado.append('\n')
            colado.append(converter(clip.getItemAt(i)))
        }
        if (colado.length > 40 && !temFormatacao(clip)) {
            // O app de origem (ex.: pré-visualização de PDF ou .docx no Drive) só copiou o texto puro.
            Toast.makeText(contexto, R.string.colado_sem_formatacao, Toast.LENGTH_LONG).show()
        }
        return colado
    }

    private fun converter(item: ClipData.Item): CharSequence {
        item.htmlText?.takeIf { it.isNotBlank() }?.let { return deHtml(it) }
        // Alguns apps já entregam o texto com estilos do Android; outros (PDF, por exemplo) só texto puro.
        return normalizar(item.text ?: "")
    }

    private fun temFormatacao(clip: ClipData): Boolean = (0 until clip.itemCount).any { i ->
        val item = clip.getItemAt(i)
        val spans = (item.text as? Spanned)?.let { it.getSpans(0, it.length, Any::class.java).isNotEmpty() } ?: false
        !item.htmlText.isNullOrBlank() || spans
    }

    /**
     * Em versões de depuração, registra no Logcat (filtro "ColagemFormatada") o formato que o app
     * de origem colocou na área de transferência: tipos, se há HTML e a estrutura dele sem o texto.
     */
    private fun registrarFormato(contexto: Context, clip: ClipData) {
        if (contexto.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val descricao = clip.description
        val tipos = (0 until descricao.mimeTypeCount).joinToString { descricao.getMimeType(it) }
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val estilosAndroid = (item.text as? Spanned)?.let { t ->
                t.getSpans(0, t.length, Any::class.java).map { it.javaClass.simpleName }.distinct()
            }
            Log.d(TAG, "item $i: tipos=[$tipos] html=${item.htmlText?.length ?: 0} caracteres, estilos Android=$estilosAndroid")
            item.htmlText?.let { html ->
                // Troca cada trecho de texto por "x" para não registrar o conteúdo do roteiro.
                val estrutura = Jsoup.parse(html)
                estrutura.body().select("*").forEach { el -> el.textNodes().forEach { it.text("x") } }
                Log.d(TAG, "estrutura do HTML: " + estrutura.outerHtml().replace(ESPACOS, " ").take(3000))
            }
        }
    }

    fun deHtml(html: String): Spannable {
        val documento = Jsoup.parse(html)
        val regras = lerRegrasCss(documento)
        val corpo = documento.body()
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
                        pilha.addLast(if (no == corpo) pilha.last() else pilha.last().derivar(no, regras))
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
                is TextAppearanceSpan -> when (span.textStyle) {
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
        fun derivar(elemento: Element, regras: List<RegraCss>): Estilo {
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
            // ...depois o CSS das classes (bloco <style>) e, por último, o inline, que pode desfazer a tag
            // (o Google Docs embrulha tudo num <b style="font-weight:normal">).
            val declaracoes = regras.filter { it.aplicaA(elemento) }.flatMap { it.declaracoes } + lerCss(elemento.attr("style"))
            for ((propriedade, valor) in declaracoes) {
                when (propriedade) {
                    "font-weight" -> e = e.copy(negrito = pesoNegrito(valor))
                    "font-style" -> e = e.copy(italico = valor.startsWith("italic") || valor.startsWith("oblique"))
                    // Atalho "font: bold italic 11pt Arial".
                    "font" -> {
                        val partes = valor.split(' ')
                        if (partes.any { it == "bold" || it == "bolder" || (it.toIntOrNull() ?: 0) >= 600 }) e = e.copy(negrito = true)
                        if (partes.any { it == "italic" || it == "oblique" }) e = e.copy(italico = true)
                    }
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

    /** Regra de um bloco <style>, com seletores simples: `.classe`, `tag`, `tag.classe`, `#id`. */
    private class RegraCss(val seletores: List<String>, val declaracoes: List<Pair<String, String>>) {
        fun aplicaA(elemento: Element) = seletores.any { seletor ->
            val tag = seletor.substringBefore('.').substringBefore('#')
            val classes = seletor.split('.').drop(1).map { it.substringBefore('#') }
            val id = seletor.substringAfter('#', "").substringBefore('.')
            (tag.isNotEmpty() || classes.isNotEmpty() || id.isNotEmpty()) &&
                (tag.isEmpty() || tag == elemento.normalName()) &&
                classes.all { elemento.hasClass(it) } &&
                (id.isEmpty() || elemento.id() == id)
        }
    }

    private fun lerRegrasCss(documento: Document): List<RegraCss> {
        val css = documento.select("style").joinToString("\n") { it.data() }.replace(COMENTARIO_CSS, "")
        return REGRA_CSS.findAll(css).mapNotNull { m ->
            val seletores = m.groupValues[1].split(',').map { it.trim().lowercase() }
                // Só seletores simples; os compostos (com espaço, >, :) são raros em conteúdo copiado.
                .filter { it.isNotEmpty() && it.none { c -> c == ' ' || c == '>' || c == ':' || c == '[' } }
            if (seletores.isEmpty()) null else RegraCss(seletores, lerCss(m.groupValues[2]))
        }.toList()
    }

    private fun normalizarEspacos(texto: String) = texto.replace('\u00A0', ' ').replace(ESPACOS, " ").trim()

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

    private const val TAG = "ColagemFormatada"
    private val ESPACOS = Regex("\\s+")
    private val REGRA_CSS = Regex("([^{}@]+)\\{([^}]*)\\}")
    private val COMENTARIO_CSS = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val RGB = Regex("rgba?\\(\\s*([\\d.]+%?)\\s*,?\\s*([\\d.]+%?)\\s*,?\\s*([\\d.]+%?)\\s*(?:[,/]\\s*([\\d.]+))?\\s*\\)")
    private val BLOCOS = setOf(
        "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table",
        "blockquote", "pre", "section", "article", "header", "footer",
    )
}
