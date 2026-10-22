package com.example.scriptorys

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Junta o vídeo (gravado pela câmera, sem som) com o áudio (gravado pelo microfone escolhido)
 * num único MP4, e salva o resultado na galeria do celular.
 */
object MontadorVideo {

    /** Pasta base dos vídeos na galeria: Filmes/Scriptorys. */
    const val PASTA_BASE = "Scriptorys"

    /**
     * Copia as trilhas de [video] e, se houver, de [audio] para [saida], intercalando pela ordem
     * do tempo. [deslocamentoAudioUs] é quanto o áudio começou depois (positivo) ou antes
     * (negativo) do vídeo; o trecho de áudio anterior ao primeiro quadro é descartado.
     */
    fun juntar(video: File, audio: File?, deslocamentoAudioUs: Long, saida: File) {
        val extratorVideo = MediaExtractor().apply { setDataSource(video.path) }
        val extratorAudio = audio?.takeIf { it.exists() && it.length() > 0 }?.let { MediaExtractor().apply { setDataSource(it.path) } }
        val muxer = MediaMuxer(saida.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val origemVideo = selecionarTrilha(extratorVideo, "video/") ?: error("O vídeo gravado não tem imagem")
            val origemAudio = extratorAudio?.let { selecionarTrilha(it, "audio/") }

            val formatoVideo = extratorVideo.getTrackFormat(origemVideo)
            if (formatoVideo.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(formatoVideo.getInteger(MediaFormat.KEY_ROTATION))
            }
            val trilhaVideo = muxer.addTrack(formatoVideo)
            val trilhaAudio = origemAudio?.let { muxer.addTrack(extratorAudio.getTrackFormat(it)) }
            muxer.start()

            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var temVideo = true
            var temAudio = trilhaAudio != null
            while (temVideo || temAudio) {
                val tempoVideo = if (temVideo) extratorVideo.sampleTime else Long.MAX_VALUE
                val tempoAudio = if (temAudio) extratorAudio!!.sampleTime + deslocamentoAudioUs else Long.MAX_VALUE
                if (temVideo && tempoVideo <= tempoAudio) {
                    temVideo = copiarAmostra(extratorVideo, muxer, trilhaVideo, buffer, info, 0)
                } else {
                    temAudio = copiarAmostra(extratorAudio!!, muxer, trilhaAudio!!, buffer, info, deslocamentoAudioUs)
                }
            }
            muxer.stop()
        } finally {
            muxer.release()
            extratorVideo.release()
            extratorAudio?.release()
        }
    }

    private fun selecionarTrilha(extrator: MediaExtractor, prefixo: String): Int? {
        for (i in 0 until extrator.trackCount) {
            if (extrator.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefixo) == true) {
                extrator.selectTrack(i)
                return i
            }
        }
        return null
    }

    /** Copia uma amostra; devolve false quando a trilha acabou. */
    private fun copiarAmostra(
        extrator: MediaExtractor, muxer: MediaMuxer, trilha: Int, buffer: ByteBuffer,
        info: MediaCodec.BufferInfo, deslocamentoUs: Long,
    ): Boolean {
        buffer.clear()
        val tamanho = extrator.readSampleData(buffer, 0)
        if (tamanho < 0) return false
        val tempo = extrator.sampleTime + deslocamentoUs
        if (tempo >= 0) {
            val quadroChave = extrator.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            info.set(0, tamanho, tempo, if (quadroChave) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(trilha, buffer, info)
        }
        return extrator.advance()
    }

    /**
     * Copia [arquivo] para a galeria em Filmes/Scriptorys (ou Filmes/Scriptorys/[subpasta]) e
     * devolve o endereço do vídeo salvo.
     */
    fun salvarNaGaleria(contexto: Context, arquivo: File, nomeArquivo: String, subpasta: String?): Uri? {
        val resolver = contexto.contentResolver
        val caminho = listOfNotNull(Environment.DIRECTORY_MOVIES, PASTA_BASE, subpasta?.let(::nomeSeguro)).joinToString("/")
        val valores = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$nomeArquivo.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            valores.put(MediaStore.Video.Media.RELATIVE_PATH, caminho)
            valores.put(MediaStore.Video.Media.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), valores) ?: return null
            resolver.openOutputStream(uri)?.use { saida -> arquivo.inputStream().use { it.copyTo(saida) } }
            valores.clear()
            valores.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
            return uri
        }
        // Android 9 ou anterior: grava o arquivo direto na pasta pública e registra na galeria.
        @Suppress("DEPRECATION")
        val pasta = File(Environment.getExternalStorageDirectory(), caminho).apply { mkdirs() }
        val destino = File(pasta, "$nomeArquivo.mp4")
        arquivo.copyTo(destino, overwrite = true)
        @Suppress("DEPRECATION")
        valores.put(MediaStore.Video.Media.DATA, destino.absolutePath)
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores)
    }

    /** Nome de pasta/arquivo sem caracteres proibidos. */
    fun nomeSeguro(nome: String): String =
        nome.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(60).ifEmpty { "Sem titulo" }
}
