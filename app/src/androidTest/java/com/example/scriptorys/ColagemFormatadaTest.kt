package com.example.scriptorys

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColagemFormatadaTest {

    /** HTML no formato que o Google Docs coloca na área de transferência. */
    private val htmlDocs = """
        <meta charset="utf-8"><b style="font-weight:normal;" id="docs-internal-guid-abc">
        <p dir="ltr"><span style="font-weight:700;color:#000000;">Abertura</span><span style="color:#000000;"> normal </span><span style="font-style:italic;color:#000000;">itálico</span><span style="color:#000000;"> e </span><span style="text-decoration:underline;color:#000000;">sublinhado</span></p>
        <p dir="ltr"><span style="color:rgb(204, 0, 0);">vermelho</span> <span style="background-color:#ffff00;">amarelo</span> <s>riscado</s></p>
        <ul><li>um</li><li><strong>dois</strong></li></ul></b>
    """.trimIndent()

    @Test
    fun converteEstilosInlineDoDocs() {
        val texto = ColagemFormatada.deHtml(htmlDocs)
        assertEquals("Abertura normal itálico e sublinhado\nvermelho amarelo riscado\n• um\n• dois", texto.toString())

        // O <b style="font-weight:normal"> que embrulha tudo não pode deixar o texto inteiro em negrito.
        assertEquals(listOf("Abertura", "dois"), trechos(texto, StyleSpan::class.java) { it.style == Typeface.BOLD })
        assertEquals(listOf("itálico"), trechos(texto, StyleSpan::class.java) { it.style == Typeface.ITALIC })
        assertEquals(listOf("sublinhado"), trechos(texto, UnderlineSpan::class.java) { true })
        assertEquals(listOf("riscado"), trechos(texto, StrikethroughSpan::class.java) { true })
        // Preto "de documento" é descartado; o vermelho fica.
        assertEquals(listOf("vermelho"), trechos(texto, ForegroundColorSpan::class.java) { true })
        assertEquals(Color.rgb(204, 0, 0), texto.getSpans(0, texto.length, ForegroundColorSpan::class.java)[0].foregroundColor)
        assertEquals(listOf("amarelo"), trechos(texto, BackgroundColorSpan::class.java) { true })
    }

    @Test
    fun entendeEstilosPorClasseCss() {
        // Formato de exportação do Google Docs: estilos em classes dentro de um bloco <style>.
        val html = """
            <html><head><style type="text/css">
              .c1{font-weight:700;color:#000000} .c2{font-style:italic} p.c3{text-decoration:underline}
              /* comentário */ .c4{font:bold 11pt Arial}
            </style></head>
            <body><p class="c3"><span class="c1">Negrito</span> <span class="c2">itálico</span></p>
            <p><span class="c4">Atalho</span> normal</p></body></html>
        """.trimIndent()
        val texto = ColagemFormatada.deHtml(html)
        assertEquals("Negrito itálico\nAtalho normal", texto.toString())
        assertEquals(listOf("Negrito", "Atalho"), trechos(texto, StyleSpan::class.java) { it.style == Typeface.BOLD })
        assertEquals(listOf("itálico"), trechos(texto, StyleSpan::class.java) { it.style == Typeface.ITALIC })
        // O sublinhado vem do parágrafo inteiro (cada trecho de texto ganha o seu).
        assertEquals("Negrito itálico", trechos(texto, UnderlineSpan::class.java) { true }.joinToString(""))
    }

    @Test
    fun colarPelaBarraDoTecladoMantemFormatacao() {
        ActivityScenario.launch(EditorActivity::class.java).use { cenario ->
            cenario.onActivity { atividade ->
                val campo = atividade.findViewById<EditorTextoRico>(R.id.campoTexto)
                campo.requestFocus()
                // O emulador sincroniza a área de transferência com o computador e troca o HTML por
                // texto puro, então o teste entrega o conteúdo copiado diretamente ao campo.
                val copiado = ClipData.newHtmlText("script", "Abertura normal itálico e sublinhado", htmlDocs.substringBefore("<p dir=\"ltr\"><span style=\"color:rgb"))
                campo.lerAreaDeTransferencia = { copiado }
                // O Gboard cola pela barra de área de transferência chamando commitText com o texto puro.
                val conexao = campo.onCreateInputConnection(EditorInfo())!!
                conexao.commitText("Abertura normal itálico e sublinhado", 1)

                assertEquals("Abertura normal itálico e sublinhado", campo.text.toString())
                assertEquals(1, campo.editableText.getSpans(0, campo.length(), StyleSpan::class.java).count { it.style == Typeface.BOLD })

                // Digitação comum (sem relação com a área de transferência) não é afetada.
                conexao.commitText(" fim", 1)
                assertTrue(campo.editableText.endsWith(" fim"))
            }
        }
    }

    @Test
    fun colarNoEditorMantemFormatacaoEPermiteSalvar() {
        ActivityScenario.launch(EditorActivity::class.java).use { cenario ->
            cenario.onActivity { atividade ->
                val campo = atividade.findViewById<EditText>(R.id.campoTexto)
                campo.setText("Início: ")
                campo.setSelection(campo.length())
                val clip = ClipData.newHtmlText("script", "texto simples", htmlDocs)
                ViewCompat.performReceiveContent(campo, ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_CLIPBOARD).build())

                assertTrue(campo.text.startsWith("Início: Abertura normal"))
                val html = Html.deTexto(campo.text)
                assertTrue(html, html.contains("<b>Abertura</b>"))
                // O HTML salvo escapa os acentos (á vira &#225;).
                assertTrue(html, html.contains("<i>it&#225;lico</i>"))
                assertTrue(html, html.contains("<u>sublinhado</u>"))
                assertTrue(html, html.contains("background-color:#FFFF00"))
            }
        }
    }

    @Test
    fun colarComoTextoSimplesIgnoraFormatacao() {
        ActivityScenario.launch(EditorActivity::class.java).use { cenario ->
            cenario.onActivity { atividade ->
                val campo = atividade.findViewById<EditText>(R.id.campoTexto)
                val clip = ClipData.newHtmlText("script", "texto simples", htmlDocs)
                ViewCompat.performReceiveContent(
                    campo,
                    ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_CLIPBOARD)
                        .setFlags(ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .build(),
                )
                assertEquals("texto simples", campo.text.toString())
                assertEquals(0, campo.text.getSpans(0, campo.length(), StyleSpan::class.java).size)
            }
        }
    }

    private fun <T : Any> trechos(texto: Spanned, tipo: Class<T>, filtro: (T) -> Boolean): List<String> =
        texto.getSpans(0, texto.length, tipo)
            .filter(filtro)
            .sortedBy { texto.getSpanStart(it) }
            .map { texto.subSequence(texto.getSpanStart(it), texto.getSpanEnd(it)).toString() }
}
