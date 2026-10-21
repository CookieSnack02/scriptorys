package com.example.scriptorys

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Editor de texto rico para criar ou editar um script. */
class EditorActivity : AppCompatActivity() {

    private lateinit var repositorio: ScriptRepository
    private lateinit var campoTitulo: EditText
    private lateinit var campoTexto: EditText
    private var scriptId: Long? = null
    private var tituloOriginal = ""
    private var htmlOriginal = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editor)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raizEditor)) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        repositorio = ScriptRepository(this)
        campoTitulo = findViewById(R.id.campoTitulo)
        campoTexto = findViewById(R.id.campoTexto)

        scriptId = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it != -1L }
        htmlOriginal = Html.deTexto("")
        scriptId?.let { repositorio.buscar(it) }?.let { script ->
            findViewById<TextView>(R.id.tituloTela).setText(R.string.editar_script)
            val texto = Html.paraTexto(script.html)
            tituloOriginal = script.titulo
            htmlOriginal = Html.deTexto(texto)
            if (savedInstanceState == null) {
                campoTitulo.setText(script.titulo)
                campoTexto.setText(texto)
            }
        }

        configurarBarraFormatacao()
        ColagemFormatada.ativar(campoTexto)

        findViewById<Button>(R.id.botaoSalvar).setOnClickListener { salvar() }
        findViewById<Button>(R.id.botaoVoltar).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!houveAlteracao()) {
                    finish()
                    return
                }
                MaterialAlertDialogBuilder(this@EditorActivity)
                    .setTitle(R.string.descartar_titulo)
                    .setNegativeButton(R.string.continuar_editando, null)
                    .setNeutralButton(R.string.descartar) { _, _ -> finish() }
                    .setPositiveButton(R.string.salvar) { _, _ -> salvar() }
                    .show()
            }
        })
    }

    private fun houveAlteracao() =
        campoTitulo.text.toString() != tituloOriginal || Html.deTexto(campoTexto.text) != htmlOriginal

    private fun salvar() {
        if (campoTexto.text.isBlank()) {
            Toast.makeText(this, R.string.script_vazio, Toast.LENGTH_SHORT).show()
            return
        }
        repositorio.salvar(scriptId, campoTitulo.text.toString().trim(), Html.deTexto(campoTexto.text))
        finish()
    }

    private fun configurarBarraFormatacao() {
        // Desenha os rótulos dos botões com o próprio estilo que eles aplicam.
        rotular(R.id.fmtSublinhado, "U", UnderlineSpan())
        rotular(R.id.fmtTachado, "S", StrikethroughSpan())
        rotular(R.id.fmtCor, "A", ForegroundColorSpan(Color.parseColor("#C62828")))
        rotular(R.id.fmtMarca, "ab", BackgroundColorSpan(Color.parseColor("#FFF176")))

        findViewById<Button>(R.id.fmtNegrito).setOnClickListener {
            alternar(StyleSpan::class.java, { it.style == Typeface.BOLD }) { StyleSpan(Typeface.BOLD) }
        }
        findViewById<Button>(R.id.fmtItalico).setOnClickListener {
            alternar(StyleSpan::class.java, { it.style == Typeface.ITALIC }) { StyleSpan(Typeface.ITALIC) }
        }
        findViewById<Button>(R.id.fmtSublinhado).setOnClickListener {
            alternar(UnderlineSpan::class.java, { true }) { UnderlineSpan() }
        }
        findViewById<Button>(R.id.fmtTachado).setOnClickListener {
            alternar(StrikethroughSpan::class.java, { true }) { StrikethroughSpan() }
        }
        findViewById<Button>(R.id.fmtCor).setOnClickListener {
            escolherCor(R.string.cor_texto, CORES_TEXTO) { cor ->
                aplicarCor(ForegroundColorSpan::class.java, cor?.let { c -> { ForegroundColorSpan(c) } })
            }
        }
        findViewById<Button>(R.id.fmtMarca).setOnClickListener {
            escolherCor(R.string.marca_texto, CORES_MARCA) { cor ->
                aplicarCor(BackgroundColorSpan::class.java, cor?.let { c -> { BackgroundColorSpan(c) } })
            }
        }
        findViewById<Button>(R.id.fmtLimpar).setOnClickListener {
            val (inicio, fim) = selecao() ?: return@setOnClickListener
            remover(campoTexto.text, inicio, fim, CharacterStyle::class.java) { true }
        }
    }

    private fun rotular(id: Int, texto: String, estilo: Any) {
        val rotulo = SpannableString(texto)
        rotulo.setSpan(estilo, 0, texto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<Button>(id).text = rotulo
    }

    /** Intervalo selecionado, ou null (com aviso) se nada estiver selecionado. */
    private fun selecao(): Pair<Int, Int>? {
        val inicio = minOf(campoTexto.selectionStart, campoTexto.selectionEnd)
        val fim = maxOf(campoTexto.selectionStart, campoTexto.selectionEnd)
        if (inicio < 0 || inicio == fim) {
            Toast.makeText(this, R.string.selecione_trecho, Toast.LENGTH_SHORT).show()
            return null
        }
        return inicio to fim
    }

    /** Se a seleção inteira já tem o estilo, remove; senão, aplica. */
    private fun <T : Any> alternar(tipo: Class<T>, filtro: (T) -> Boolean, criar: () -> Any) {
        val (inicio, fim) = selecao() ?: return
        val texto = campoTexto.text
        val jaTem = cobre(texto, inicio, fim, tipo, filtro)
        remover(texto, inicio, fim, tipo, filtro)
        if (!jaTem) texto.setSpan(criar(), inicio, fim, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
    }

    /** Troca a cor do trecho; [criar] nulo apenas remove a cor existente. */
    private fun <T : Any> aplicarCor(tipo: Class<T>, criar: (() -> Any)?) {
        val (inicio, fim) = selecao() ?: return
        remover(campoTexto.text, inicio, fim, tipo) { true }
        criar?.let { campoTexto.text.setSpan(it(), inicio, fim, Spannable.SPAN_EXCLUSIVE_INCLUSIVE) }
    }

    private fun escolherCor(titulo: Int, cores: List<Pair<String, Int?>>, aoEscolher: (Int?) -> Unit) {
        // Guarda a seleção: o diálogo pode tirar o foco do campo.
        val inicio = campoTexto.selectionStart
        val fim = campoTexto.selectionEnd
        if (inicio == fim) {
            Toast.makeText(this, R.string.selecione_trecho, Toast.LENGTH_SHORT).show()
            return
        }
        val nomes = cores.map { (nome, cor) ->
            SpannableString("●  $nome").apply {
                if (cor != null) setSpan(ForegroundColorSpan(cor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }.toTypedArray<CharSequence>()
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setItems(nomes) { _, i ->
                campoTexto.setSelection(inicio, fim)
                aoEscolher(cores[i].second)
            }
            .show()
    }

    private fun <T : Any> cobre(texto: Editable, inicio: Int, fim: Int, tipo: Class<T>, filtro: (T) -> Boolean): Boolean {
        val intervalos = texto.getSpans(inicio, fim, tipo)
            .filter(filtro)
            .map { texto.getSpanStart(it) to texto.getSpanEnd(it) }
            .sortedBy { it.first }
        var posicao = inicio
        for ((s, e) in intervalos) {
            if (s > posicao) break
            if (e > posicao) posicao = e
            if (posicao >= fim) return true
        }
        return posicao >= fim
    }

    /** Remove o estilo só dentro de [inicio, fim), preservando o que estiver fora. */
    private fun <T : Any> remover(texto: Editable, inicio: Int, fim: Int, tipo: Class<T>, filtro: (T) -> Boolean) {
        for (span in texto.getSpans(inicio, fim, tipo)) {
            if (!filtro(span)) continue
            val s = texto.getSpanStart(span)
            val e = texto.getSpanEnd(span)
            if (e <= inicio || s >= fim) continue
            texto.removeSpan(span)
            if (s < inicio) copiar(span)?.let { texto.setSpan(it, s, inicio, Spannable.SPAN_EXCLUSIVE_INCLUSIVE) }
            if (e > fim) copiar(span)?.let { texto.setSpan(it, fim, e, Spannable.SPAN_EXCLUSIVE_INCLUSIVE) }
        }
    }

    private fun copiar(span: Any): Any? = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        is StrikethroughSpan -> StrikethroughSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is BackgroundColorSpan -> BackgroundColorSpan(span.backgroundColor)
        else -> null
    }

    companion object {
        const val EXTRA_ID = "id"

        private val CORES_TEXTO = listOf(
            "Padrão" to null,
            "Vermelho" to Color.parseColor("#C62828"),
            "Laranja" to Color.parseColor("#EF6C00"),
            "Verde" to Color.parseColor("#2E7D32"),
            "Azul" to Color.parseColor("#1565C0"),
            "Roxo" to Color.parseColor("#6A1B9A"),
        )

        private val CORES_MARCA = listOf(
            "Nenhum" to null,
            "Amarelo" to Color.parseColor("#FFF176"),
            "Verde" to Color.parseColor("#A5D6A7"),
            "Azul" to Color.parseColor("#90CAF9"),
            "Rosa" to Color.parseColor("#F48FB1"),
        )
    }
}

/** Conversão entre o texto formatado do editor e o HTML salvo. */
object Html {
    fun deTexto(texto: CharSequence): String =
        HtmlCompat.toHtml(SpannableString(texto), HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)

    fun paraTexto(html: String): CharSequence {
        val texto = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        var fim = texto.length
        while (fim > 0 && texto[fim - 1].isWhitespace()) fim--
        return texto.subSequence(0, fim)
    }
}
