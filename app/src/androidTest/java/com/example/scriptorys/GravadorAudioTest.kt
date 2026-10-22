package com.example.scriptorys

import android.Manifest
import android.annotation.SuppressLint
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GravadorAudioTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun permitirMicrofone() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(contexto.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun listaAoMenosOMicrofoneDoCelular() {
        val lista = Microfones.listar(contexto)
        assertTrue("nenhum microfone encontrado", lista.isNotEmpty())
        assertEquals(contexto.getString(R.string.microfone_celular), lista.first { !it.externo }.nome)
    }

    @SuppressLint("MissingPermission")
    @Test
    fun gravaArquivoAacComOMicrofoneEscolhido() {
        val arquivo = File(contexto.cacheDir, "teste-audio.m4a").apply { delete() }
        val interno = Microfones.listar(contexto).first { !it.externo }.dispositivo
        val gravador = GravadorAudio(interno, arquivo)
        val antes = System.nanoTime()

        gravador.iniciar()
        Thread.sleep(1500)
        gravador.parar()

        assertTrue("nada foi gravado", gravador.temAudio())
        assertTrue("início do áudio fora do esperado", gravador.inicioNanos in (antes - 1_000_000_000L)..System.nanoTime())
        assertNotNull("o app não registrou qual microfone o Android usou", gravador.dispositivoEmUso)

        val extrator = MediaExtractor().apply { setDataSource(arquivo.path) }
        try {
            assertEquals(1, extrator.trackCount)
            val formato = extrator.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, formato.getString(MediaFormat.KEY_MIME))
            // ~1,5 s gravados (com folga para o início e o fim do microfone).
            val duracaoUs = formato.getLong(MediaFormat.KEY_DURATION)
            assertTrue("duração inesperada: $duracaoUs µs", duracaoUs in 800_000L..2_500_000L)
        } finally {
            extrator.release()
            arquivo.delete()
        }
    }

    @SuppressLint("MissingPermission")
    @Test
    fun testeDeVolumeNaoCriaArquivo() {
        val gravador = GravadorAudio(null, null)
        gravador.iniciar()
        Thread.sleep(500)
        gravador.parar()
        assertTrue(gravador.inicioNanos > 0)
        assertTrue(!gravador.temAudio())
    }
}
